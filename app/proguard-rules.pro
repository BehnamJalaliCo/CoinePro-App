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

# --- Field names, everywhere ---
# The rule above protects the classes named *Dto and nothing else, and that turned out to be a trap.
# Nine request bodies are not named that way — LoginRequest, GoogleRequest, RefreshRequest,
# RegisterStartRequest, RegisterVerifyRequest, ForgotPasswordRequest, ResetPasswordRequest,
# ProgressBody, QuizSubmitBody — and none of them carries @SerializedName. Gson serialises by
# reflecting over fields, so with their names obfuscated a sign-in posts {"a":"…","b":"…"} and the
# server rejects it. In a release build only. Every request would fail and nothing would say why.
#
# So this is deliberately broad rather than another naming convention: a convention is what failed.
#
# `keepclassmembers` and not `keepclassmembernames`. The weaker rule only protects the names of
# members that survive, and these do not survive at all: the mapping file for LoginRequest listed
# `<init>`, `equals`, `hashCode`, `toString` and **no fields**, because nothing in the app reads
# them and R8 cannot see Gson's reflection. A kept name on a deleted field is nothing. This rule
# keeps the fields themselves, on classes that are kept anyway, so it costs a little dex and stops
# every request body in the app from serialising as `{}`.
-keepclassmembers class com.coinepro.** { <fields>; }

# --- Retrofit service interfaces ---
# Every method on these is called through a dynamic proxy, so nothing in the app references them
# directly and R8 has no reason to keep them. Retrofit ships a rule for this, and it is repeated here
# because the app would still compile and still install without it — the failure arrives at the
# first request, as a method that resolves to nothing.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1> { <methods>; }

# Retrofit resolves suspend response types from the generic signature, so it must survive.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# --- Telegram sign-in bridge ---
# The WebView calls this method by name from JavaScript; R8 cannot see that reference.
-keepclassmembers class com.coinepro.feature.auth.TelegramBridge {
    @android.webkit.JavascriptInterface <methods>;
}
