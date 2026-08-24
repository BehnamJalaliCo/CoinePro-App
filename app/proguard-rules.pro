# CoinePro app-specific R8/ProGuard rules.

# --- Transport models ---
# Every wire model is constructed only by Gson reflection and is named by field, not by
# @SerializedName: NetworkFactory applies FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES to the
# Kotlin field names. Without these keeps R8 removes the classes outright, Retrofit reads the
# erased generic signature as Continuation<Object>, and no API response can be parsed.
-keep class com.coinepro.**Dto { *; }
-keep class com.coinepro.**Dto$* { *; }

# Auth crosses the Retrofit boundary with domain types rather than *Dto types.
-keep class com.coinepro.core.auth.UserProfile { *; }
-keep class com.coinepro.core.auth.TelegramAuthPayload { *; }

# Retrofit resolves suspend response types from the generic signature, so it must survive.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# --- Telegram sign-in bridge ---
# The WebView calls this method by name from JavaScript; R8 cannot see that reference.
-keepclassmembers class com.coinepro.feature.auth.TelegramBridge {
    @android.webkit.JavascriptInterface <methods>;
}
