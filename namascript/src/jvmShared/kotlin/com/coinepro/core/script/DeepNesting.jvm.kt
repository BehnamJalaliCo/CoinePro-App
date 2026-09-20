package com.coinepro.core.script

/**
 * On the JVM and on Android a stack overflow is an `Error`, and catching it here is safe: the
 * stack unwinds, nothing else in the process is affected, and the reader gets `E405` rather than a
 * crash. This is the behaviour the app has shipped with since the language existed.
 */
internal actual fun <R> catchingDeepNesting(fallback: () -> R, block: () -> R): R = try {
    block()
} catch (error: StackOverflowError) {
    fallback()
}
