package com.left.app.core.security

import android.util.Log
import com.left.app.BuildConfig

/**
 * Production-safe logger (PRD §20, Technical Architecture §7).
 *
 * CONTRACT — never pass any of the following to this logger:
 *  - transaction amounts
 *  - merchant names
 *  - notes or free-text financial content
 *  - any other user financial data
 *
 * Debug builds emit verbose technical logs; release builds emit warnings and
 * errors with technical, non-financial context only.
 */
object SafeLogger {

    /** Placeholder for positions where a sensitive value would otherwise appear. */
    const val REDACTED = "<redacted>"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String, cause: Throwable? = null) {
        if (cause != null) Log.w(tag, message, cause) else Log.w(tag, message)
    }

    fun e(tag: String, message: String, cause: Throwable? = null) {
        if (cause != null) Log.e(tag, message, cause) else Log.e(tag, message)
    }
}
