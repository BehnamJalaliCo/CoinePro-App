package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.proseDigits
import com.coinepro.core.common.proseGroupedDigits
import com.coinepro.core.symbols.SymbolMeta

/**
 * The language the app is actually in, and the instrument names that follow from it.
 *
 * ### The defect this closes
 *
 * A symbol had one name. `SymbolMeta.description` is built by the classifier out of a Persian
 * table, so a reader with the app in English opened the chart on **«طلا / دلار آمریکا»** and a
 * watchlist of «بیت‌کوین/تتر» — the one part of the interface `strings.xml` could not reach,
 * because a symbol name is keyed by an ISO code and is read outside composition as well as in it.
 * `SymbolNames` carries both languages since 4.71.0; this is how a screen asks for the right one.
 *
 * ### Why the configuration and not `Locale.getDefault()`
 *
 * This app sets its language **per app**: `AppLanguageStore.apply` re-bases the activity's context
 * on the chosen locale in `attachBaseContext`, so the phone's language and the app's legitimately
 * disagree — a phone in English with the app in Persian is the common case here — and it is the
 * app's language that every `stringResource` on the screen is already resolved against. Reading the
 * composition's configuration is reading the same answer they read. `Locale.getDefault()` happens
 * to agree inside an activity, because `apply` sets it too, and does not agree in a widget worker
 * or any other process entry that never went through an activity: off composition the language is
 * read from `AppLanguageStore` itself, not from here and not from the default locale.
 */
@Composable
@ReadOnlyComposable
fun appLanguage(): AppLanguage =
    AppLanguage.fromTag(LocalConfiguration.current.locales[0]?.language)

/** True where the screen is in English. The shape every name lookup below is asked in. */
@Composable
@ReadOnlyComposable
fun inEnglish(): Boolean = appLanguage() == AppLanguage.ENGLISH

/** The instrument's full name — «Gold / US Dollar», «طلا / دلار آمریکا». */
@Composable
@ReadOnlyComposable
fun SymbolMeta.localName(): String = description(inEnglish())

/** The short form a list row carries under the ticker — «Gold/Dollar», «طلا/دلار». */
@Composable
@ReadOnlyComposable
fun SymbolMeta.localRowName(): String = listDescription(inEnglish())

/**
 * A prose count in the language of the screen it is drawn on: «۴ نماد», `4 symbols`.
 *
 * The composable half of `Int.proseDigits` in `core:common`. It reads the configuration the screen
 * is composed with rather than the process-wide [com.coinepro.core.common.AppLocale], which is what
 * makes a render test correct: a proof frame is drawn by overriding the configuration's locale, and
 * a global read would have every English frame in this repository still counting in Persian.
 *
 * Market figures are not counts and never come through here — a price, a percentage, a volume or an
 * axis figure is `MarketNumberFormatter`'s and is Latin in both languages, by the owner's rule and
 * by the `check_numeric_styles_are_latin` gate.
 */
@Composable
@ReadOnlyComposable
fun Int.proseDigits(): String = proseDigits(inEnglish())

/** The grouped form — «۵۲٬۳۴۰ عضو», `52,340 members`. Same rule, same prohibition. */
@Composable
@ReadOnlyComposable
fun Long.proseGroupedDigits(): String = proseGroupedDigits(inEnglish())
