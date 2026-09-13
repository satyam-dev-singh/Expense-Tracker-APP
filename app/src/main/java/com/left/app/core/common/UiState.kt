package com.left.app.core.common

/**
 * Standard screen states (PRD §20). Every screen renders exactly one of
 * Loading / Success / Empty / Error. Error messages are user-readable text —
 * never exception class names or stack traces.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    /** For list-like content when there is nothing to show yet (never a blank screen). */
    data object Empty : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

/** Convenience: empty lists become [UiState.Empty], non-empty lists [UiState.Success]. */
fun <T> List<T>.toUiState(): UiState<List<T>> =
    if (isEmpty()) UiState.Empty else UiState.Success(this)
