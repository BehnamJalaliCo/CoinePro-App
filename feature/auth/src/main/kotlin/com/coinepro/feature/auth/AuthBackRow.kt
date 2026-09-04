package com.coinepro.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The way out of a sign-in screen, for a reader who arrived at one from inside the app.
 *
 * ### The report
 *
 * «روی هر کدوم از قسمت‌هایی که ورود با حساب می‌زنم وارد قسمت ثبت‌نام یا ورود با گوگل می‌شه ولی هیچ
 * دکمهٔ برگشتی نذاشتی.» And it was exact: signing in is not a destination on the navigation graph —
 * it *replaces* the shell — so there was no back stack behind it, no top bar over it and no arrow in
 * the corner. A reader who tapped a locked row in the menu to see what was behind it was left on a
 * form with two ways out: complete it, or kill the app.
 *
 * ### Both gestures, and only one of them is here
 *
 * This is the visible half. The system's own back is the caller's, wired to the same lambda where
 * the state it changes lives — a screen that can be left by a button but not by the back gesture is
 * one that most readers on the device cannot leave the way they leave everything else.
 *
 * The caller also decides where back goes, which is the point of the whole fix: the shell keeps the
 * tab the reader was on standing behind the form, so leaving it puts them back on the menu they
 * came from rather than on the app's front page.
 */
@Composable
internal fun AuthBackRow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.auth_back)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CoineProShapes.small)
                .clickable(onClick = onBack)
                .background(CoineProColors.Surface)
                .heightIn(min = TARGET)
                .padding(horizontal = CoineProSpacing.One),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(CoineProIcons.Back),
                // The word beside it names the action; a second announcement would read it twice.
                contentDescription = null,
                tint = CoineProColors.TextPrimary,
                modifier = Modifier.size(GLYPH),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
            )
        }
    }
}

/** Material's minimum target, which is the floor for the only control that leaves this screen. */
private val TARGET = 40.dp

private val GLYPH = 18.dp
