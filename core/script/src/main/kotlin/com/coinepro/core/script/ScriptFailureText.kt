package com.coinepro.core.script

import com.coinepro.core.common.AppLanguage

/** [ScriptFailure.text] in the app's own language type, which the language module does not know. */
fun ScriptFailure.text(language: AppLanguage): String = text(english = language == AppLanguage.ENGLISH)
