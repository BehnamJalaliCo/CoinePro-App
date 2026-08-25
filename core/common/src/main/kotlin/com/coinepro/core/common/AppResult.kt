package com.coinepro.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    /**
     * [retryAfterSeconds] is only ever set from a server's `Retry-After` header, and only on
     * [ErrorKind.RATE_LIMIT]. Absent means the server did not say, and the UI must not fill in a
     * number of its own — a countdown the server never promised is a guess wearing a clock's face.
     */
    data class Failure(
        val kind: ErrorKind,
        val message: String? = null,
        val cause: Throwable? = null,
        val retryAfterSeconds: Int? = null,
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
