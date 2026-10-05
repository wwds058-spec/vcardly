package com.yasin.vcardly.core.common

/** Domain-level failure. Messages are never built from user data (no PII in errors). */
sealed interface AppError {
    /** A form/field failed validation. [field] is a stable key, not user text. */
    data class Validation(val field: String, val reason: Reason) : AppError {
        enum class Reason { REQUIRED, INVALID_FORMAT, TOO_LONG, DUPLICATE }
    }

    data object NotFound : AppError
    data object Storage : AppError
    data object PermissionDenied : AppError
    data object Unknown : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
