package com.coinepro.core.webhook

import java.net.URI
import java.util.Locale

/**
 * One place an alert can be posted to when it fires — [142].
 *
 * ### What this is for
 *
 * An alert that only lights up a phone is an alert that can only be acted on by a person holding
 * that phone. A webhook is the same alert delivered to something that does not sleep: a bot, a
 * sheet, a desk system, a group chat. It is the single most-requested thing on top of alerting and
 * the obvious competitor charges for it — Essential and above, plus a two-factor requirement on the
 * account before the field even appears. Here it is free and there is no such gate. What *is* kept
 * from their design is the part that is right, and it is kept deliberately; see [WebhookUrl].
 *
 * ### The secret
 *
 * [secret] is used for one thing: signing the body, so the receiver can prove the request came from
 * this install rather than from anyone who learned the URL. It is never sent, never logged, never
 * printed on a screen and never written into a delivery record — see [WebhookSignature] and
 * [WebhookAttempt], both of which carry the signature or an error and neither of which can carry
 * this field. A webhook URL is a bearer credential in itself, which is exactly why a second factor
 * that is *not* in the URL is worth having.
 *
 * @param createdAt epoch milliseconds, supplied by the caller. Nothing in this type reads a clock.
 */
data class WebhookTarget(
    val id: String,
    /** The reader's own name for it — «ربات تلگرام», «شیت معاملات». Never derived from the URL. */
    val name: String,
    val url: String,
    /**
     * The shared secret this target's requests are signed with.
     *
     * Nullable because a receiver that cannot check a signature is a legitimate configuration — a
     * spreadsheet endpoint, somebody's first experiment — and refusing to post at all would trade a
     * working feature for a theoretical one. Where it is absent no signature header is sent, rather
     * than one computed under an empty key, which would look like a signature and prove nothing.
     */
    val secret: String? = null,
    /**
     * Whether this target is currently posted to.
     *
     * Kept rather than deleted, because the reason a reader switches a webhook off is nearly always
     * that it is misbehaving — and the URL, which is long and pasted from somewhere else, is the
     * thing they would have to find again.
     */
    val enabled: Boolean = true,
    val createdAt: Long,
) {
    /** True where this target is both switched on and addressable by this build. */
    val deliverable: Boolean get() = enabled && WebhookUrl.validate(url) == null
}

/**
 * Why a URL was refused, in the reader's own language.
 *
 * Every reason is a sentence a person can act on. That is the point of refusing at entry rather
 * than at fire time: a webhook that is accepted and then silently fails at three in the morning is
 * the alert-that-never-arrived failure this whole product is defined against, one layer down.
 */
enum class WebhookUrlRefusal(val reason: String) {
    /** Nothing typed, or whitespace. */
    EMPTY("نشانی وارد نشده است"),

    /** Not a URL at all — no scheme, a space in the middle, something a parser cannot read. */
    MALFORMED("نشانی خوانده نمی‌شود"),

    /**
     * Plain HTTP.
     *
     * Stricter than the competitor, which accepts both. The body carries what a market is doing and
     * when an account acts on it, and on an Iranian mobile network in particular an unencrypted
     * POST is readable and rewritable by anything between the phone and the receiver.
     */
    NOT_HTTPS("نشانی باید با https شروع شود"),

    /**
     * A numeric address, or a bare host name with no domain in it.
     *
     * An address names a machine rather than a service: certificates cannot be verified for one,
     * it is usually somewhere inside a network that the phone happens to be on, and a webhook that
     * posts market events at an internal address is a way to reach things that were never meant to
     * be reachable from an app. A domain name is also the only form that keeps working when the
     * receiver moves.
     */
    NOT_A_DOMAIN("نشانی باید نام دامنه باشد، نه نشانی عددی"),

    /**
     * A port other than 80 or 443.
     *
     * TradingView's own rule and this is the reason it is right rather than arbitrary: those two
     * ports are what a public web service listens on, and everything else on a host is something
     * else — a database, an admin panel, a service that never expected a POST. Copied deliberately.
     */
    PORT_NOT_ALLOWED("فقط پورت‌های ۸۰ و ۴۴۳ پذیرفته می‌شوند"),

    /** No host at all: `https:///path`. */
    NO_HOST("نشانی میزبان ندارد"),
}

/**
 * What a webhook URL is allowed to be, and why — [142].
 *
 * ### Where these rules come from
 *
 * Three of them are TradingView's, kept because they are right: **ports 80 and 443 only**, no
 * redirect chasing, and a refusal at the moment the URL is entered rather than at the moment it
 * fires. Two of theirs are deliberately not copied — they require two-factor authentication on the
 * account before a webhook may be created at all, and they sell the feature. Ours is free and asks
 * for no second factor, because the risk a webhook actually carries is to the *receiver*, and the
 * answer to that is the signature in [WebhookSignature], not a gate on the sender.
 *
 * One rule is stricter than theirs: HTTPS only. See [WebhookUrlRefusal.NOT_HTTPS].
 *
 * ### Why validation is a pure function on a string
 *
 * So the editor can refuse while the reader is still looking at the field, so a test can state the
 * rule in one line, and so nothing has to be posted anywhere to find out whether a URL is usable.
 */
object WebhookUrl {

    /** The only two ports a webhook may name. Kept from TradingView; see [WebhookUrlRefusal]. */
    val ALLOWED_PORTS: Set<Int> = setOf(80, 443)

    /**
     * Why [url] cannot be used, or null when it can.
     *
     * Null-means-good rather than a boolean, because every caller that refuses has to say why, and
     * a boolean would push that sentence out to the call sites where it would be written three
     * times and translated twice.
     */
    fun validate(url: String): WebhookUrlRefusal? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return WebhookUrlRefusal.EMPTY
        // Parsed rather than pattern-matched. A URL with a space, a control character or a second
        // scheme in it is not something a regular expression should be asked to judge, and OkHttp
        // will parse it the same way this does when the time comes to post.
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return WebhookUrlRefusal.MALFORMED
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return WebhookUrlRefusal.MALFORMED
        if (scheme != "https") return WebhookUrlRefusal.NOT_HTTPS
        val host = uri.host?.takeIf(String::isNotBlank) ?: return WebhookUrlRefusal.NO_HOST
        if (!isDomainName(host)) return WebhookUrlRefusal.NOT_A_DOMAIN
        // -1 is "no port given", which means the scheme's default, which is 443.
        val port = uri.port
        if (port != -1 && port !in ALLOWED_PORTS) return WebhookUrlRefusal.PORT_NOT_ALLOWED
        return null
    }

    /** True where [url] can be posted to. The boolean form, for a caller that has already asked why. */
    fun isValid(url: String): Boolean = validate(url) == null

    /**
     * Whether a host is a domain name rather than an address.
     *
     * Three things are refused and each is a real case rather than a hypothetical: an IPv4 literal
     * (`https://192.168.1.4/hook`, which is a machine on whatever network the phone joined), an
     * IPv6 literal (which `URI` hands back still wrapped in its brackets), and a single label with
     * no dot in it (`https://localhost/hook`, `https://intranet/hook`) — which is the same claim as
     * an address written in words.
     */
    private fun isDomainName(host: String): Boolean {
        if (host.startsWith("[")) return false
        if (IPV4.matches(host)) return false
        val labels = host.trim('.').split('.')
        if (labels.size < 2) return false
        return labels.all { label -> label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' } }
    }

    /**
     * Four dotted decimal groups.
     *
     * Deliberately not checking that each group is under 256: `999.1.2.3` is not a valid address
     * either, and refusing it as "an address" is a better sentence than letting it through as a
     * domain name that will never resolve.
     */
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
}
