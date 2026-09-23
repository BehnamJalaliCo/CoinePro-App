package javax.net.ssl

open class SSLException(message: String? = null, cause: Throwable? = null) : java.io.IOException(message, cause)
class SSLPeerUnverifiedException(message: String? = null) : SSLException(message)
class SSLHandshakeException(message: String? = null) : SSLException(message)
