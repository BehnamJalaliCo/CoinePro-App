@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package android.content

import android.net.Uri
import android.os.Bundle

private fun openJs(url: String): Unit = js("(function () { try { window.open(url, '_blank', 'noopener'); } catch (e) {} })()")
private fun shareTextJs(text: String, title: String): Unit = js(
    "(function () { try { if (navigator.share) { navigator.share({ title: title, text: text }).catch(function () {}); } else if (navigator.clipboard) { navigator.clipboard.writeText(text); } } catch (e) {} })()",
)
private fun copyJs(text: String): Unit = js("(function () { try { navigator.clipboard.writeText(text); } catch (e) {} })()")

/**
 * The page, in the role the phone gives its `Context`: somewhere to start an activity from. An
 * intent to *view* a link opens it; an intent to *send* text uses the browser's share sheet, or
 * the clipboard where there is none. Anything else is the phone's own business and is ignored
 * rather than faked.
 */
private val connectivity by lazy { android.net.ConnectivityManager() }

open class Context {
    open val packageName: String = "com.coinepro.app"
    val applicationContext: Context get() = this
    val contentResolver: ContentResolver = ContentResolver()
    val resources: android.content.res.Resources = android.content.res.Resources()
    val assets: android.content.res.AssetManager get() = resources.assets
    val cacheDir: java.io.File = java.io.File("/cache")
    val filesDir: java.io.File = java.io.File("/files")

    open fun startActivity(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_DIAL, Intent.ACTION_SENDTO -> intent.data?.toString()?.let(::openJs)
            Intent.ACTION_SEND -> shareTextJs(intent.extras[Intent.EXTRA_TEXT]?.toString() ?: "", intent.extras[Intent.EXTRA_SUBJECT]?.toString() ?: "")
            Intent.ACTION_CHOOSER -> (intent.extras[Intent.EXTRA_INTENT] as? Intent)?.let(::startActivity)
        }
    }
    fun startActivity(intent: Intent, options: Bundle?) = startActivity(intent)
    fun getString(id: Int): String = com.coinepro.web.Strings.get(com.coinepro.web.WebResources.nameOf(id))
    fun getString(id: Int, vararg args: Any?): String = com.coinepro.web.formatAndroid(getString(id), *args)
    fun getColor(id: Int): Int = 0
    fun getSystemService(name: String): Any? = when (name) {
        CLIPBOARD_SERVICE -> ClipboardManager()
        CONNECTIVITY_SERVICE -> connectivity
        else -> null
    }
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSystemService(type: kotlin.reflect.KClass<T>): T? = when (type) {
        android.net.ConnectivityManager::class -> connectivity as T
        ClipboardManager::class -> ClipboardManager() as T
        else -> null
    }
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences(name)
    fun createConfigurationContext(configuration: android.content.res.Configuration): Context = this
    val packageManager: android.content.pm.PackageManager get() = android.content.pm.PackageManager()
    open fun sendBroadcast(intent: Intent) {}
    fun getExternalFilesDir(type: String?): java.io.File? = filesDir
    fun checkSelfPermission(permission: String): Int =
        if (com.coinepro.web.content.WebPermissions.granted(permission)) android.content.pm.PackageManager.PERMISSION_GRANTED
        else android.content.pm.PackageManager.PERMISSION_DENIED

    /** The page: the one activity, the one context. */
    object Page : androidx.fragment.app.FragmentActivity()

    companion object {
        const val MODE_PRIVATE = 0
        const val CLIPBOARD_SERVICE = "clipboard"
        const val NOTIFICATION_SERVICE = "notification"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val VIBRATOR_SERVICE = "vibrator"
        const val INPUT_METHOD_SERVICE = "input_method"
    }
}

/** Files the reader picked or is saving — see `com.coinepro.web.content.WebContent`. */
class ContentResolver {
    fun openInputStream(uri: android.net.Uri): java.io.InputStream? {
        val text = uri.toString()
        if (text.startsWith("webfile://")) return java.io.FileInputStream(text.removePrefix("webfile://"))
        if (text.startsWith("file://")) return java.io.FileInputStream(text.removePrefix("file://"))
        return com.coinepro.web.content.WebContent.bytes(text)?.let { java.io.ByteArrayInputStream(it) }
            ?: throw java.io.FileNotFoundException(text)
    }
    fun openOutputStream(uri: android.net.Uri): java.io.OutputStream? = openOutputStream(uri, "w")
    fun openOutputStream(uri: android.net.Uri, mode: String): java.io.OutputStream? = DownloadStream(uri.toString())
    fun getType(uri: android.net.Uri): String? = com.coinepro.web.content.WebContent.type(uri.toString())
    fun takePersistableUriPermission(uri: android.net.Uri, flags: Int) {}
    fun releasePersistableUriPermission(uri: android.net.Uri, flags: Int) {}
    fun query(uri: android.net.Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): android.database.Cursor? =
        android.database.Cursor(uri.lastPathSegment)
}

private class DownloadStream(private val uri: String) : java.io.OutputStream() {
    private val buffer = java.io.ByteArrayOutputStream()
    private var saved = false
    override fun write(b: Int) = buffer.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)
    override fun close() {
        if (saved) return
        saved = true
        com.coinepro.web.content.WebContent.save(uri, buffer.toByteArray())
    }
}

class ClipData private constructor(val label: CharSequence?, val text: CharSequence?) {
    companion object { fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(label, text) }
    fun getItemAt(i: Int): Item = Item(text)
    val itemCount: Int get() = 1
    class Item(val text: CharSequence?) {
        fun coerceToText(context: Context?): CharSequence = text ?: ""
    }
}

class ClipboardManager {
    fun setPrimaryClip(clip: ClipData) { copyJs(clip.text?.toString() ?: "") }
    val primaryClip: ClipData? get() = null
    fun hasPrimaryClip(): Boolean = false
}

open class ActivityNotFoundException(message: String? = null) : RuntimeException(message)

class Intent(var action: String? = null, var data: Uri? = null) {
    constructor(action: String?) : this(action, null)
    constructor(context: Context, cls: Any) : this(null, null)
    val extras: MutableMap<String, Any?> = LinkedHashMap()
    var type: String? = null
    var flags: Int = 0
    var `package`: String? = null
    fun putExtra(name: String, value: Any?): Intent = apply { extras[name] = value }
    fun addFlags(f: Int): Intent = apply { flags = flags or f }
    fun setFlags(f: Int): Intent = apply { flags = f }
    fun setData(uri: Uri?): Intent = apply { data = uri }
    fun setType(t: String?): Intent = apply { type = t }
    fun setAction(a: String?): Intent = apply { action = a }
    fun setPackage(p: String?): Intent = apply { `package` = p }
    fun addCategory(c: String): Intent = this
    fun setDataAndType(uri: Uri?, t: String?): Intent = apply { data = uri; type = t }
    fun getStringExtra(name: String): String? = extras[name] as? String
    fun getBooleanExtra(name: String, fallback: Boolean): Boolean = extras[name] as? Boolean ?: fallback
    fun getIntExtra(name: String, fallback: Int): Int = extras[name] as? Int ?: fallback
    fun getLongExtra(name: String, fallback: Long): Long = extras[name] as? Long ?: fallback
    fun hasExtra(name: String): Boolean = name in extras
    fun resolveActivity(pm: Any?): Any? = this

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_DIAL = "android.intent.action.DIAL"
        const val ACTION_CHOOSER = "android.intent.action.CHOOSER"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_INTENT = "android.intent.extra.INTENT"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
        const val FLAG_ACTIVITY_CLEAR_TASK = 0x00008000
        const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001
        fun createChooser(target: Intent, title: CharSequence?): Intent =
            Intent(ACTION_CHOOSER).putExtra(EXTRA_INTENT, target).putExtra(EXTRA_TITLE, title)
    }
}

/** Stored in `localStorage` under `name/key`, so a preference survives a reload as on the phone. */
class SharedPreferences(private val name: String) {
    fun getString(key: String, fallback: String?): String? = com.coinepro.web.stored("$name/$key") ?: fallback
    fun getBoolean(key: String, fallback: Boolean): Boolean = com.coinepro.web.stored("$name/$key")?.toBooleanStrictOrNull() ?: fallback
    fun getInt(key: String, fallback: Int): Int = com.coinepro.web.stored("$name/$key")?.toIntOrNull() ?: fallback
    fun getLong(key: String, fallback: Long): Long = com.coinepro.web.stored("$name/$key")?.toLongOrNull() ?: fallback
    fun getFloat(key: String, fallback: Float): Float = com.coinepro.web.stored("$name/$key")?.toFloatOrNull() ?: fallback
    fun contains(key: String): Boolean = com.coinepro.web.stored("$name/$key") != null
    val all: Map<String, *> get() = emptyMap<String, Any>()
    fun edit(): Editor = Editor(name)

    class Editor(private val name: String) {
        private val pending = LinkedHashMap<String, String?>()
        fun putString(k: String, v: String?): Editor = apply { pending[k] = v }
        fun putBoolean(k: String, v: Boolean): Editor = apply { pending[k] = v.toString() }
        fun putInt(k: String, v: Int): Editor = apply { pending[k] = v.toString() }
        fun putLong(k: String, v: Long): Editor = apply { pending[k] = v.toString() }
        fun putFloat(k: String, v: Float): Editor = apply { pending[k] = v.toString() }
        fun remove(k: String): Editor = apply { pending[k] = null }
        fun clear(): Editor = this
        fun apply() { pending.forEach { (k, v) -> if (v == null) com.coinepro.web.unstore("$name/$k") else com.coinepro.web.store("$name/$k", v) } }
        fun commit(): Boolean { apply(); return true }
    }
}

open class ContextWrapper(val baseContext: Context) : Context()

open class BroadcastReceiver {
    open fun onReceive(context: Context, intent: Intent) {}
    fun goAsync(): PendingResult = PendingResult()
    class PendingResult { fun finish() {} }
}

class ComponentName(val packageName: String, val className: String) {
    constructor(context: Context, klass: kotlin.reflect.KClass<*>) : this(context.packageName, klass.simpleName ?: "")
}

class IntentFilter(vararg actions: String)
