package com.coinepro.app

import android.content.Context
import android.content.res.Configuration
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.AppLocale
import java.util.Locale

/**
 * Persists the chosen app language and applies it to a [Context].
 *
 * Deliberately backed by SharedPreferences rather than DataStore: the language has to be known
 * inside `attachBaseContext`, which runs before dependency injection and cannot suspend. Reading it
 * asynchronously there would mean the first frame renders in the wrong language and then flips.
 */
object AppLanguageStore {
    private const val PREFERENCES = "app_language"
    private const val KEY_TAG = "language_tag"

    /** The stored language, or [AppLanguage.Default] when the reader has never chosen one. */
    fun current(context: Context): AppLanguage =
        AppLanguage.fromTag(preferences(context).getString(KEY_TAG, null))

    fun set(context: Context, language: AppLanguage) {
        preferences(context).edit().putString(KEY_TAG, language.tag).apply()
        // The process-wide answer for code that runs with no screen to ask — the widget worker and
        // the notification builder format their counts from it. Set here as well as in [apply],
        // because the reader who switches language does not restart the process.
        AppLocale.language = language
    }

    /**
     * Returns [context] re-based on the stored language.
     *
     * Both the locale list and the layout direction are set: `setLocales` alone leaves
     * `Configuration.getLayoutDirection` on its previous value for view-based surfaces such as the
     * WebView used by Telegram sign-in.
     */
    fun apply(context: Context): Context {
        val language = current(context)
        AppLocale.language = language
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
