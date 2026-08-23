package com.coinepro.feature.aiassistant

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("AI Assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = controller::newConversation, enabled = !state.sending) {
                    Text("New chat")
                }
            }
            Text(
                "Context-aware chat with server-verified market context. Assistant prose never creates positions, signals, or execution state.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HistoryPolicyCard(
            policy = state.conversation?.historyPolicy ?: AssistantHistoryPolicy.UNKNOWN,
            retentionDays = state.conversation?.retentionDays,
        )

        if (state.messages.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Verified context only", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ask about active signals, current market context, news/calendar, risk, or tools. Context cards below assistant replies show exactly what structured sources the server supplied.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.messages.forEach { message ->
            MessageCard(message = message, onOpenSignal = onOpenSignal)
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                if (value.length <= ASSISTANT_MAX_MESSAGE_CHARS) draft = value
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask CoinePro AI") },
            supportingText = { Text("${draft.length}/$ASSISTANT_MAX_MESSAGE_CHARS") },
            minLines = 2,
            maxLines = 6,
            enabled = !state.sending,
        )
        Button(
            onClick = {
                val clean = draft.trim()
                if (clean.isNotEmpty()) {
                    controller.send(clean)
                    draft = ""
                }
            },
            enabled = !state.sending && draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.sending) {
                CircularProgressIndicator()
            } else {
                Text("Send")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HistoryPolicyCard(
    policy: AssistantHistoryPolicy,
    retentionDays: Int?,
) {
    val text = when (policy) {
        AssistantHistoryPolicy.EPHEMERAL -> "This chat is server-declared ephemeral. Android does not persist the transcript locally."
        AssistantHistoryPolicy.ACCOUNT -> buildString {
            append("Server account history is enabled. Android still does not persist the transcript locally.")
            retentionDays?.let { append(" Server retention: $it days.") }
        }
        AssistantHistoryPolicy.UNKNOWN -> "Android does not persist assistant history locally. Server retention policy has not been reported yet."
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Conversation history", fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MessageCard(
    message: AssistantMessage,
    onOpenSignal: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (message.role == AssistantRole.USER) "You" else "CoinePro AI",
                fontWeight = FontWeight.SemiBold,
            )
            Text(message.text)
            if (message.role == AssistantRole.ASSISTANT) {
                if (message.context.isEmpty()) {
                    Text(
                        "No structured market context was attached to this reply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Verified context used", style = MaterialTheme.typography.labelLarge)
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
    val freshness = when (item.freshness) {
        AssistantFreshness.FRESH -> "Fresh"
        AssistantFreshness.STALE -> "Stale"
        AssistantFreshness.UNKNOWN -> "Freshness unknown"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${kindLabel(item.kind)} · $freshness", fontWeight = FontWeight.Medium)
            Text(item.title)
            item.summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val provenance = listOfNotNull(item.source?.let { "Source: $it" }, item.asOf?.let { "As of: $it" })
                .joinToString(" · ")
            if (provenance.isNotBlank()) {
                Text(provenance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val signalId = item.signalId
            if (item.kind == AssistantContextKind.ACTIVE_SIGNAL && signalId != null) {
                TextButton(onClick = { onOpenSignal(signalId) }) { Text("Open verified Signal") }
            }
        }
    }
}

private fun kindLabel(kind: AssistantContextKind): String = when (kind) {
    AssistantContextKind.ACTIVE_SIGNAL -> "Active signal"
    AssistantContextKind.MARKET -> "Market"
    AssistantContextKind.NEWS -> "News"
    AssistantContextKind.CALENDAR -> "Calendar"
    AssistantContextKind.RISK -> "Risk"
    AssistantContextKind.TOOL -> "Tool"
}
