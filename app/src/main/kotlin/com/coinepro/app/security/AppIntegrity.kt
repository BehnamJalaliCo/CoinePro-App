package com.coinepro.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.coinepro.app.BuildConfig
import java.security.MessageDigest

/** What the app can say about the certificate it is actually running under. */
sealed interface IntegrityState {

    /**
     * No fingerprint was compiled in, so nothing was checked.
     *
     * Every debug build, and any release built without the signing configuration. Not a failure:
     * refusing to run here would brick development, and a check that cannot be performed must not
     * be reported as a check that passed.
     */
    data object NotChecked : IntegrityState

    /** The running APK is signed by a key this build was told to expect. */
    data object Genuine : IntegrityState

    /** It is not. [actual] is what it is signed with, so a report can name it. */
    data class Repackaged(val actual: String) : IntegrityState
}

/**
 * Whether this APK is the one that was built, or a copy of it that somebody changed.
 *
 * ### What this stops, and what it does not
 *
 * Android will not install a modified APK under the original signature — the signature covers every
 * byte — so the only way to ship a changed CoinePro is to re-sign it with a different key. That is
 * the real threat for an app like this one and it is not theoretical: a trading app with a Persian
 * audience is exactly the sort of thing that gets rebuilt with a wallet-draining patch and handed
 * around on Telegram as "CoinePro, unlocked". This check makes that copy refuse to run, and the
 * person handing it out has to explain why.
 *
 * It does **not** stop somebody determined. They have the APK; they can find this class in the dex,
 * even obfuscated, and remove it. Nothing running on hardware the attacker controls can prevent
 * that, and any page claiming otherwise is selling something. What it does is raise the cost from
 * "re-sign it" to "understand and patch it", which is the difference between a copy that spreads
 * and one that does not.
 *
 * It is also not a substitute for the two things that actually protect an account: the server
 * deciding what a token may do, and the token being useless off this device.
 *
 * ### Why it cannot lock anybody out by accident
 *
 * The expected fingerprint is read from the keystore that signs the build, so a genuine build
 * carries its own and cannot fail. An empty list — every debug build — turns the check off. And
 * Play App Signing, which re-signs uploads with Google's key, is handled by
 * `COINEPRO_EXPECTED_SIGNERS`; see the note beside it in `app/build.gradle.kts`, because forgetting
 * it is the one way this could refuse a real install.
 */
object AppIntegrity {

    fun check(
        context: Context,
        expected: String = BuildConfig.EXPECTED_SIGNERS,
    ): IntegrityState = verdict(expected, fingerprints(context, "SHA-256"))

    /**
     * The comparison itself, with the platform taken out of it.
     *
     * Separate because this is where the bugs live and the platform read is where they do not: the
     * console writes `AB:CD:…`, the keystore reader writes `ABCD…`, Play adds a second legitimate
     * key, and any one of those turning into a refusal would stop the app starting for everybody.
     * A pure function can be tested against all four in a few lines; a `PackageManager` cannot.
     */
    internal fun verdict(expected: String, actual: List<String>): IntegrityState {
        val wanted = expected.split(",").mapNotNull(::normalise).toSet()
        if (wanted.isEmpty()) return IntegrityState.NotChecked
        // Nothing to compare — an install whose certificate could not be read. Fail open: this
        // check exists to stop a repackaged copy, not to strand somebody whose phone answered
        // strangely, and the server is what actually guards an account.
        val found = actual.mapNotNull(::normalise)
        if (found.isEmpty()) return IntegrityState.NotChecked
        return if (found.any { it in wanted }) {
            IntegrityState.Genuine
        } else {
            IntegrityState.Repackaged(actual.first())
        }
    }

    private fun normalise(raw: String): String? =
        raw.trim().replace(":", "").uppercase().takeIf { it.isNotEmpty() }

    /**
     * The signing certificates of the running app, as colon-separated hex.
     *
     * Public information — it is in the APK anybody can download — and the reason it is exposed is
     * that Google's console asks for it and there is otherwise no way to be sure which key a
     * given install carries. A phone showing its own answer settles that in one look.
     */
    fun fingerprints(context: Context, algorithm: String = "SHA-1"): List<String> = runCatching {
        val manager = context.packageManager
        val certificates: Array<out android.content.pm.Signature> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = manager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val signing = info.signingInfo ?: return@runCatching emptyList()
                // A rotated key reports a history; the current one is what an install is verified
                // against, so it is the one to compare.
                if (signing.hasMultipleSigners()) {
                    signing.apkContentsSigners
                } else {
                    signing.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                    ?: return@runCatching emptyList()
            }
        certificates.map { signature ->
            MessageDigest.getInstance(algorithm)
                .digest(signature.toByteArray())
                .joinToString(":") { byte -> "%02X".format(byte) }
        }
    }.getOrDefault(emptyList())
}
