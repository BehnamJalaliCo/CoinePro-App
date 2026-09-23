package retrofit2.converter.gson

/** Carried for the phone's builder calls; the browser's services decode through `com.coinepro.web.wire`. */
class GsonConverterFactory private constructor() {
    companion object {
        fun create(): GsonConverterFactory = GsonConverterFactory()
        fun create(gson: com.google.gson.Gson): GsonConverterFactory = GsonConverterFactory()
    }
}
