package com.coinepro.core.notifications

/**
 * How one alert is allowed to reach the reader.
 *
 * ### Not the same thing as an Android notification channel
 *
 * [NotificationCategory] maps to the operating system's channels — the per-category controls the
 * reader gets by long-pressing a notification. This is a different axis and it is per *alert*: two
 * alerts in the same category, one of which should buzz the phone and one of which should sit
 * quietly in the app until it is opened. The platform has no concept of that, because the platform
 * does not know that one of these numbers is the reader's entry and the other is idle curiosity.
 *
 * ### Why four, and why they are independent rather than a scale
 *
 * A single "importance" slider forces an order — quiet, then loud, then louder — and the order is
 * wrong. Vibration without sound is what somebody in a meeting wants; sound without vibration is
 * what somebody with the phone in another room wants; in-app only is what somebody who is already
 * looking at the chart wants. These are choices, not degrees, so they are a set.
 */
enum class AlertChannel(val id: String, val defaultOn: Boolean) {
    /**
     * A system notification, so it arrives with the app closed.
     *
     * On by default: an alert the reader only finds by opening the app is a note to self, and they
     * did not ask for a note to self.
     */
    PUSH("push", defaultOn = true),

    /** A banner inside the app while it is open. Free, and never the thing that wakes anybody. */
    IN_APP("in_app", defaultOn = true),

    /**
     * Audible.
     *
     * Separate from [PUSH] because a silent push is a real and common preference, and because the
     * loudness of this one is the reader's own — see [AlertSound].
     */
    SOUND("sound", defaultOn = true),

    /** Haptic. The one that works in a pocket, in a meeting, and with the ringer off. */
    VIBRATE("vibrate", defaultOn = false),
    ;

    companion object {
        fun fromId(id: String?): AlertChannel? = entries.firstOrNull { it.id == id }

        /** What a new alert starts with: it arrives, it is audible, it does not buzz. */
        val DEFAULTS: Set<AlertChannel> = entries.filter { it.defaultOn }.toSet()

        /** Between two channel ids in the stored form. No id contains one. */
        private const val SEPARATOR = ","

        /**
         * The stored form of a selection.
         *
         * An empty selection is written as a single dash rather than as an empty string, because
         * an empty field in a delimited row is indistinguishable from a field an older version
         * never wrote — and those two must decode differently. Absent means "use the defaults";
         * empty means "the reader turned everything off", which is a choice the app must keep.
         */
        fun encode(channels: Set<AlertChannel>): String =
            if (channels.isEmpty()) NONE else channels.joinToString(SEPARATOR) { it.id }

        /** The selection a row was written with, or null where the row predates this field. */
        fun decode(raw: String?): Set<AlertChannel>? {
            val text = raw?.takeIf(String::isNotBlank) ?: return null
            if (text == NONE) return emptySet()
            val decoded = text.split(SEPARATOR).mapNotNull(::fromId).toSet()
            // A field that held only names this version does not know is not a silent alert; it is
            // a row from a later release, and the defaults are a better guess than silence.
            return decoded.ifEmpty { null }
        }

        private const val NONE = "-"
    }
}

/**
 * How loud an alert's own sound is, independently of the rest of the app.
 *
 * ### Why an alert gets its own level at all
 *
 * A review of this category of app, quoted more or less verbatim: *a beep is not enough to alert
 * someone busy at work*. The reader's notification volume is set for the notifications they get all
 * day — a signal published, a copy trade filled — and the price they have been waiting three weeks
 * for is not one of those. Forcing one volume on both means either the routine ones are alarming or
 * the important one is inaudible.
 *
 * ### What the number means
 *
 * A fraction of the maximum the chosen output allows, from silent to full. It scales the sound the
 * app plays itself; a system notification's own sound still belongs to its Android channel, which
 * the reader controls from outside the app and which this deliberately does not fight.
 *
 * Past [LOUD_THRESHOLD] the delivery layer plays on the alarm output instead of the notification
 * one. That is the only mechanism Android offers for "louder than notifications", and it is a real
 * escalation rather than a bigger number, so it is a documented threshold rather than a hidden one.
 */
object AlertSound {

    /** Silent. Selecting [AlertChannel.SOUND] and then this is unusual but it is not a bug. */
    const val MIN_LEVEL = 0f

    /** As loud as the chosen output goes. */
    const val MAX_LEVEL = 1f

    /**
     * What a new alert starts at.
     *
     * Below [LOUD_THRESHOLD] on purpose: a fresh alert behaves like every other notification the
     * app sends, and being louder than the rest of the phone is something the reader opts into for
     * the one alert that deserves it rather than something the app assumes about all of them.
     */
    const val DEFAULT_LEVEL = 0.7f

    /**
     * Above this, the sound goes out on the alarm output.
     *
     * Above the default, so reaching it is a deliberate move by somebody who has decided this one
     * alert matters more than the phone's notification volume.
     */
    const val LOUD_THRESHOLD = 0.9f

    /** Clamps a stored or typed level. Never throws; a nonsense number becomes the nearest sane one. */
    fun coerce(level: Float): Float =
        if (level.isNaN()) DEFAULT_LEVEL else level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /** Whether this level asks for the alarm output rather than the notification one. */
    fun isLoud(level: Float): Boolean = coerce(level) > LOUD_THRESHOLD
}
