package com.left.app.core.domain

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeIncomeSourceRepository
import com.left.app.core.data.fake.FakeMonthlyBudgetRepository
import com.left.app.core.data.fake.FakeOnboardingPreferences
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.model.IncomeFrequency
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CompleteOnboarding tests (PRD FR-01, Phase 2): profile/budget/income/category
 * persistence, validation-before-write, skip semantics, and the critical
 * ordering guarantee — the completion flag is always written LAST.
 */
class CompleteOnboardingTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var events: MutableList<String>
    private lateinit var userProfileRepository: FakeUserProfileRepository
    private lateinit var budgetRepository: FakeMonthlyBudgetRepository
    private lateinit var incomeSourceRepository: FakeIncomeSourceRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var preferences: FakeOnboardingPreferences
    private lateinit var completeOnboarding: CompleteOnboarding

    @Before
    fun setUp() {
        events = mutableListOf()
        userProfileRepository = FakeUserProfileRepository(clock, events)
        budgetRepository = FakeMonthlyBudgetRepository(clock, events)
        incomeSourceRepository = FakeIncomeSourceRepository(clock, events)
        categoryRepository = FakeCategoryRepository(clock, events)
        preferences = FakeOnboardingPreferences(events)
        completeOnboarding = CompleteOnboarding(
            userProfileRepository = userProfileRepository,
            monthlyBudgetRepository = budgetRepository,
            incomeSourceRepository = incomeSourceRepository,
            categoryRepository = categoryRepository,
            onboardingPreferences = preferences,
            clock = clock,
        )
    }

    private fun input(
        name: String = "Satyam",
        currencyCode: String = "INR",
        income: OnboardingIncome? = OnboardingIncome("Salary", Money.ofMinorUnits(5_000_000), IncomeFrequency.MONTHLY),
        monthlyBudget: Money? = Money.ofMinorUnits(3_000_000),
        archiveCategoryIds: Set<String> = emptySet(),
    ) = OnboardingResult(
        name = name,
        currencyCode = currencyCode,
        locale = "en-IN",
        income = income,
        monthlyBudget = monthlyBudget,
        archiveCategoryIds = archiveCategoryIds,
    )

    @Test
    fun `happy path persists everything and writes the completion flag last`() = runTest {
        completeOnboarding(input(name = "  Satyam  ", archiveCategoryIds = setOf("default-travel")))

        val profile = userProfileRepository.getProfile()
        assertEquals("Satyam", profile?.name) // trimmed
        assertEquals("INR", profile?.currencyCode)
        assertEquals("en-IN", profile?.locale)

        // Budget applies to the CURRENT month derived from the injected clock.
        val budget = budgetRepository.getBudget(2024, 3)
        assertEquals(3_000_000L, budget?.totalLimit?.minorUnits)

        val income = incomeSourceRepository.observeAll().first().single()
        assertEquals("Salary", income.name)
        assertEquals(5_000_000L, income.amount.minorUnits)
        assertEquals(IncomeFrequency.MONTHLY, income.frequency)
        assertEquals(LocalDate.of(2024, 4, 10), income.nextDate)
        assertTrue(income.active)

        // Deselected default is archived, never deleted (PRD §11).
        val archived = categoryRepository.getById("default-travel")
        assertTrue(archived!!.isArchived)

        assertTrue(preferences.completed)
        assertEquals("INR", preferences.currencyCode)

        // Ordering: completion flag is always the final write.
        assertEquals("completed-flag", events.last())
        assertTrue(events.indexOf("profile") < events.indexOf("completed-flag"))
        assertTrue(events.indexOf("categories") < events.indexOf("profile"))
    }

    @Test
    fun `skipping income and budget persists only profile and flags`() = runTest {
        completeOnboarding(input(income = null, monthlyBudget = null))

        assertEquals("Satyam", userProfileRepository.getProfile()?.name)
        assertNull(budgetRepository.getBudget(2024, 3))
        assertTrue(incomeSourceRepository.observeAll().first().isEmpty())
        assertTrue(preferences.completed)
        assertEquals("completed-flag", events.last())
    }

    @Test
    fun `blank name is rejected before anything is written`() {
        assertThrows(OnboardingValidationException.BlankName::class.java) {
            runTest { completeOnboarding(input(name = "   ")) }
        }
        assertNull(userProfileRepository.getProfile())
        assertFalse(preferences.completed)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `invalid currency is rejected before anything is written`() {
        assertThrows(OnboardingValidationException.InvalidCurrency::class.java) {
            runTest { completeOnboarding(input(currencyCode = "XYZ")) }
        }
        assertThrows(OnboardingValidationException.InvalidCurrency::class.java) {
            runTest { completeOnboarding(input(currencyCode = "inr")) }
        }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `non-positive amounts are rejected before anything is written`() {
        assertThrows(OnboardingValidationException.NonPositiveAmount::class.java) {
            runTest {
                completeOnboarding(input(income = OnboardingIncome("Salary", Money.ZERO, IncomeFrequency.MONTHLY)))
            }
        }
        assertThrows(OnboardingValidationException.NonPositiveAmount::class.java) {
            runTest { completeOnboarding(input(monthlyBudget = Money.ZERO)) }
        }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `budget month comes from the injected clock - december 31 stays december`() = runTest {
        val newYearsEve = Clock.fixed(Instant.parse("2024-12-31T10:00:00Z"), ZoneId.of("Asia/Kolkata"))
        val useCase = CompleteOnboarding(
            userProfileRepository, budgetRepository, incomeSourceRepository,
            categoryRepository, preferences, newYearsEve,
        )
        useCase(input(income = null))

        assertEquals(3_000_000L, budgetRepository.getBudget(2024, 12)?.totalLimit?.minorUnits)
        assertNull(budgetRepository.getBudget(2025, 1))
    }

    @Test
    fun `next occurrence dates follow the frequency`() = runTest {
        fun nextDateFor(frequency: IncomeFrequency): LocalDate? {
            val events = mutableListOf<String>()
            val incomes = FakeIncomeSourceRepository(clock, events)
            val useCase = CompleteOnboarding(
                FakeUserProfileRepository(clock, events),
                FakeMonthlyBudgetRepository(clock, events),
                incomes,
                FakeCategoryRepository(clock, events),
                FakeOnboardingPreferences(events),
                clock,
            )
            kotlinx.coroutines.runBlocking {
                useCase(input(income = OnboardingIncome("Salary", Money.ofMinorUnits(100_000), frequency), monthlyBudget = null))
            }
            return incomes.observeAll().first().single().nextDate
        }

        assertEquals(LocalDate.of(2024, 3, 17), nextDateFor(IncomeFrequency.WEEKLY))
        assertEquals(LocalDate.of(2024, 3, 24), nextDateFor(IncomeFrequency.BIWEEKLY))
        assertEquals(LocalDate.of(2024, 4, 10), nextDateFor(IncomeFrequency.MONTHLY))
        assertEquals(LocalDate.of(2024, 6, 10), nextDateFor(IncomeFrequency.QUARTERLY))
        assertEquals(LocalDate.of(2025, 3, 10), nextDateFor(IncomeFrequency.YEARLY))
        assertNull(nextDateFor(IncomeFrequency.ONE_TIME))
    }

    @Test
    fun `blank income name falls back to Income`() = runTest {
        completeOnboarding(input(income = OnboardingIncome("   ", Money.ofMinorUnits(100_000), IncomeFrequency.MONTHLY), monthlyBudget = null))
        assertEquals("Income", incomeSourceRepository.observeAll().first().single().name)
    }

    @Test
    fun `default categories exist even if seeding has not run yet`() = runTest {
        // No explicit seeding in this test — CompleteOnboarding must ensure them.
        completeOnboarding(input(income = null, monthlyBudget = null))
        assertEquals(10, categoryRepository.observeAllCategories().first().size)
    }
}
