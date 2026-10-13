package com.coinepro.app.owner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the owner's console on pro-chart.com has to say to this install (5.30.0).
 *
 * The console (`web/admin`) answers the app's check-in with the campaigns aimed at it — an
 * announcement, an offer with a discount code, an update nudge, a maintenance notice — and two
 * banners the owner can switch on without a release. Most campaigns arrive as a notification; the
 * ones that cannot (the reader keeps offers switched off, or notifications are off altogether) and
 * the ones the owner marked «in the app only» wait here as a card at the top of the main screens.
 *
 * This file is shared with the browser, which never checks in: there the board stays empty and
 * nothing is drawn. The phone's side — the request, the notifications — is `OwnerChannel`.
 */
data class OwnerMessage(
    val id: Long,
    val kind: String,
    val high: Boolean,
    val title: String,
    val body: String,
    val cta: String,
    val link: String,
    val coupon: String,
    val notify: Boolean,
    val endsAtMillis: Long?,
)

/** The banners and the cards on screen now. A null banner is not shown; a blank one uses the app's own sentence. */
data class OwnerBoard(
    val maintenance: String? = null,
    val updateNudge: String? = null,
    val cards: List<OwnerMessage> = emptyList(),
)

/** What a tap on the board does; the phone's `OwnerChannel` is the one implementation. */
interface OwnerActions {
    fun open(message: OwnerMessage)
    fun dismiss(message: OwnerMessage)
    fun shown(message: OwnerMessage)
}

object OwnerInbox {
    private val state = MutableStateFlow(OwnerBoard())
    val board: StateFlow<OwnerBoard> = state
    var actions: OwnerActions? = null

    fun publish(board: OwnerBoard) {
        state.value = board
    }

    fun open(message: OwnerMessage) {
        state.value = state.value.copy(cards = state.value.cards.filterNot { it.id == message.id })
        actions?.open(message)
    }

    fun dismiss(message: OwnerMessage) {
        state.value = state.value.copy(cards = state.value.cards.filterNot { it.id == message.id })
        actions?.dismiss(message)
    }

    fun shown(message: OwnerMessage) {
        actions?.shown(message)
    }
}

/** The decisions, apart from Android, so they can be tested. */
object OwnerPlan {
    /** At most this many campaign notifications per check-in; the rest wait for the next. */
    const val MAX_NOTIFICATIONS_PER_RUN = 2

    /**
     * An offer is marketing, which the reader turns on for themselves (off by default); everything
     * else the owner sends is an announcement about the service, which is on.
     */
    fun categoryOf(kind: String): NotificationCategory =
        if (kind == "offer") NotificationCategory.MARKETING else NotificationCategory.ANNOUNCEMENT

    /**
     * Which campaigns go out as a notification now, and which wait as a card.
     *
     * A campaign already notified, opened or dismissed is never shown again in either form. One the
     * reader's settings keep out of the notification shade — offers with marketing off, anything
     * while notifications are off or in quiet hours — becomes a card instead, except that quiet
     * hours only delay: it is notified in the morning, unless the reader has seen it as a card.
     */
    fun plan(
        messages: List<OwnerMessage>,
        settled: Set<Long>,
        nowMillis: Long,
        mayNotify: (OwnerMessage) -> Boolean,
        quietNow: Boolean,
    ): Pair<List<OwnerMessage>, List<OwnerMessage>> {
        val live = messages.filter { it.id !in settled && (it.endsAtMillis == null || it.endsAtMillis > nowMillis) }
        val notify = live.filter { it.notify && mayNotify(it) }.take(MAX_NOTIFICATIONS_PER_RUN)
        val waiting = if (quietNow) live.filter { it.notify && it !in notify } else emptyList()
        val cards = live.filter { it !in notify && it !in waiting }
        return notify to cards
    }

    /** Whether the reader's own settings let this campaign into the notification shade. */
    fun allowed(settings: NotificationSettings, message: OwnerMessage, nowMillis: Long, minuteOfDay: Int, nightQuiet: Boolean): Boolean {
        if (nightQuiet && !message.high) return false
        return settings.shouldShow(categoryOf(message.kind), nowMillis, minuteOfDay)
    }

    /** The update banner: on, and this install older than the oldest version the owner still recommends. */
    fun nudge(on: Boolean, message: String, minVersionCode: Long, versionCode: Long): String? =
        if (on && minVersionCode > 0 && versionCode < minVersionCode) message else null
}

/**
 * The maintenance banner, the update banner and the first waiting card, above the main screens.
 * Nothing at all when the board is empty, which it always is in the browser.
 */
@Composable
fun OwnerBanners(
    board: OwnerBoard,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = board.maintenance != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            BannerRow(
                icon = CoineProIcons.Warning,
                ink = CoineProColors.Warning,
                text = board.maintenance?.takeIf(String::isNotBlank) ?: stringResource(R.string.owner_maintenance_default),
            )
        }
        AnimatedVisibility(
            visible = board.updateNudge != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            BannerRow(
                icon = CoineProIcons.Info,
                ink = CoineProColors.Gold,
                text = board.updateNudge?.takeIf(String::isNotBlank) ?: stringResource(R.string.owner_update_default),
                action = stringResource(R.string.owner_update_action),
                onAction = onUpdate,
            )
        }
        val card = board.cards.firstOrNull()
        AnimatedVisibility(
            visible = card != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (card != null) OwnerCard(card)
        }
    }
}

@Composable
private fun BannerRow(
    icon: Int,
    ink: androidx.compose.ui.graphics.Color,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProTint.fill(ink, CoineProColors.Stage))
            .padding(horizontal = CoineProSpacing.Gutter, vertical = BAR_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(GLYPH))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(text = action, style = MaterialTheme.typography.labelMedium, color = ink)
            }
        }
    }
}

@Composable
private fun OwnerCard(message: OwnerMessage) {
    LaunchedEffect(message.id) { OwnerInbox.shown(message) }
    val ink = if (message.kind == "offer") CoineProColors.Gold else CoineProColors.Accent
    val clipboard = LocalClipboardManager.current
    val opens = message.link.isNotBlank() || message.cta.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CARD_GAP)
            .background(CoineProColors.SurfaceElevated, CARD_SHAPE)
            .border(1.dp, CoineProTint.fill(ink, CoineProColors.Border), CARD_SHAPE)
            .then(if (opens) Modifier.clickable { OwnerInbox.open(message) } else Modifier)
            .padding(start = CoineProSpacing.Gutter, end = CARD_GAP, top = CARD_GAP, bottom = CARD_GAP),
        verticalArrangement = Arrangement.spacedBy(CARD_LINE),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(CoineProIcons.Bell), contentDescription = null, tint = ink, modifier = Modifier.size(GLYPH))
            Text(
                text = message.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = CoineProColors.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CoineProSpacing.Half),
            )
            IconButton(onClick = { OwnerInbox.dismiss(message) }, modifier = Modifier.size(CLOSE)) {
                Icon(
                    painter = painterResource(CoineProIcons.Close),
                    contentDescription = stringResource(R.string.owner_banner_close),
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier.size(GLYPH),
                )
            }
        }
        if (message.body.isNotBlank()) {
            Text(text = message.body, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
        }
        if (message.coupon.isNotBlank() || opens) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                if (message.coupon.isNotBlank()) {
                    // A code is a figure: Latin, and copied with one tap.
                    Text(
                        text = stringResource(R.string.owner_coupon_label, message.coupon),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        modifier = Modifier
                            .background(CoineProTint.fill(ink, CoineProColors.Stage), CHIP_SHAPE)
                            .clickable { clipboard.setText(AnnotatedString(message.coupon)) }
                            .padding(horizontal = CARD_GAP, vertical = CARD_LINE),
                    )
                }
                if (opens) {
                    TextButton(onClick = { OwnerInbox.open(message) }) {
                        Text(
                            text = message.cta.ifBlank { stringResource(R.string.owner_card_open) },
                            style = MaterialTheme.typography.labelMedium,
                            color = ink,
                        )
                    }
                }
            }
        }
    }
}

private val BAR_VERTICAL = 4.dp
private val GLYPH = 16.dp
private val CLOSE = 32.dp
private val CARD_GAP = 12.dp
private val CARD_LINE = 4.dp
private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val CHIP_SHAPE = RoundedCornerShape(8.dp)
