package com.coinepro.app.owner

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coinepro.app.AppLanguageStore
import com.coinepro.app.BuildConfig
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.app.pulse.MarketPulse
import com.coinepro.app.pulse.ReaderClock
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BrandConfig
import com.coinepro.core.common.FeatureFlags
import com.coinepro.core.datastore.NotificationSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The phone's half of the owner's console (5.30.0): a check-in with pro-chart.com every few hours,
 * and what comes back — campaigns as notifications or cards, and two banners.
 *
 * ### What is sent, and what is not
 *
 * A random install id made on this phone (not the account, not the device's own ids), the version,
 * the store the build came from, the language, the Android version and the time zone's name. That
 * is what lets the owner count active installs and aim a campaign at a version or a language. The
 * server keeps a salted hash of the id and never the address. Nothing about the reader's account,
 * markets or activity goes with it.
 *
 * ### When
 *
 * From the market notifications' quarter-hourly run and when the app comes to the front, but at
 * most every `hello_every_hours` (six by default, the owner's to change). A failed check-in waits
 * half an hour before the next try. It needs no Firebase and arrives in Iran, like the market
 * notifications themselves.
 */
@Singleton
class OwnerChannel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: NotificationSettingsStore,
) : OwnerActions {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    init {
        OwnerInbox.actions = this
        runCatching { OwnerInbox.publish(boardFrom(preferences.getString(LAST_REPLY, null), System.currentTimeMillis())) }
    }

    /** One check-in, unless the last was recent; [force] skips the wait. Never throws. */
    suspend fun run(force: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock {
            val now = System.currentTimeMillis()
            val every = preferences.getInt(EVERY_HOURS, DEFAULT_EVERY_HOURS).coerceIn(1, 24) * HOUR
            val last = preferences.getLong(LAST_HELLO, 0L)
            val failed = preferences.getLong(LAST_FAILURE, 0L)
            if (!force && (now - last < every || now - failed < RETRY_AFTER)) {
                OwnerInbox.publish(boardFrom(preferences.getString(LAST_REPLY, null), now))
                return@withLock
            }
            val reply = post(HELLO_PATH, helloBody())
            if (reply == null) {
                preferences.edit().putLong(LAST_FAILURE, now).apply()
                return@withLock
            }
            val hours = runCatching { JSONObject(reply).optJSONObject("config")?.optInt("hello_every_hours", DEFAULT_EVERY_HOURS) }
                .getOrNull() ?: DEFAULT_EVERY_HOURS
            preferences.edit()
                .putLong(LAST_HELLO, now)
                .putLong(LAST_FAILURE, 0L)
                .putInt(EVERY_HOURS, hours)
                .putString(LAST_REPLY, reply)
                .apply()
            deliver(reply, now)
        }
    }

    private suspend fun deliver(reply: String, now: Long) {
        val messages = messagesFrom(reply)
        val settings = runCatching { settingsStore.settings.first() }.getOrNull()
        val zone = runCatching { ReaderClock.zone(context) }.getOrDefault(ZoneId.systemDefault())
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val minute = local.hour * 60 + local.minute
        val night = MarketPulse.quiet(minute)
        val canPost = canPost()
        val (notify, _) = OwnerPlan.plan(
            messages = messages,
            // A campaign already seen as a card is not sent again as a notification.
            settled = settled() + preferences.getStringSet(SHOWN, emptySet()).orEmpty().mapNotNull(String::toLongOrNull),
            nowMillis = now,
            mayNotify = { message ->
                canPost && settings != null && OwnerPlan.allowed(settings, message, now, minute, night)
            },
            quietNow = night || settings?.quietHours?.contains(minute) == true,
        )
        notify.forEach { message ->
            if (post(message)) {
                settle(message.id)
                event(message.id, "delivered")
            }
        }
        OwnerInbox.publish(boardFrom(reply, now))
    }

    // ── The board ───────────────────────────────────────────────────────────────────────────────

    private fun boardFrom(reply: String?, now: Long): OwnerBoard {
        if (reply == null) return OwnerBoard()
        val json = runCatching { JSONObject(reply) }.getOrNull() ?: return OwnerBoard()
        val config = json.optJSONObject("config")
        val english = english()
        fun banner(name: String): Pair<Boolean, String> {
            val item = config?.optJSONObject(name) ?: return false to ""
            val text = item.optString(if (english) "message_en" else "message_fa").ifBlank { item.optString("message_fa") }
            return item.optBoolean("on") to text
        }
        val (maintenanceOn, maintenance) = banner("maintenance")
        val (nudgeOn, nudge) = banner("update_nudge")
        val settled = settled()
        val (notify, cards) = OwnerPlan.plan(messagesFrom(reply), settled, now, mayNotify = { false }, quietNow = false)
        return OwnerBoard(
            maintenance = maintenance.takeIf { maintenanceOn },
            updateNudge = OwnerPlan.nudge(nudgeOn, nudge, config?.optLong("min_version_code") ?: 0L, BuildConfig.VERSION_CODE.toLong()),
            cards = (notify + cards).filter { it.id !in settled },
        )
    }

    private fun messagesFrom(reply: String): List<OwnerMessage> = runCatching {
        val array = JSONObject(reply).optJSONArray("messages") ?: return@runCatching emptyList()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("id", -1L).takeIf { it > 0 } ?: return@mapNotNull null
            OwnerMessage(
                id = id,
                kind = item.optString("kind"),
                high = item.optString("priority") == "high",
                title = item.optString("title").take(MAX_TITLE),
                body = item.optString("body").take(MAX_BODY),
                cta = item.optString("cta").take(MAX_CTA),
                link = item.optString("link").takeIf(::isSafeLink).orEmpty(),
                coupon = item.optString("coupon").takeIf { it.matches(COUPON) }.orEmpty(),
                notify = item.optBoolean("notify", true),
                endsAtMillis = if (item.isNull("ends_at")) null else item.optLong("ends_at") * 1000,
            )
        }
    }.getOrDefault(emptyList())

    // ── Notifications ───────────────────────────────────────────────────────────────────────────

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // `canPost` is checked before anything reaches here; lint cannot follow the helper.
    @android.annotation.SuppressLint("MissingPermission")
    private fun post(message: OwnerMessage): Boolean = runCatching {
        val localized = AppLanguageStore.localized(context)
        val open = PendingIntent.getActivity(
            context,
            REQUEST_BASE + (message.id % REQUEST_SPAN).toInt(),
            openIntent(message),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (message.coupon.isNotBlank()) {
            message.body + "\n" + localized.getString(R.string.owner_coupon_label, message.coupon)
        } else {
            message.body
        }
        val builder = NotificationCompat.Builder(context, NotificationChannels.channelId(OwnerPlan.categoryOf(message.kind)))
            .setSmallIcon(R.drawable.ic_stat_coinepro)
            .setColor(if (message.kind == "offer") GOLD else ACCENT)
            .setContentTitle(message.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(if (message.high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (message.kind == "offer") NotificationCompat.CATEGORY_PROMO else NotificationCompat.CATEGORY_RECOMMENDATION)
        if (message.cta.isNotBlank()) builder.addAction(0, message.cta, open)
        message.endsAtMillis?.let { ends ->
            val left = ends - System.currentTimeMillis()
            if (left > 0) builder.setTimeoutAfter(left)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + (message.id % REQUEST_SPAN).toInt(), builder.build())
        true
    }.getOrDefault(false)

    private fun openIntent(message: OwnerMessage): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .apply { if (message.link.startsWith(BrandConfig.SCHEME_PREFIX)) data = Uri.parse(message.link) }
            .putExtra(EXTRA_MESSAGE_ID, message.id)
            .putExtra(EXTRA_LINK, message.link.takeIf { it.startsWith("https://") })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /**
     * A tap on one of the console's notifications: counted as opened, and a web address handed to
     * the browser. An app link (`coinepro://…`) is the intent's data and the activity's own deep-link
     * handling takes it from there. The extra is removed so a rotation does not count it twice.
     */
    fun consume(intent: Intent?, launcher: Context) {
        val id = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L)?.takeIf { it > 0 } ?: return
        val link = intent.getStringExtra(EXTRA_LINK)
        intent.removeExtra(EXTRA_MESSAGE_ID)
        intent.removeExtra(EXTRA_LINK)
        settle(id)
        event(id, "opened")
        if (link != null && isSafeLink(link) && link.startsWith("https://")) browse(launcher, link)
    }

    // ── OwnerActions: the cards ─────────────────────────────────────────────────────────────────

    override fun open(message: OwnerMessage) {
        settle(message.id)
        event(message.id, "opened")
        when {
            message.link.startsWith("https://") -> browse(context, message.link)
            message.link.startsWith(BrandConfig.SCHEME_PREFIX) -> runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse(message.link))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }

    override fun dismiss(message: OwnerMessage) {
        settle(message.id)
        event(message.id, "dismissed")
    }

    override fun shown(message: OwnerMessage) {
        val shown = preferences.getStringSet(SHOWN, emptySet()).orEmpty()
        if (message.id.toString() in shown) return
        preferences.edit().putStringSet(SHOWN, (shown + message.id.toString()).toList().takeLast(MAX_REMEMBERED).toSet()).apply()
        event(message.id, "delivered")
    }

    // ── Plumbing ────────────────────────────────────────────────────────────────────────────────

    private fun browse(launcher: Context, link: String) {
        runCatching {
            launcher.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Campaigns this install has finished with: notified, opened or dismissed. */
    private fun settled(): Set<Long> =
        preferences.getStringSet(SETTLED, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()

    private fun settle(id: Long) {
        val now = preferences.getStringSet(SETTLED, emptySet()).orEmpty()
        if (id.toString() in now) return
        preferences.edit().putStringSet(SETTLED, (now + id.toString()).toList().takeLast(MAX_REMEMBERED).toSet()).apply()
    }

    private fun installId(): String {
        preferences.getString(INSTALL_ID, null)?.let { return it }
        val made = UUID.randomUUID().toString()
        preferences.edit().putString(INSTALL_ID, made).apply()
        return made
    }

    private fun english(): Boolean = runCatching { AppLanguageStore.current(context) == AppLanguage.ENGLISH }.getOrDefault(false)

    private fun helloBody(): JSONObject = JSONObject()
        .put("install_id", installId())
        .put("platform", "android")
        .put("flavour", if (FeatureFlags.storeRestricted) "bazaar" else "direct")
        .put("lang", if (english()) "en" else "fa")
        .put("version", BuildConfig.VERSION_NAME)
        .put("version_code", BuildConfig.VERSION_CODE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("tz", ZoneId.systemDefault().id)

    private fun event(id: Long, kind: String) {
        scope.launch {
            post(EVENT_PATH, JSONObject().put("install_id", installId()).put("message_id", id).put("kind", kind))
        }
    }

    /** A POST to the brand host with no credential of any kind; null on any failure. */
    private fun post(path: String, body: JSONObject): String? = runCatching {
        val connection = URL(BrandConfig.WEB_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ProChart-Android/" + BuildConfig.VERSION_NAME)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { stream -> stream.readBytes().take(MAX_REPLY).toByteArray().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface Access {
        fun ownerChannel(): OwnerChannel
    }

    companion object {
        /** The one instance, for a caller Hilt does not build. */
        fun from(context: Context): OwnerChannel =
            dagger.hilt.android.EntryPointAccessors.fromApplication(context, Access::class.java).ownerChannel()

        const val EXTRA_MESSAGE_ID = "com.coinepro.app.owner.MESSAGE_ID"
        const val EXTRA_LINK = "com.coinepro.app.owner.LINK"

        private const val FILE = "owner_channel"
        private const val INSTALL_ID = "install_id"
        private const val LAST_HELLO = "last_hello"
        private const val LAST_FAILURE = "last_failure"
        private const val LAST_REPLY = "last_reply"
        private const val EVERY_HOURS = "every_hours"
        private const val SETTLED = "settled"
        private const val SHOWN = "shown"
        private const val HELLO_PATH = "/api/app/hello"
        private const val EVENT_PATH = "/api/app/event"
        private const val DEFAULT_EVERY_HOURS = 6
        private const val HOUR = 3_600_000L
        private const val RETRY_AFTER = 30L * 60 * 1000
        private const val TIMEOUT = 10_000
        private const val MAX_REPLY = 256 * 1024
        private const val MAX_REMEMBERED = 200
        private const val MAX_TITLE = 80
        private const val MAX_BODY = 400
        private const val MAX_CTA = 30
        private const val REQUEST_BASE = 0x0A_0000
        private const val NOTIFICATION_BASE = 0x0A_0000
        private const val REQUEST_SPAN = 0xFFFF
        private const val GOLD = 0xFFD8A848.toInt()
        private const val ACCENT = 0xFF7C6CFF.toInt()
        private val COUPON = Regex("[A-Z0-9][A-Z0-9-]{2,39}")

        /** The console allows these two shapes and nothing else; checked again here, where it is acted on. */
        fun isSafeLink(link: String): Boolean =
            link.isEmpty() || link.startsWith(BrandConfig.SCHEME_PREFIX) ||
                (link.startsWith("https://") && link.length <= 300 && link.none { it.isWhitespace() })
    }
}
