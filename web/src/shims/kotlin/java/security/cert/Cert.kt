package java.security.cert

open class CertificateException(message: String? = null, cause: Throwable? = null) : java.security.GeneralSecurityException(message, cause)
class CertificateExpiredException(message: String? = null) : CertificateException(message)
abstract class Certificate(val type: String) { abstract val encoded: ByteArray }
abstract class X509Certificate : Certificate("X.509")
