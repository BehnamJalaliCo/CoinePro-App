package com.coinepro.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Which explanatory subtitles are drawn as text, and which fold into an ⓘ.
 *
 * ### The problem this closes
 *
 * The app has some two hundred and fifty strings named `*_note`, `*_hint` and `*_body`: a line of
 * small grey text under an option, a switch, a section. Each one was written because somebody
 * once needed it. Read together, on a tablet with three panels open, they are a wall — the
 * reader stops reading grey text altogether, and the one line that would have stopped a real
 * mistake goes unread with the rest.
 *
 * So there is a budget. A note stays **inline** only where not reading it costs real money, loses
 * data, weakens security or grants a permission — the confirm dialog before a deletion, the
 * copy-trading switch, the alarm-channel sound, the MetaTrader credentials. Everything else is
 * still one tap away behind ⓘ, with its full text, and no longer competes with the option it
 * explains. The budget is sixty; the policy lists forty-odd.
 *
 * ### How it is enforced
 *
 * [visible] is the whole allowlist, by resource name. `tools/i18n/notes.tsv` mirrors it with a
 * class for every note key in the repository, and `tools/i18n/lint_strings.py` (part of the
 * consistency gate) fails the build when a `_note` key exists that the registry does not know,
 * when the two lists disagree, when the allowlist passes sixty, or when a Kotlin source resolves a
 * demoted key to a string itself instead of handing the id to [CoineProNote]. A new note is
 * therefore a registry entry first and a string second, and «visible» is a decision somebody has
 * to write down.
 *
 * Keys are compared by name so that promoting or demoting a note is a one-line change here, with
 * no call site touched: every site passes the id and lets the policy decide.
 */
object NotePolicy {

    /** The budget. The lint fails above it. */
    const val BUDGET = 60

    /** Notes drawn inline. Each prevents a real mistake: money, deletion, security, permissions. */
    val visible: Set<String> = setOf(
        // Confirmations before something is lost.
        "logout_confirm_body",
        "alerts_delete_body",
        "connections_disconnect_body",
        "delete_account_kept_note",
        "delete_account_local_note",
        "delete_account_web_body",
        // Real money: copy trading and the account it trades on.
        "copy_switch_note",
        "copy_mismatch_body",
        "copy_account_none_body",
        "connections_lbank_body",
        "connections_mt5_body",
        "connections_mt5_pending_note",
        "detail_copy_note",
        "detail_stale_note",
        "detail_high_impact_body",
        "membership_copytrade_note",
        "membership_lbank_note",
        "membership_ourbit_note",
        "membership_referral_note",
        "membership_access_fund_note",
        "guest_record_note",
        "membership_record_note",
        "paper_settings_note",
        "setup_paper_trade_note",
        // Security and privacy.
        "safety_how_body",
        "safety_camera_body",
        "safety_risk_body",
        "safety_provider_body",
        "safety_privacy_body",
        "safety_support_body",
        "tampered_body",
        "lock_screen_body",
        "lock_sheet_note",
        "webhooks_secret_hint",
        "webhooks_url_hint",
        "auth_password_hint",
        // Alerts that ring, and the switch that silences all of them.
        "alerts_venue_server_note",
        "alerts_venue_device_note",
        "alerts_sound_loud_note",
        "notifications_master_note",
        // Permissions.
        "notifications_permission_body",
        "vision_permission_note",
    )

    fun isVisible(resourceName: String): Boolean = resourceName in visible
}

/** Whether the policy draws [id] inline. Resolved once per id; the name lookup is not free. */
@Composable
fun noteVisible(@StringRes id: Int): Boolean {
    val context = LocalContext.current
    return remember(id) {
        val name = runCatching { context.resources.getResourceEntryName(id) }.getOrDefault("")
        NotePolicy.isVisible(name)
    }
}

/**
 * A note under an option: the text itself where [NotePolicy] allows, an ⓘ with the same text in a
 * tooltip everywhere else. Call sites pass the resource id, never the string, so that the policy
 * — not the screen — decides.
 */
@Composable
fun CoineProNote(
    @StringRes id: Int,
    vararg formatArgs: Any,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = CoineProColors.TextMuted,
) {
    val text = stringResource(id, *formatArgs)
    if (noteVisible(id)) {
        Text(text = text, style = style, color = color, modifier = modifier)
    } else {
        CoineProInfoTip(text = text, modifier = modifier, tint = color)
    }
}

/**
 * The ⓘ. A 24 dp target with a 16 dp glyph, which is the smallest thing on any row and meant to
 * be: it is the answer to a question the reader may not have. Tapping it opens a plain tooltip
 * with the full note; the tooltip is persistent (it waits for a tap outside) because a note the
 * reader chose to open should not vanish while they read it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoineProInfoTip(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = CoineProColors.TextMuted,
) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = state,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(INFO_TIP_TARGET)
                .clip(CircleShape)
                .clickable { scope.launch { state.show() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.icon_info),
                contentDescription = text,
                modifier = Modifier.size(INFO_TIP_GLYPH),
                tint = tint,
            )
        }
    }
}

private val INFO_TIP_TARGET = 24.dp
private val INFO_TIP_GLYPH = 16.dp
