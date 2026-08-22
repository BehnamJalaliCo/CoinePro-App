package com.coinepro.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(
        val kind: ErrorKind,
        val message: String? = null,
        val cause: Throwable? = null,
    ) : AppResult<Nothing>
}

enum class ErrorKind {
    NETWORK,
    AUTH,
    VALIDATION,
    RATE_LIMIT,
    SERVER,
    UNKNOWN,
}
