package com.left.app.core.voice

import com.left.app.core.model.Category
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import com.left.app.core.utils.MoneyParseException
import java.time.LocalDate

/** The outcome of parsing one recognized utterance. */
sealed interface VoiceParseResult {
    /** Fully parsed draft, ready for user confirmation (PRD FR-04). */
    data class Parsed(val draft: VoiceExpenseDraft) : VoiceParseResult

    /** No usable amount was found — the UI shows guidance and offers retry/typing. */
    data class NoAmount(val transcript: String) : VoiceParseResult
}

/** A parsed voice entry. [note] keeps the full transcript so context is never lost. */
data class VoiceExpenseDraft(
    val amount: Money,
    val type: TransactionType,
    val categoryId: String?,
    val merchant: String?,
    val note: String,
    val date: LocalDate,
)

/**
 * Rule-based voice parser (PRD FR-04, Phase 5). Deterministic and offline —
 * no AI/ML in MVP (Technical Architecture: AI features are Phase 10).
 *
 * Examples:
 *  - "add 250 for groceries"           → ₹250.00 expense, Food
 *  - "spent 50 on food at chai point"  → ₹50.00 expense, Food, merchant "Chai Point"
 *  - "received 50000 salary"           → ₹50,000.00 income, merchant null
 *  - "paid 1200 for electricity yesterday" → ₹1,200.00 expense, Bills, yesterday
 *
 * Amount is REQUIRED: without a number the result is [VoiceParseResult.NoAmount].
 */
object VoiceExpenseParser {

    private val INCOME_KEYWORDS = listOf(
        "received", "salary", "income", "earned", "credited", "refund", "got paid", "bonus",
    )

    /**
     * Keyword dictionary per default category NAME (the seed list, PRD §17).
     * Order matters: the first category with a keyword hit wins.
     */
    private val CATEGORY_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "Food" to listOf(
            "food", "groceries", "grocery", "lunch", "dinner", "breakfast", "chai", "tea",
            "coffee", "restaurant", "meal", "snack", "pizza", "biryani", "swiggy", "zomato",
        ),
        "Transport" to listOf(
            "uber", "ola", "taxi", "cab", "fuel", "petrol", "diesel", "metro",
            "bus", "train", "auto", "rickshaw", "parking", "toll",
        ),
        "Shopping" to listOf(
            "shopping", "clothes", "amazon", "flipkart", "shirt", "shoes", "electronics", "gadget",
        ),
        "Bills" to listOf(
            "bill", "bills", "electricity", "rent", "recharge", "wifi",
            "internet", "broadband", "mobile", "dth", "water",
        ),
        "Entertainment" to listOf(
            "movie", "movies", "cinema", "netflix", "game", "games", "spotify", "show", "concert",
        ),
        "Health" to listOf(
            "doctor", "medicine", "medicines", "pharmacy", "hospital",
            "gym", "health", "dentist", "tablet", "clinic",
        ),
        "Education" to listOf(
            "course", "book", "books", "tuition", "school", "college", "udemy", "exam", "class",
        ),
        "Travel" to listOf(
            "flight", "hotel", "trip", "travel", "visa", "holiday", "vacation",
        ),
        "Personal" to listOf(
            "salon", "haircut", "personal", "cosmetics", "grooming",
        ),
    )

    private val AMOUNT_REGEX = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val MERCHANT_REGEX = Regex("""\b(?:at|from|to)\s+(.+?)\s*$""")
    private val ON_DAY_REGEX = Regex("""\bon(?:\s+the)?\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val MONTH_NAME_DAY_REGEX = Regex(
        """\bon\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+(\d{1,2})(?:st|nd|rd|th)?\b""",
    )

    fun parse(
        transcript: String,
        currencyCode: String,
        categories: List<Category>,
        today: LocalDate,
    ): VoiceParseResult {
        val text = transcript.trim()
        val lower = text.lowercase()

        // 1. Amount — the first number-like token wins. Symbols/grouping are fine
        //    because Money.parse strips them (₹, commas, "Rs.").
        val amountToken = AMOUNT_REGEX.find(lower)?.value
            ?: return VoiceParseResult.NoAmount(text)
        val amount = try {
            Money.parse(amountToken, currencyCode)
        } catch (e: MoneyParseException) {
            return VoiceParseResult.NoAmount(text)
        }
        if (!amount.isPositive) return VoiceParseResult.NoAmount(text)

        // 2. Type — income keywords override the EXPENSE default.
        val type = if (INCOME_KEYWORDS.any { lower.containsWord(it) }) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }

        return VoiceParseResult.Parsed(
            VoiceExpenseDraft(
                amount = amount,
                type = type,
                categoryId = matchCategory(lower, categories)?.id,
                merchant = extractMerchant(text, amountToken),
                note = text,
                date = extractDate(lower, today),
            ),
        )
    }

    /** First keyword-hit category wins; a directly named category is the fallback. */
    internal fun matchCategory(lower: String, categories: List<Category>): Category? {
        val byName = categories.associateBy { it.name }
        for ((categoryName, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { lower.containsWord(it) }) {
                byName[categoryName]?.let { return it }
                // Keyword matched but that category is unavailable (e.g. archived)
                // — keep scanning rather than guessing.
            }
        }
        return categories.firstOrNull { lower.containsWord(it.name.lowercase()) }
    }

    /**
     * Merchant = the trailing phrase after "at"/"from"/"to", with the amount
     * token removed (amounts often follow the merchant: "flight to delhi 5500"),
     * then title-cased.
     */
    private fun extractMerchant(text: String, amountToken: String): String? {
        val match = MERCHANT_REGEX.find(text) ?: return null
        return match.groupValues[1]
            .replace(amountToken, "")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            .ifBlank { null }
    }

    /**
     * Date words: "yesterday", "on the 5th", "on March 5". Days beyond today roll
     * into the previous month; named-month dates beyond today roll back a year.
     * Default: today.
     */
    private fun extractDate(lower: String, today: LocalDate): LocalDate {
        if (lower.containsWord("yesterday")) return today.minusDays(1)

        MONTH_NAME_DAY_REGEX.find(lower)?.let { match ->
            val month = MONTH_ABBREVIATIONS[match.groupValues[1]] ?: return@let
            val day = match.groupValues[2].toInt()
            var candidate = LocalDate.of(today.year, month, day.coerceAtMost(31))
                .let { if (day > it.lengthOfMonth()) it.withDayOfMonth(it.lengthOfMonth()) else it }
            if (candidate.isAfter(today)) candidate = candidate.minusYears(1)
            return candidate
        }

        ON_DAY_REGEX.find(lower)?.let { match ->
            val day = match.groupValues[1].toInt()
            var candidate = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
            if (candidate.isAfter(today)) {
                val previousMonth = today.minusMonths(1)
                candidate = previousMonth.withDayOfMonth(day.coerceAtMost(previousMonth.lengthOfMonth()))
            }
            return candidate
        }

        return today
    }

    private val MONTH_ABBREVIATIONS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private fun String.containsWord(word: String): Boolean =
        Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(this)
}
