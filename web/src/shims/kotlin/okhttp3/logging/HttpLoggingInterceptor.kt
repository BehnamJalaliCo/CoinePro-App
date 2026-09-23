package okhttp3.logging

import okhttp3.Interceptor
import okhttp3.Response

/** The browser's network panel already shows every exchange; this passes each one through. */
class HttpLoggingInterceptor(private val logger: Logger = Logger.DEFAULT) : Interceptor {
    enum class Level { NONE, BASIC, HEADERS, BODY }
    fun interface Logger {
        fun log(message: String)
        companion object { val DEFAULT: Logger = Logger { } }
    }
    var level: Level = Level.NONE
    fun setLevel(level: Level): HttpLoggingInterceptor = apply { this.level = level }
    fun redactHeader(name: String) {}
    override suspend fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
