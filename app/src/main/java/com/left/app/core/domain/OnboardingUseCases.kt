package com.left.app.core.domain

import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.IncomeSourceRepository
import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.datastore.OnboardingPreferences
import com.left.app.core.model.IncomeFrequency
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The validated result of the onboarding flow, ready to persist. */
data class OnboardingResult(
    val name: String,
    val currencyCode: String,
    val locale: String,
    /** null = income step skipped. */
    val income: OnboardingIncome?,
    /** null = budget step skipped. */
    val monthlyBudget: Money?,
    /** Default categories the user chose to hide (archived, never deleted). */
    val archiveCategoryIds: Set<String> = emptySet(),
)

/** A recurring income configured during onboarding. */
data class OnboardingIncome(
    val name: String,
    val amount: Money,
    val frequency: IncomeFrequency,
)

/** Onboarding validation failures — messages are user-safe by contract (PRD §20). */
sealed class OnboardingValidationException(message: String) : IllegalArgumentException(message) {
    class BlankName : OnboardingValidationException("Please enter your name")
    class InvalidCurrency(code: String) : OnboardingValidationException("Unsupported currency code: $code")
    class NonPositiveAmount : OnboardingValidationException("Amount must be greater than zero")
}

/**
 * Completes onboarding (Implementation Plan Phase 2, PRD FR-01): persists the
 * user profile, optional monthly budget (current month), optional recurring
 * income source, and category preferences — then marks onboarding completed.
 *
 * Write order matters: the completion flag is written LAST, so if anything
 * fails midway the app still routes to onboarding on next launch and the user
 * can safely retry. All failures surface as friendly messages, never stack
 * traces (PRD §20).
 */
class CompleteOnboarding @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val incomeSourceRepository: IncomeSourceRepository,
    private val categoryRepository: CategoryRepository,
    private val onboardingPreferences: OnboardingPreferences,
    private val clock: Clock,
) {
    suspend operator fun invoke(result: OnboardingResult) {
        // Validate BEFORE any write so bad input never leaves partial state.
        val name = result.name.trim()
        if (name.isEmpty()) throw OnboardingValidationException.BlankName()
        if (!CurrencyUtils.isValidCurrencyCode(result.currencyCode)) {
            throw OnboardingValidationException.InvalidCurrency(result.currencyCode)
        }
        result.income?.let { if (!it.amount.isPositive) throw OnboardingValidationException.NonPositiveAmount() }
        result.monthlyBudget?.let { if (!it.isPositive) throw OnboardingValidationException.NonPositiveAmount() }

        // Defaults exist before any archive call (idempotent, PRD §17).
        categoryRepository.ensureDefaultCategories()

        userProfileRepository.upsertProfile(
            name = name,
            currencyCode = result.currencyCode,
            locale = result.locale,
        )

        result.monthlyBudget?.let { budget ->
            val current = MonthRange.current(clock)
            monthlyBudgetRepository.setBudget(current.year, current.month, budget)
        }

        result.income?.let { income ->
            incomeSourceRepository.create(
                name = income.name.trim().ifEmpty { "Income" },
                amount = income.amount,
                frequency = income.frequency,
                nextDate = nextOccurrence(income.frequency, clock),
                active = true,
            )
        }

        result.archiveCategoryIds.forEach { categoryRepository.archive(it) }

        // Flags last — completion is only recorded once everything else succeeded.
        onboardingPreferences.setDefaultCurrencyCode(result.currencyCode)
        onboardingPreferences.setOnboardingCompleted(true)
    }

    /** First expected occurrence after today for the given frequency; null for one-time income. */
    private fun nextOccurrence(frequency: IncomeFrequency, clock: Clock): LocalDate? {
        val today = LocalDate.now(clock)
        return when (frequency) {
            IncomeFrequency.WEEKLY -> today.plusWeeks(1)
            IncomeFrequency.BIWEEKLY -> today.plusWeeks(2)
            IncomeFrequency.MONTHLY -> today.plusMonths(1)
            IncomeFrequency.QUARTERLY -> today.plusMonths(3)
            IncomeFrequency.YEARLY -> today.plusYears(1)
            IncomeFrequency.ONE_TIME -> null
        }
    }
}
