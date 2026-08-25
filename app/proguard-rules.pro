# CoinePro app-specific R8/ProGuard rules.

# --- Transport models ---
# Every wire model is constructed only by Gson reflection. Most are named by field —
# NetworkFactory applies FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES to the Kotlin field names —
# and the signed-in profile additionally carries @SerializedName, because the two backends spell
# that one object differently and a single policy cannot read both. Either way, without these keeps
# R8 removes the classes outright, Retrofit reads the erased generic signature as
# Continuation<Object>, and no API response can be parsed.
-keep class com.coinepro.**Dto { *; }
-keep class com.coinepro.**Dto$* { *; }

# Sent to the server as a body rather than parsed from one, so it is not named *Dto.
-keep class com.coinepro.core.auth.TelegramAuthPayload { *; }

# Retrofit resolves suspend response types from the generic signature, so it must survive.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# --- Telegram sign-in bridge ---
# The WebView calls this method by name from JavaScript; R8 cannot see that reference.
-keepclassmembers class com.coinepro.feature.auth.TelegramBridge {
    @android.webkit.JavascriptInterface <methods>;
}
