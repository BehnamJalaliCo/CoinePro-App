package com.coinepro.core.aiassistant

import com.coinepro.core.common.UiMessage

enum class AssistantRole(val wireValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

enum class AssistantContextKind(val wireValue: String) {
    ACTIVE_SIGNAL("active_signal"),
    MARKET("market"),
    NEWS("news"),
    CALENDAR("calendar"),
    RISK("risk"),
    TOOL("tool"),
}

enum class AssistantFreshness(val wireValue: String) {
    FRESH("fresh"),
    STALE("stale"),
    UNKNOWN("unknown"),
}

enum class AssistantHistoryPolicy(val wireValue: String) {
    EPHEMERAL("ephemeral"),
    ACCOUNT("account"),
    UNKNOWN("unknown"),
}

data class AssistantContextItem(
    val kind: AssistantContextKind,
    val title: String,
    val summary: String?,
    val source: String?,
    val asOf: String?,
    val freshness: AssistantFreshness,
    val signalId: Long? = null,
)

data class AssistantMessage(
    val id: String,
    val role: AssistantRole,
    val text: String,
    val createdAt: String?,
    val context: List<AssistantContextItem> = emptyList(),
)

data class AssistantConversationMeta(
    val id: String,
    val historyPolicy: AssistantHistoryPolicy,
    val retentionDays: Int?,
)

data class AssistantState(
    val sending: Boolean = false,
    val conversation: AssistantConversationMeta? = null,
    val messages: List<AssistantMessage> = emptyList(),
    /**
     * Owned copy, in the reader's language.
     *
     * This was a `String?` that the controller wrote authored **English** sentences into — "Write a
     * message before sending.", "The prepared image is too large for AI Vision." — and the screen
     * rendered verbatim, to an audience whose default language is Persian. Not exception text
     * leaking through: sentences somebody wrote, for the reader, in the wrong language.
     */
    val error: UiMessage? = null,
) {
    val canSend: Boolean
        get() = !sending
}

const val ASSISTANT_MAX_MESSAGE_CHARS: Int = 4_000
