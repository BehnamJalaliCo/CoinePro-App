package com.coinepro.core.script

/**
 * The one failure in this package that is not the same on every target.
 *
 * A script nested deeply enough — `((((((…))))))` — exhausts the call stack inside the recursive
 * descent parser before the interpreter's node budget has anything to count. On the JVM that
 * arrives as `StackOverflowError`, which is catchable, so the reader gets `E405` and a caret
 * instead of a crash. In a browser it is a WebAssembly trap, and a trap is not catchable at all:
 * the instance is gone.
 *
 * So this is an `expect` rather than a `catch (error: StackOverflowError)` in common code, and the
 * two `actual`s are honest about the difference rather than pretending it away. Catching
 * `Throwable` instead would have compiled everywhere and been worse: a genuine bug in this package
 * would then reach the reader as «your script is nested too deeply», which is a lie told by the
 * error handler.
 *
 * The browser's protection is upstream of this and belongs there: [NamaScript.MAX_SOURCE_LENGTH]
 * refuses an over-long source before it is tokenised, and the parser's own depth limit — which
 * costs nothing and runs on every target — is what keeps a legal source from reaching the stack's
 * floor.
 */
internal expect fun <R> catchingDeepNesting(fallback: () -> R, block: () -> R): R

/** The failure both the compile path and the run path report for it, in one place. */
internal fun deepNestingFailure(): ScriptFailure = ScriptFailure(
    "اسکریپت بیش از حد تودرتو است",
    "The script is nested too deeply",
    0,
    0,
    "E405",
)
