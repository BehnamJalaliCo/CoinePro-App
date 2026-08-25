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
    @POST("user/ai/chat")
    suspend fun send(@Body request: AssistantMessageRequestDto): AssistantMessageResponseDto
}

/** The server takes one field. It has no notion of a conversation and no context scopes to widen. */
internal data class AssistantMessageRequestDto(val message: String)

/**
 * What the assistant actually returns: an answer and a quota, nothing else.
 *
 * No conversation, no message id, no context items, and no history kept anywhere on the server —
 * every request is judged on its own. The richer shape the app was written against does not exist,
 * so the thread on screen is the app's alone, and the gateway says so rather than inventing
 * provenance the server never claimed.
 */
internal data class AssistantMessageResponseDto(
    val answer: String? = null,
    val used: Int? = null,
    val quota: Int? = null,
    val remaining: Int? = null,
)

class NetworkAiAssistantGateway private constructor(
    private val api: AiAssistantApi,
) : AiAssistantGateway {
    override suspend fun send(conversationId: String?, message: String): AssistantTurn = translate {
        val cleanMessage = message.trim()
        require(cleanMessage.isNotBlank()) { "Assistant message cannot be empty" }
        require(cleanMessage.length <= ASSISTANT_MAX_MESSAGE_CHARS) { "Assistant message is too long" }
        val response = api.send(AssistantMessageRequestDto(cleanMessage))
        val answer = response.answer?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Invalid assistant reply response")
        AssistantTurn(
            conversation = AssistantConversationMeta(
                // The thread is the app's own, so it keeps whatever id it already had. The server
                // has no opinion and will not remember this exchange either way.
                id = conversationId?.takeIf { it.isNotBlank() } ?: LOCAL_CONVERSATION,
                historyPolicy = AssistantHistoryPolicy.EPHEMERAL,
                retentionDays = null,
            ),
            reply = AssistantMessage(
                // Derived rather than reported: the server sends no id, and a stable one per reply
                // is what the list needs to avoid redrawing the whole thread on every turn.
                id = "reply-" + answer.hashCode().toUInt().toString(16),
                role = AssistantRole.ASSISTANT,
                text = answer,
                createdAt = null,
                // Deliberately empty. The server cites nothing, and a citation the app assembled
                // itself would be the app vouching for the model in the service's voice.
                context = emptyList(),
            ),
        )
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
        private const val LOCAL_CONVERSATION = "local"

        fun create(retrofit: Retrofit): NetworkAiAssistantGateway =
            NetworkAiAssistantGateway(retrofit.create(AiAssistantApi::class.java))
    }
}
