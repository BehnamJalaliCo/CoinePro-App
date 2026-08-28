package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A button that carries somebody else's mark.
 *
 * ### Why this is its own component
 *
 * [CoineProPrimaryButton] and [CoineProSecondaryButton] take a label and nothing else, and that is
 * correct for the forty buttons in this app that act on the app's own behalf. Three do not: two
 * open an exchange's registration page and one asks Google who the reader is. A button that names
 * another company should show that company's mark — it is what a reader recognises before they have
 * read anything, and for Google it is what their sign-in guidelines ask for.
 *
 * Widening the ordinary buttons to take an optional icon would have put an icon slot on all forty.
 *
 * ### The mark is drawn with `Image`, never `Icon`
 *
 * `Icon` applies a tint. These marks are somebody's identity and their colour is part of it —
 * LBank's blue, Ourbit's green, Google's four — and a tinted one is a mark that company does not
 * have. That is the single rule this component exists to enforce.
 *
 * ### It sits on the reading edge
 *
 * First in the row, which puts it on the right in Persian and on the left in English. A mark at the
 * far end of a full-width button reads as an ornament; a mark before the words reads as the subject
 * of them.
 */
@Composable
fun CoineProBrandButton(
    @DrawableRes logo: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gold, for the one of the two the platform can actually serve end to end. */
    primary: Boolean = false,
    logoSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val background = if (primary) CoineProColors.Accent else CoineProColors.SurfaceElevated
    val ink = if (primary) CoineProColors.OnAccent else CoineProColors.TextPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CoineProPillShape)
            .background(background)
            .then(
                if (primary) Modifier else Modifier.border(1.dp, CoineProColors.Border, CoineProPillShape),
            )
            .pressScale(interaction, CoineProPress.CTA)
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = CoineProSpacing.Two),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(logo),
            // Decorative: the label beside it names the company in words.
            contentDescription = null,
            modifier = Modifier.size(logoSize),
        )
        Spacer(Modifier.width(CoineProSpacing.OneHalf))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
