package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One row of a dropdown or context menu (DIALOGS-21).
 *
 * Material's item is a 48 dp row in `labelLarge` — Medium, which in IRANYekanX reads bold — with no
 * room for the shortcut a desktop reader looks for. TradingView's are 32–40 px, 14 px regular, a 20 px
 * glyph in front and the key combination faint at the far end. On a pointer window this is 36 dp;
 * a phone keeps Material's 48, because a thumb is not a pointer.
 *
 * The menu's own plate and hover come from the theme (`surfaceContainer` and the ripple's hover
 * alpha), so a `DropdownMenu` of these needs no colours passed.
 */
@Composable
fun CoineProMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A 20 dp glyph before the label, tinted with it. */
    @DrawableRes icon: Int? = null,
    /** A key combination at the row's far end — «Alt+R» — in the muted ink. */
    shortcut: String? = null,
    /** A second, fainter line: what a template holds, what a choice will do. */
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val pointer = coineProWindowClass().showsTwoPanes
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = if (pointer && supporting == null) {
            modifier.height(MENU_ROW_POINTER)
        } else {
            modifier.heightIn(min = if (pointer) MENU_ROW_POINTER else MENU_ROW_TOUCH)
        },
        leadingIcon = icon?.let { res ->
            {
                Icon(
                    painter = painterResource(res),
                    contentDescription = null,
                    modifier = Modifier.size(MENU_GLYPH),
                )
            }
        },
        trailingIcon = shortcut?.let { keys ->
            {
                Text(
                    text = keys,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = CoineProColors.TextMuted,
                )
            }
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = CoineProSpacing.OneHalf),
    )
}

private val MENU_ROW_POINTER = 36.dp
private val MENU_ROW_TOUCH = 48.dp
private val MENU_GLYPH = 20.dp
