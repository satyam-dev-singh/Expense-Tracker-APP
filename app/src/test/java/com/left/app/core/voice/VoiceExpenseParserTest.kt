package com.left.app.core.voice

import com.left.app.core.model.Category
import com.left.app.core.model.CategoryType
import com.left.app.core.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice parser tests (PRD FR-04, Phase 5): amount extraction, income keywords,
 * category keyword matching, merchant phrase, date words, missing-amount
 * guidance path. Pure rules — deterministic and offline.
 */
class VoiceExpenseParserTest {

    private val today: LocalDate = LocalDate.of(2024, 3, 10)
    private val now: Instant = Instant.parse("2024-03-10T08:00:00Z")

    /** The ten seeded defaults as domain categories. */
    private val categories: List<Category> = listOf(
        "Food", "Transport", "Shopping", "Bills", "Entertainment",
        "Health", "Education", "Travel", "Personal", "Other",
    ).map { name ->
        Category(
            id = "default-" + name.lowercase(),
            name = name,
            iconKey = "category",
            type = CategoryType.EXPENSE,
            budgetLimit = null,
            isDefault = true,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun parse(text: String): VoiceParseResult =
        VoiceExpenseParser.parse(text, "INR", categories, today)

    private fun parsed(text: String): VoiceExpenseDraft =
        (parse(text) as VoiceParseResult.Parsed).draft

    @Test
    fun `simple expense with category keyword`() {
        val draft = parsed("add 250 for groceries")
        assertEquals(25_000L, draft.amount.minorUnits)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("default-food", draft.categoryId)
        assertNull(draft.merchant)
        assertEquals(today, draft.date)
        assertEquals("add 250 for groceries", draft.note) // transcript kept as note
    }

    @Test
    fun `merchant follows at and is title-cased`() {
        val draft = parsed("Spent 50 on food at chai point")
        assertEquals(5_000L, draft.amount.minorUnits)
        assertEquals("default-food", draft.categoryId)
        assertEquals("Chai Point", draft.merchant)
    }

    @Test
    fun `merchant phrase does not swallow the amount`() {
        val draft = parsed("flight to delhi 5500")
        assertEquals(550_000L, draft.amount.minorUnits)
        assertEquals("default-travel", draft.categoryId)
        assertEquals("Delhi", draft.merchant)
    }

    @Test
    fun `income keywords switch the type`() {
        assertEquals(TransactionType.INCOME, parsed("received 50000 salary").type)
        assertEquals(TransactionType.INCOME, parsed("got paid 12000").type)
        assertEquals(TransactionType.INCOME, parsed("refund of 300 credited").type)
        assertEquals(5_000_000L, parsed("received 50000 salary").amount.minorUnits)
    }

    @Test
    fun `amount handles symbols grouping and decimals`() {
        assertEquals(100_050L, parsed("₹1,000.50 for books").amount.minorUnits)
        assertEquals(25_000L, parsed("spent Rs. 250 on chai").amount.minorUnits)
    }

    @Test
    fun `category keyword map covers the seeded defaults`() {
        assertEquals("default-transport", parsed("uber 300").categoryId)
        assertEquals("default-shopping", parsed("bought shoes for 2000").categoryId)
        assertEquals("default-bills", parsed("paid 1200 electricity bill").categoryId)
        assertEquals("default-entertainment", parsed("movie night 450").categoryId)
        assertEquals("default-health", parsed("doctor visit 800").categoryId)
        assertEquals("default-education", parsed("course fee 1500").categoryId)
        assertEquals("default-travel", parsed("hotel 3000").categoryId)
        assertEquals("default-personal", parsed("haircut 500").categoryId)
    }

    @Test
    fun `unmatched text stays uncategorized`() {
        val draft = parsed("spent 100 at random place")
        assertNull(draft.categoryId)
        assertEquals("Random Place", draft.merchant)
    }

    @Test
    fun `income keyword plus expense-ish category still parses coherently`() {
        val draft = parsed("received 250 refund for food")
        assertEquals(TransactionType.INCOME, draft.type)
        assertEquals("default-food", draft.categoryId)
    }

    @Test
    fun `missing or non-positive amount yields the guidance path`() {
        assertTrue(parse("bought groceries") is VoiceParseResult.NoAmount)
        assertTrue(parse("spent 0 on food") is VoiceParseResult.NoAmount)
        assertEquals("bought groceries", (parse("bought groceries") as VoiceParseResult.NoAmount).transcript)
    }

    @Test
    fun `date words - yesterday, on the Nth, named months`() {
        assertEquals(today.minusDays(1), parsed("paid 1200 for electricity yesterday").date)
        assertEquals(today, parsed("lunch 200 today").date)
        assertEquals(LocalDate.of(2024, 3, 5), parsed("lunch 200 on the 5th").date)
        // Said on March 3: "on the 28th" resolves to February 28 (previous month).
        val march3 = LocalDate.of(2024, 3, 3)
        assertEquals(
            LocalDate.of(2024, 2, 28),
            (VoiceExpenseParser.parse("lunch 200 on the 28th", "INR", categories, march3) as VoiceParseResult.Parsed).draft.date,
        )
        // Named month beyond today rolls back a year.
        assertEquals(LocalDate.of(2023, 12, 5), parsed("hotel 5000 on december 5").date)
        assertEquals(LocalDate.of(2024, 3, 5), parsed("hotel 5000 on march 5").date)
    }

    @Test
    fun `first number is the amount`() {
        assertEquals(20_000L, parsed("spent 200 on 2 movie tickets").amount.minorUnits)
    }
}
