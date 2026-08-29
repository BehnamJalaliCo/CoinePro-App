package com.coinepro.core.webhook

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * How a receiver can prove a webhook came from this install — [142].
 *
 * ### The thing TradingView does not do
 *
 * They post the alert body and nothing else. That means the URL *is* the credential: anybody who
 * ever sees it — in a screenshot, in a chat where it was pasted, in a log on the receiving side —
 * can post whatever they like to it, and the receiver has no way to tell that message from a real
 * one. For a webhook that only writes to a spreadsheet that is a nuisance. For one that places an
 * order it is the whole system.
 *
 * So every request this app sends carries a keyed hash of its own body under the target's secret.
 * The receiver computes the same hash and compares; if it does not match, the request did not come
 * from here. The secret itself never travels — that is the entire point of a keyed hash rather than
 * a shared token in a header — and it is never logged or shown, in this file or anywhere else.
 *
 * ### What the signature covers, and what that means for replays
 *
 * The body, exactly as sent, byte for byte in UTF-8. Nothing else: not the URL, not the headers,
 * not the time of sending. That is the smallest promise that is worth anything and the easiest one
 * for a receiver in any language to verify — three lines in Python, four in Node.
 *
 * It is *not* protection against a replay on its own, and a receiver that needs one should read the
 * time out of the body rather than trust the moment it arrived: the payload this app composes
 * carries `firedAt`, and because the whole body is signed, that field cannot be moved forward by
 * anybody who does not have the secret. A receiver that rejects an event whose `firedAt` is older
 * than a few minutes has a replay window of a few minutes.
 *
 * ### SHA-256, hex, prefixed
 *
 * `sha256=<hex>` is the shape GitHub, Stripe and Shopify all use, so a receiving developer already
 * has the code — the receiver computes an HMAC-SHA-256 of the raw request body under the same
 * secret, hex-encodes it, and compares the two in constant time. The prefix is not decoration: it is
 * what lets the algorithm change one day without every receiver silently accepting the new hash as
 * though it were the old one.
 */
object WebhookSignature {

    /** The header the signature travels in. */
    const val HEADER: String = "X-CoinePro-Signature"

    /**
     * The header carrying when this delivery was attempted, in epoch milliseconds.
     *
     * Outside the signature on purpose, and documented as such so nobody builds a check on it: it
     * is there for a receiver's own logs. The time that can be *trusted* is the one inside the
     * signed body.
     */
    const val TIMESTAMP_HEADER: String = "X-CoinePro-Timestamp"

    /** Names this app in a receiver's logs, which is what tells them who to come back to. */
    const val USER_AGENT: String = "CoinePro-Android-Webhook/1"

    private const val ALGORITHM = "HmacSHA256"
    private const val PREFIX = "sha256="

    /**
     * The signature for [body] under [secret], or null where there is no secret to sign with.
     *
     * Null rather than a hash of an empty key. A signature computed under no secret verifies for
     * anybody who guesses that, which is worse than no signature at all: it looks like proof.
     *
     * Deterministic, so a receiver's own implementation can be checked against a fixed body and a
     * fixed key without either side running.
     */
    fun of(body: String, secret: String?): String? {
        val key = secret?.takeIf(String::isNotBlank) ?: return null
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        val digest = mac.doFinal(body.toByteArray(StandardCharsets.UTF_8))
        return PREFIX + digest.toHex()
    }

    /** Lower-case hex, which is what every receiving library produces by default. */
    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4])
            out.append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
