@file:Suppress("unused", "UNUSED_PARAMETER")

package android.os

object Build {
    object VERSION { const val SDK_INT: Int = 35; const val RELEASE: String = "web" }
    object VERSION_CODES {
        const val M = 23; const val N = 24; const val O = 26; const val P = 28; const val Q = 29; const val R = 30
        const val S = 31; const val S_V2 = 32; const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34; const val VANILLA_ICE_CREAM = 35
    }
    const val MANUFACTURER: String = "browser"
    const val MODEL: String = "web"
    const val BRAND: String = "web"
    const val DEVICE: String = "web"
    const val FINGERPRINT: String = "web"
    val SUPPORTED_ABIS: Array<String> = arrayOf("wasm")
}

class Bundle {
    private val map = LinkedHashMap<String, Any?>()
    fun putString(k: String, v: String?) { map[k] = v }
    fun getString(k: String): String? = map[k] as? String
    fun putInt(k: String, v: Int) { map[k] = v }
    fun getInt(k: String, fallback: Int = 0): Int = map[k] as? Int ?: fallback
    fun putBoolean(k: String, v: Boolean) { map[k] = v }
    fun getBoolean(k: String, fallback: Boolean = false): Boolean = map[k] as? Boolean ?: fallback
    fun containsKey(k: String): Boolean = k in map
}

class Handler(looper: Looper? = null) {
    fun post(r: () -> Unit): Boolean { r(); return true }
    fun postDelayed(r: () -> Unit, delay: Long): Boolean { r(); return true }
    fun removeCallbacksAndMessages(token: Any?) {}
}

class Looper { companion object { fun getMainLooper(): Looper = Looper(); fun myLooper(): Looper? = Looper() } }

object SystemClock {
    fun elapsedRealtime(): Long = com.coinepro.web.jvm.nowMillisJs().toLong()
    fun uptimeMillis(): Long = com.coinepro.web.jvm.nowMillisJs().toLong()
    fun elapsedRealtimeNanos(): Long = com.coinepro.web.jvm.nanoTimeJs().toLong()
}

class Parcel
interface Parcelable
class PowerManager
class VibrationEffect
class Vibrator
class StatFs(path: String) { val availableBytes: Long = 0; val totalBytes: Long = 0 }
object Environment { fun getDataDirectory(): java.io.File = java.io.File("/data") }
