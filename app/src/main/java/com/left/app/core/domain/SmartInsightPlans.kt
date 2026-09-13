package com.left.app.core.domain

/** Phase 10 local-only smart extraction and insight helpers. No network AI/ML. */
data class SmartExtraction(val amountText: String?, val merchant: String?, val categoryName: String)

fun extractSmartTransactionText(text: String): SmartExtraction {
    val amount = Regex("\\d+(?:\\.\\d{1,2})?").find(text)?.value
    val lower = text.lowercase()
    val merchant = when {
        "swiggy" in lower -> "Swiggy"
        "zomato" in lower -> "Zomato"
        "uber" in lower -> "Uber"
        else -> null
    }
    val category = when {
        merchant in setOf("Swiggy", "Zomato") || "food" in lower || "dinner" in lower -> "Food"
        merchant == "Uber" || "cab" in lower -> "Transport"
        else -> "Other"
    }
    return SmartExtraction(amount, merchant, category)
}

fun spendingInsight(topCategory: String?, moneyLeftMinor: Long): String = when {
    topCategory == null -> "Add transactions to unlock local insights."
    moneyLeftMinor < 0 -> "$topCategory is your biggest area, and expenses are above income."
    else -> "$topCategory is your biggest area, while income still covers spending."
}
