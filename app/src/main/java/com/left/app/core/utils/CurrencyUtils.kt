package com.left.app.core.utils

import java.util.Currency

/**
 * ISO 4217 currency validation and metadata backed by [java.util.Currency].
 * No third-party library is needed for validation (PRD §4 rule:
 * prefer JDK/AndroidX native solutions first).
 */
object CurrencyUtils {

    /** Default currency for the initial Indian-market MVP (PRD §3 target users). */
    const val DEFAULT_CURRENCY_CODE = "INR"

    /** True when [code] is an uppercase ISO 4217 code known to the JVM. */
    fun isValidCurrencyCode(code: String): Boolean =
        code.length == 3 &&
            code.all { it in 'A'..'Z' } &&
            runCatching { Currency.getInstance(code) }.isSuccess

    /**
     * Number of fraction digits for [code] (INR → 2, JPY → 0).
     * @throws IllegalArgumentException for an unknown code.
     */
    fun fractionDigits(code: String): Int = Currency.getInstance(code).defaultFractionDigits
}
