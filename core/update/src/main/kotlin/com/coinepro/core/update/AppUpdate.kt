package com.coinepro.core.update

/**
 * Whether there is a newer build than the one running, and what to say about it.
 *
 * ### Why this exists at all
 *
 * Every Android app the reader has ever installed updates itself without being asked, because the
 * Play Store does it. **This one is not on the Play Store and will not be**: Iran is not on Google's
 * list of countries a developer account may be registered from, and Play does not serve installs
 * into the country either (`docs/PLAY_COUNTRIES.md` has the reading, from Google's own pages). The
 * app is downloaded as an APK and installed by hand, which works — and has exactly one thing
 * missing from it, which is any way for a reader to learn that a newer one exists.
 *
 * Without that, an old build is silently permanent. The reader has no reason to look, nothing tells
 * them, and a fix shipped today reaches only the people who happen to visit the channel it was
 * announced on. That is the hole this closes, and it is the one piece of the store the product has
 * to build for itself. `docs/release/DISTRIBUTION.md` is the whole picture.
 *
 * ### What it deliberately does not do
 *
 * **It does not download, and it does not install.** The button hands the address to the browser and
 * the reader takes it from there — the same three taps they used to install the app in the first
 * place. Doing it in-process would mean `REQUEST_INSTALL_PACKAGES`, a permission that lets an app
 * put *arbitrary packages* on the phone and that a reader is right to be suspicious of, plus a
 * download manager, a file provider and a store of half-fetched APKs. For one link a year that is a
 * great deal of attack surface bought with a permission, and the thing it buys is one fewer tap.
 *
 * **It does not block.** [AppRelease.mandatory] changes the sentence, never the doors. A flag on a
 * server that can stop an installed app from opening is a remote kill switch, and a kill switch is
 * one compromised host away from being everyone's app at once. A reader on an old build is a reader
 * the product still owes a working chart to.
 *
 * **It does not decide by name.** Only [AppRelease.versionCode] is compared, because that integer is
 * the only thing the package manager itself compares — `scripts/release/version.py` derives it from
 * the name for precisely that reason. A name is for the reader; the order is the code's.
 */
sealed interface AppUpdateStatus {

    /**
     * Nothing is known: not asked yet, the host did not answer, or it answered something this app
     * will not act on (see [AppUpdate.publishable]).
     *
     * The three are one state on purpose. The screen draws nothing for any of them, and a reader
     * has no use for the difference between «we could not ask» and «the answer was malformed» —
     * both mean «carry on with what you have», which is what an update check owes when it fails.
     */
    data object Unknown : AppUpdateStatus

    /** The running build is the newest the host publishes. */
    data object Current : AppUpdateStatus

    /** There is a newer build, and this is it. */
    data class Available(val release: AppRelease) : AppUpdateStatus
}

/**
 * A published build, as the host describes it.
 *
 * @param versionCode the integer Android orders installs by — the only field the comparison reads.
 * @param versionName what the release is called, for the reader.
 * @param url where the APK can be downloaded. HTTPS, on a host this product publishes from.
 * @param sha256 the file's digest, shown so a reader can check what they downloaded is what was
 *   published. Required rather than optional: an APK offered without one is an APK nobody can
 *   verify, and this app is not going to be the thing that teaches its readers to install those.
 * @param notesFa what changed, in Persian.
 * @param notesEn the same, in English.
 * @param mandatory whether the owner considers this update important. It changes the wording only.
 */
data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val url: String,
    val sha256: String,
    val notesFa: String,
    val notesEn: String,
    val mandatory: Boolean,
)

/** The comparison, and the rules about what may be offered. */
object AppUpdate {

    /**
     * The hosts this product publishes builds from.
     *
     * An allow-list rather than «any HTTPS address», and the reason is the threat this route has
     * that no other route in the app has: every other response the app handles ends up as *text on
     * a screen*, and this one ends up as **an installable package on the reader's phone**. A host
     * that could name any address could send a reader anywhere with the product's own voice behind
     * it, which is a far better phishing page than a phishing page.
     *
     * `github.com` is here because that is where signed builds are published today — the release
     * workflow attaches `pro-chart-X.Y.Z.apk` to the tag. It stays on the list after the brand host
     * serves them too, because a release on GitHub is a release with a public, immutable record of
     * what was uploaded and when, and that is worth keeping as the second place to point at.
     */
    val PUBLISHING_HOSTS: Set<String> = setOf(
        "pro-chart.com",
        "www.pro-chart.com",
        "github.com",
    )

    /** Exactly the 64 hexadecimal characters of a SHA-256, in either case. */
    private val DIGEST = Regex("[0-9a-fA-F]{64}")

    private const val HTTPS = "https://"

    /**
     * Whether this app will put the release in front of a reader.
     *
     * Four conditions, and each one is a way an offer could be wrong rather than merely untidy:
     * an address that is not HTTPS is an APK anyone on the path can replace; an address off
     * [PUBLISHING_HOSTS] is an APK this product did not publish; a missing or malformed digest is
     * an APK nobody can check; and a blank name or a zero code is a record the host has not
     * finished writing. Anything that fails is [AppUpdateStatus.Unknown] — not an error shown to
     * the reader, because the reader did not do anything wrong and cannot do anything about it.
     */
    fun publishable(release: AppRelease): Boolean =
        release.versionCode > 0L &&
            release.versionName.isNotBlank() &&
            DIGEST.matches(release.sha256) &&
            hostOf(release.url) in PUBLISHING_HOSTS

    /**
     * The host of an `https://` address, or null where there is not one.
     *
     * Hand-parsed rather than handed to `android.net.Uri`, so this module stays plain Kotlin and
     * the rule can be tested without a device. The two things worth getting right are both here:
     *
     * * **userinfo.** `https://pro-chart.com@evil.example/x` has host `evil.example`, and a reader
     *   glancing at it sees the brand. Any `@` in the authority means this is not a plain address
     *   and it is refused outright rather than parsed cleverly.
     * * **case and port.** Hosts are case-insensitive and may carry `:443`; neither changes which
     *   machine is being named, so both are normalised away before the comparison.
     */
    private fun hostOf(url: String): String? {
        if (!url.startsWith(HTTPS)) return null
        val authority = url.removePrefix(HTTPS).substringBefore('/').substringBefore('?')
        if (authority.isEmpty() || '@' in authority) return null
        return authority.substringBefore(':').lowercase().takeIf { it.isNotEmpty() }
    }

    /**
     * What to show, given the running build's code and whatever the host said.
     *
     * @param installed `BuildConfig.VERSION_CODE` of the app doing the asking.
     * @param release the published build, or null where nothing was fetched.
     */
    fun decide(installed: Long, release: AppRelease?): AppUpdateStatus = when {
        release == null -> AppUpdateStatus.Unknown
        !publishable(release) -> AppUpdateStatus.Unknown
        release.versionCode > installed -> AppUpdateStatus.Available(release)
        // Equal or lower. Lower is not a mistake worth reporting: a reader running a build newer
        // than the published one is somebody testing, and telling them to «update» to an older
        // build is the one thing the package manager would refuse anyway.
        else -> AppUpdateStatus.Current
    }
}
