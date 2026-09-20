package com.coinepro.core.script

/**
 * A browser has nothing to catch.
 *
 * Exhausting the stack in WebAssembly is a trap, and a trap ends the instance — there is no
 * `catch` that runs afterwards, because there is no afterwards. So [fallback] is unreachable here
 * and saying so is the honest implementation; pretending otherwise would put a `catch (Throwable)`
 * in the way of every real bug for the sake of a case it could not handle anyway.
 *
 * What keeps the page alive is therefore not this function but `Parser.MAX_NESTING`, which refuses
 * an over-nested script before the recursion starts and reports the same `E405` with a line and a
 * column. That check runs on every target, which is why the JVM path below it is now a second line
 * of defence rather than the only one.
 */
internal actual fun <R> catchingDeepNesting(fallback: () -> R, block: () -> R): R = block()
