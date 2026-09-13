package com.left.app.core.domain

import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Input for [AddTransaction]. Amount is positive minor units; [date] is a valid LocalDate by construction. */
data class NewTransaction(
    val type: TransactionType,
    val amount: Money,
    val currencyCode: String,
    val categoryId: String?,
    val merchant: String? = null,
    val note: String? = null,
    val date: LocalDate,
    val source: TransactionSource = TransactionSource.MANUAL,
    val isRecurring: Boolean = false,
)

/**
 * Validation failures (PRD §18). Messages are user-safe by contract — never
 * expose exception class names or stack traces in UI (PRD §20).
 */
sealed class TransactionValidationException(message: String) : IllegalArgumentException(message) {
    class NonPositiveAmount : TransactionValidationException("Amount must be greater than zero")
    class InvalidCurrency(code: String) : TransactionValidationException("Unsupported currency code: $code")
    class UnknownCategory : TransactionValidationException("The selected category no longer exists")
    class NotFound : TransactionValidationException("This transaction no longer exists")
}

private object TransactionValidator {

    fun validateAmount(amount: Money) {
        if (!amount.isPositive) throw TransactionValidationException.NonPositiveAmount()
    }

    fun validateCurrency(currencyCode: String) {
        if (!CurrencyUtils.isValidCurrencyCode(currencyCode)) {
            throw TransactionValidationException.InvalidCurrency(currencyCode)
        }
    }

    /**
     * A null category is explicitly allowed (uncategorized); a non-null one
     * must reference an existing category (PRD §18).
     */
    suspend fun validateCategory(categoryId: String?, categoryRepository: CategoryRepository) {
        if (categoryId != null && !categoryRepository.exists(categoryId)) {
            throw TransactionValidationException.UnknownCategory()
        }
    }
}

/** Validates and records a new transaction. Source of truth is Room (offline-first, PRD §22). */
class AddTransaction @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    /** @return the id of the stored transaction. */
    suspend operator fun invoke(new: NewTransaction): String {
        TransactionValidator.validateAmount(new.amount)
        TransactionValidator.validateCurrency(new.currencyCode)
        TransactionValidator.validateCategory(new.categoryId, categoryRepository)

        val now = Instant.now(clock)
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            type = new.type,
            amount = new.amount,
            currencyCode = new.currencyCode,
            categoryId = new.categoryId,
            merchant = new.merchant?.trim()?.ifEmpty { null },
            note = new.note?.trim()?.ifEmpty { null },
            date = new.date,
            createdAt = now,
            updatedAt = now,
            source = new.source,
            isRecurring = new.isRecurring,
        )
        transactionRepository.insert(transaction)
        return transaction.id
    }
}

/** Validates and stores edits to an existing transaction. */
class UpdateTransaction @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(transaction: Transaction) {
        TransactionValidator.validateAmount(transaction.amount)
        TransactionValidator.validateCurrency(transaction.currencyCode)
        TransactionValidator.validateCategory(transaction.categoryId, categoryRepository)
        transactionRepository.getById(transaction.id)
            ?: throw TransactionValidationException.NotFound()
        transactionRepository.update(transaction.copy(updatedAt = Instant.now(clock)))
    }
}

/** Deletes a transaction; throws [TransactionValidationException.NotFound] when it does not exist. */
class DeleteTransaction @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(id: String) {
        transactionRepository.getById(id) ?: throw TransactionValidationException.NotFound()
        transactionRepository.delete(id)
    }
}

/** Streams one transaction (null once deleted). */
class GetTransaction @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(id: String): Flow<Transaction?> = transactionRepository.observeById(id)
}

/** Streams all transactions, newest first. */
class GetTransactions @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(): Flow<List<Transaction>> = transactionRepository.observeAll()
}

/** Streams the transactions of one calendar month (robust boundaries via [MonthRange], PRD §16). */
class GetMonthlyTransactions @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(year: Int, month: Int): Flow<List<Transaction>> {
        MonthRange.of(year, month) // validates month is 1..12
        return transactionRepository.observeByMonth(year, month)
    }
}
