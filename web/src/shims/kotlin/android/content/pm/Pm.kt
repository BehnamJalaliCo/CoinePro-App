@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content.pm

/** The page as a package: no signing certificates to read, which the phone treats as "not checked". */
class PackageManager {
    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo()
    fun getPackageInfo(packageName: String, flags: PackageInfoFlags): PackageInfo = PackageInfo()
    fun hasSystemFeature(name: String): Boolean = false
    class PackageInfoFlags private constructor() { companion object { fun of(v: Long) = PackageInfoFlags() } }
    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
        const val GET_SIGNATURES = 64
        const val GET_SIGNING_CERTIFICATES = 0x08000000
        const val FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture"
    }
}

class Signature(private val bytes: ByteArray) { fun toByteArray(): ByteArray = bytes }

class SigningInfo {
    fun hasMultipleSigners(): Boolean = false
    val apkContentsSigners: Array<Signature> get() = emptyArray()
    val signingCertificateHistory: Array<Signature> get() = emptyArray()
}

class PackageInfo {
    var versionName: String? = null
    var longVersionCode: Long = 0
    val signingInfo: SigningInfo? get() = null
    val signatures: Array<Signature>? get() = null
}
