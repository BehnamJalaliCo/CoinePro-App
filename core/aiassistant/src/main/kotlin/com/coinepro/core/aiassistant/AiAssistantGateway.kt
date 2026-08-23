package com.coinepro.core.aiassistant

import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

interface AiAssistantGateway {
    suspend fun send(conversationId: String?, message: String): AssistantTurn
}

data class AssistantTurn(
    val conversation: AssistantConversationMeta,
    val reply: AssistantMessage,
)

class AiAssistantEntitlementRequiredException : Exception("AI Assistant entitlement required")
class AiAssistantRateLimitedException : Exception("AI Assistant rate limited")
class AiAssistantRequestRejectedException(message: String) : Exception(message)

internal interface AiAssistantApi {
    @POST("user/ai/assistant/messages")
    suspend fun send(@Body request: AssistantMessageRequestDto): AssistantMessageResponseDto
}

internal data class AssistantMessageRequestDto(
    val conversationId: String?,
    val message: String,
    val contextScopes: List<String>,
)

internal data class AssistantConversationDto(
    val id: String? = null,
    val historyPolicy: String? = null,
    val retentionDays: Int? = null,
)

internal data class AssistantContextDto(
    val kind: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val source: String? = null,
    val asOf: String? = null,
    val freshness: String? = null,
    val signalId: Long? = null,
)

internal data class AssistantMessageDto(
    val id: String? = null,
    val role: String? = null,
    val text: String? = null,
    val createdAt: String? = null,
    val context: List<AssistantContextDto> = emptyList(),
)

internal data class AssistantMessageResponseDto(
    val conversation: AssistantConversationDto? = null,
    val reply: AssistantMessageDto? = null,
)

class NetworkAiAssistantGateway private constructor(
    private val api: AiAssistantApi,
) : AiAssistantGateway {
    override suspend fun send(conversationId: String?, message: String): AssistantTurn = translate {
        val cleanMessage = message.trim()
        require(cleanMessage.isNotBlank()) { "Assistant message cannot be empty" }
        require(cleanMessage.length <= ASSISTANT_MAX_MESSAGE_CHARS) { "Assistant message is too long" }
        val response = api.send(
            AssistantMessageRequestDto(
                conversationId = conversationId?.takeIf { it.isNotBlank() },
                message = cleanMessage,
                contextScopes = AssistantContextScopes.requested,
            ),
        )
        val conversation = response.conversation?.toDomain()
            ?: throw IllegalArgumentException("Invalid assistant conversation response")
        val reply = response.reply?.toDomain()
            ?: throw IllegalArgumentException("Invalid assistant reply response")
        AssistantTurn(conversation, reply)
    }

    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        when (error.code()) {
            403 -> throw AiAssistantEntitlementRequiredException()
            422 -> throw AiAssistantRequestRejectedException("Assistant request was rejected by server validation")
            429 -> throw AiAssistantRateLimitedException()
            else -> throw error
        }
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkAiAssistantGateway =
            NetworkAiAssistantGateway(retrofit.create(AiAssistantApi::class.java))
    }
}

internal fun AssistantConversationDto.toDomain(): AssistantConversationMeta? {
    val safeId = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val policy = AssistantHistoryPolicy.entries.firstOrNull {
        it.wireValue == historyPolicy?.trim()?.lowercase()
    } ?: AssistantHistoryPolicy.UNKNOWN
    val safeRetention = retentionDays?.takeIf { it > 0 }
    return AssistantConversationMeta(
        id = safeId,
        historyPolicy = policy,
        retentionDays = safeRetention,
    )
}

internal fun AssistantMessageDto.toDomain(): AssistantMessage? {
    val safeId = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (role?.trim()?.lowercase() != AssistantRole.ASSISTANT.wireValue) return null
    val safeText = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val safeContext = context.mapNotNull(AssistantContextDto::toDomain)
    return AssistantMessage(
        id = safeId,
        role = AssistantRole.ASSISTANT,
        text = safeText,
        createdAt = createdAt,
        context = safeContext,
    )
}

internal fun AssistantContextDto.toDomain(): AssistantContextItem? {
    val safeKind = AssistantContextKind.entries.firstOrNull {
        it.wireValue == kind?.trim()?.lowercase()
    } ?: return null
    val safeTitle = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val safeSource = source.clean()
    val safeAsOf = asOf.clean()
    val reportedFreshness = AssistantFreshness.entries.firstOrNull {
        it.wireValue == freshness?.trim()?.lowercase()
    } ?: AssistantFreshness.UNKNOWN
    val safeFreshness = if (
        reportedFreshness == AssistantFreshness.FRESH &&
        (safeSource == null || safeAsOf == null)
    ) {
        AssistantFreshness.UNKNOWN
    } else {
        reportedFreshness
    }
    val safeSignalId = signalId?.takeIf { it > 0L }
    if (safeKind == AssistantContextKind.ACTIVE_SIGNAL && safeSignalId == null) return null
    if (safeKind != AssistantContextKind.ACTIVE_SIGNAL && signalId != null) return null
    return AssistantContextItem(
        kind = safeKind,
        title = safeTitle,
        summary = summary.clean(),
        source = safeSource,
        asOf = safeAsOf,
        freshness = safeFreshness,
        signalId = safeSignalId,
    )
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() }
