package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What the app feels like under a finger.
 *
 * ### Why this exists at all
 *
 * Everything in this design system so far has been about what a control *looks* like when pressed —
 * `pressScale` compresses it, the colour shifts, the ripple runs. All of that arrives a frame or
 * two after the touch and through the one sense that is already busy reading the screen. Touch is
 * the sense that is free, and it is the one every application a reader would compare this to uses:
 * a Binance tab, a Revolut toggle, an iOS segmented control all answer the finger before they
 * answer the eye. An app that stays silent under the thumb reads as a web page in a frame, and no
 * amount of spacing or type fixes that impression.
 *
 * ### Three weights, and no more
 *
 * The temptation is a vocabulary — one buzz per kind of event — and it is a mistake, because a
 * reader cannot learn eight vibrations and will read any of them as "something happened". So there
 * are three, separated by how much they should make somebody look up:
 *
 * - [select] for a choice that changed: a tab, a chip, a segment, a row that opened something. The
 *   lightest tick the platform has. Used constantly, so it has to be nearly subliminal.
 * - [commit] for an action that did something the reader would want undone if it were wrong: an
 *   order placed, an alert armed, a setting saved, a sign-out.
 * - [reject] for a refusal — a form that will not submit, a limit reached. Distinguishable from
 *   [commit] because "it worked" and "it did not" must never feel the same.
 *
 * ### The device's own setting is the only setting
 *
 * There is no in-app switch. Android already has one, every reader who wants haptics off has
 * already found it, and `HapticFeedback` honours it — a second switch in this app's settings would
 * be a control that appears to do something the operating system has already done. This is the same
 * argument the notification channels make about importance.
 */
@Immutable
class CoineProHaptics internal constructor(private val feedback: HapticFeedback) {

    /**
     * A choice changed. The platform's segment tick — Android's `CLOCK_TICK` — which is what a
     * chip, a timeframe key and the magnet's snap feel like on the reference.
     */
    fun select() {
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** Something was done that the reader would want undone if it were wrong: Android's `CONFIRM`. */
    fun commit() {
        feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    /** The app declined: Android's `REJECT`, which the platform makes feel unlike a confirm. */
    fun reject() {
        feedback.performHapticFeedback(HapticFeedbackType.Reject)
    }

    /** A long press took hold — the crosshair landing, a drag beginning. Android's `LONG_PRESS`. */
    fun longPress() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** The haptics for this composition, remembered so a row does not allocate one per recomposition. */
@Composable
fun rememberCoineProHaptics(): CoineProHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { CoineProHaptics(feedback) }
}
