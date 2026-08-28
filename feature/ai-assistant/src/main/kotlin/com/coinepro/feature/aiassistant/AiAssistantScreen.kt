package com.coinepro.feature.aiassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProAgentOrb
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.aiassistant.ASSISTANT_MAX_MESSAGE_CHARS
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aiassistant.AssistantContextItem
import com.coinepro.core.aiassistant.AssistantContextKind
import com.coinepro.core.aiassistant.AssistantFreshness
import com.coinepro.core.aiassistant.AssistantHistoryPolicy
import com.coinepro.core.aiassistant.AssistantMessage
import com.coinepro.core.aiassistant.AssistantRole

@Composable
fun AiAssistantScreen(
    controller: AiAssistantController,
    onOpenSignal: (Long) -> Unit,
    /**
     * Whether this deployment has an assistant at all.
     *
     * Checked here rather than only at the entry point that opens this screen. Switching platform
     * does not pop the back stack, so a reader who opened this on the platform that has one and
     * then switched would otherwise keep typing into a thread the platform on screen cannot serve —
     * and the answers would come from the other backend without either of them saying so.
     */
    available: Boolean = true,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    if (!available) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .padding(CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(
                text = stringResource(R.string.assistant_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.assistant_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoineProAgentOrb(size = 22.dp)
                Text(
                    text = stringResource(R.string.assistant_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                )
            }
            CoineProSecondaryButton(
                text = stringResource(R.string.assistant_new_chat),
                onClick = { if (!state.sending) controller.newConversation() },
                modifier = Modifier.alpha(if (state.sending) 0.45f else 1f),
            )
        }
        Text(
            text = stringResource(R.string.assistant_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )

        HistoryPolicyCard(
            policy = state.conversation?.historyPolicy ?: AssistantHistoryPolicy.UNKNOWN,
            retentionDays = state.conversation?.retentionDays,
        )

        if (state.messages.isEmpty()) {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.assistant_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                Text(
                    text = stringResource(R.string.assistant_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }

        state.messages.forEach { message ->
            MessageCard(message = message, onOpenSignal = onOpenSignal)
        }

        if (state.sending) {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoineProThinkingDots()
                    Text(
                        text = stringResource(R.string.assistant_thinking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }

        // The server's own wording where it sent one, this app's where it did not. See
        // `UiMessage`: the controller used to write English sentences into this field.
        state.error?.let {
            Text(
                text = it.resolve(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CoineProColors.Sell.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
                    .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Sell,
            )
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                if (value.length <= ASSISTANT_MAX_MESSAGE_CHARS) draft = value
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.assistant_input_label)) },
            supportingText = {
                Text(
                    BidiText.isolateLtr("${draft.length}/$ASSISTANT_MAX_MESSAGE_CHARS"),
                    color = CoineProColors.TextMuted,
                )
            },
            shape = MaterialTheme.shapes.medium,
            minLines = 2,
            maxLines = 6,
            enabled = !state.sending,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CoineProColors.TextPrimary,
                unfocusedTextColor = CoineProColors.TextPrimary,
                focusedBorderColor = CoineProColors.Gold,
                unfocusedBorderColor = CoineProColors.Border,
                focusedLabelColor = CoineProColors.Accent,
                unfocusedLabelColor = CoineProColors.TextMuted,
                cursorColor = CoineProColors.Gold,
                focusedContainerColor = CoineProColors.Surface,
                unfocusedContainerColor = CoineProColors.Surface,
            ),
        )
        val canSend = !state.sending && draft.isNotBlank()
        CoineProPrimaryButton(
            text = stringResource(R.string.assistant_send),
            onClick = {
                if (!canSend) return@CoineProPrimaryButton
                val clean = draft.trim()
                if (clean.isNotEmpty()) {
                    controller.send(clean)
                    draft = ""
                }
            },
            modifier = Modifier.fillMaxWidth().alpha(if (canSend) 1f else 0.45f),
        )

        Spacer(Modifier.height(CoineProSpacing.Three))
    }
}

@Composable
private fun HistoryPolicyCard(
    policy: AssistantHistoryPolicy,
    retentionDays: Int?,
) {
    val text = when (policy) {
        AssistantHistoryPolicy.EPHEMERAL -> stringResource(R.string.assistant_history_ephemeral)
        AssistantHistoryPolicy.ACCOUNT -> if (retentionDays != null) {
            stringResource(R.string.assistant_history_account_days, BidiText.isolateLtr("$retentionDays"))
        } else {
            stringResource(R.string.assistant_history_account)
        }
        AssistantHistoryPolicy.UNKNOWN -> stringResource(R.string.assistant_history_unknown)
    }
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CoineProSpacing.Two),
    ) {
        Text(
            text = stringResource(R.string.assistant_history_title),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
    }
}

@Composable
private fun MessageCard(
    message: AssistantMessage,
    onOpenSignal: (Long) -> Unit,
) {
    val fromReader = message.role == AssistantRole.USER
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        // The reader's own turns sit a step above the assistant's, so a long exchange reads as an
        // exchange rather than as one column of identical blocks.
        elevated = fromReader,
    ) {
        Text(
            text = stringResource(
                if (fromReader) R.string.assistant_role_you else R.string.assistant_role_assistant,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (fromReader) CoineProColors.TextSecondary else CoineProColors.Accent,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = CoineProColors.TextPrimary,
        )
        if (message.role == AssistantRole.ASSISTANT) {
            Spacer(Modifier.height(CoineProSpacing.One))
            if (message.context.isEmpty()) {
                // Said plainly rather than left blank: a reply with no sources behind it is a
                // different kind of answer, and the reader has to be able to tell.
                Text(
                    text = stringResource(R.string.assistant_no_context),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.Warning,
                )
            } else {
                Text(
                    text = stringResource(R.string.assistant_context_used),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    message.context.forEach { item -> ContextCard(item, onOpenSignal) }
                }
            }
        }
    }
}

@Composable
private fun ContextCard(
    item: AssistantContextItem,
    onOpenSignal: (Long) -> Unit,
) {
    // Staleness is the whole point of these cards: an answer built on a stale quote is a different
    // answer, so freshness is coloured rather than left as one more grey line.
    val freshnessColour = when (item.freshness) {
        AssistantFreshness.FRESH -> CoineProColors.Buy
        AssistantFreshness.STALE -> CoineProColors.Warning
        AssistantFreshness.UNKNOWN -> CoineProColors.TextMuted
    }
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(CoineProSpacing.Two),
        elevated = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(item.kind.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
            )
            Text(
                text = stringResource(item.freshness.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = freshnessColour,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextPrimary)
        item.summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
        }
        val provenance = listOfNotNull(item.source, item.asOf).joinToString(" · ")
        if (provenance.isNotBlank()) {
            Text(
                text = provenance,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        val signalId = item.signalId
        if (item.kind == AssistantContextKind.ACTIVE_SIGNAL && signalId != null) {
            Spacer(Modifier.height(CoineProSpacing.One))
            CoineProSecondaryButton(
                text = stringResource(R.string.assistant_open_signal),
                onClick = { onOpenSignal(signalId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@androidx.annotation.StringRes
private fun AssistantContextKind.labelRes(): Int = when (this) {
    AssistantContextKind.ACTIVE_SIGNAL -> R.string.assistant_kind_signal
    AssistantContextKind.MARKET -> R.string.assistant_kind_market
    AssistantContextKind.NEWS -> R.string.assistant_kind_news
    AssistantContextKind.CALENDAR -> R.string.assistant_kind_calendar
    AssistantContextKind.RISK -> R.string.assistant_kind_risk
    AssistantContextKind.TOOL -> R.string.assistant_kind_tool
}

@androidx.annotation.StringRes
private fun AssistantFreshness.labelRes(): Int = when (this) {
    AssistantFreshness.FRESH -> R.string.assistant_fresh
    AssistantFreshness.STALE -> R.string.assistant_stale
    AssistantFreshness.UNKNOWN -> R.string.assistant_freshness_unknown
}
