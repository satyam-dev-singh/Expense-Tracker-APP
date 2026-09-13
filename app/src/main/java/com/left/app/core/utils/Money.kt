package com.left.app.core.utils

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Immutable money value object.
 *
 * CRITICAL RULE (PRD §10 / Technical Architecture §3): money is ALWAYS stored and
 * computed as integer minor currency units (e.g. paise for INR). Float and Double
 * are never used for money anywhere in the app. All arithmetic uses
 * overflow-checked integer operations ([Math.addExact] / [Math.subtractExact]).
 *
 * Example: ₹100.50 → Money(minorUnits = 10050)
 */
@JvmInline
value class Money private constructor(val minorUnits: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(minorUnits, other.minorUnits))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(minorUnits, other.minorUnits))

    operator fun unaryMinus(): Money = Money(Math.negateExact(minorUnits))

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    /**
     * Formats this amount for display in [currencyCode].
     * Display only — the stored value is never affected by locale or formatting.
     */
    fun format(currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val fractionDigits = CurrencyUtils.fractionDigits(currencyCode)
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        return formatter.format(BigDecimal(minorUnits).movePointLeft(fractionDigits))
    }

    override fun toString(): String = minorUnits.toString()

    companion object {
        val ZERO: Money = Money(0L)

        /** Wraps an amount already expressed in minor units (e.g. paise). */
        fun ofMinorUnits(minorUnits: Long): Money = Money(minorUnits)

        /**
         * Parses user input such as "250", "100.50", "₹1,00,000.50" or "-50"
         * into minor units for [currencyCode].
         *
         * Grouping separators (`,` in both Indian and Western styles) and currency
         * symbols are stripped. Inputs with more precision than the currency allows
         * (e.g. "10.005" for INR) are rejected — never silently rounded.
         *
         * @throws MoneyParseException if the input is blank, non-numeric,
         * over-precise, or out of range.
         */
        fun parse(input: String, currencyCode: String): Money {
            // Validates the currency first — an invalid code throws IllegalArgumentException.
            val fractionDigits = CurrencyUtils.fractionDigits(currencyCode)
            val cleaned = input
                .trim()
                // Drop dots that are not decimal separators (e.g. "Rs. 10" -> "Rs 10").
                .replace(Regex("\\.(?=[^0-9]|$)"), "")
                .replace(Regex("[^0-9,\\.\\-]"), "") // drop symbols, letters, whitespace
                .replace(",", "") // drop grouping separators (Indian & Western)
            if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == "-.") {
                throw MoneyParseException("Empty or non-numeric amount: '$input'")
            }
            val decimal = try {
                BigDecimal(cleaned)
            } catch (e: NumberFormatException) {
                throw MoneyParseException("Malformed amount: '$input'", e)
            }
            return try {
                Money(decimal.movePointRight(fractionDigits).longValueExact())
            } catch (e: ArithmeticException) {
                throw MoneyParseException("Amount '$input' has too many decimals or is too large", e)
            }
        }
    }
}

/** Thrown when a user-entered amount cannot be represented safely as [Money]. */
class MoneyParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Overflow-checked integer sum of a collection of [Money]. */
fun Iterable<Money>.sum(): Money = fold(Money.ZERO, Money::plus)
