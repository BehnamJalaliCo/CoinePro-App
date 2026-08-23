package com.coinepro.core.aiassistant

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiAssistantController(
    private val gateway: AiAssistantGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    fun send(rawMessage: String) {
        val message = rawMessage.trim()
        if (message.isBlank()) {
            _state.update { it.copy(error = "Write a message before sending.") }
            return
        }
        if (message.length > ASSISTANT_MAX_MESSAGE_CHARS) {
            _state.update { it.copy(error = "Message is longer than $ASSISTANT_MAX_MESSAGE_CHARS characters.") }
            return
        }
        if (_state.value.sending) return

        val conversationId = _state.value.conversation?.id
        val localUserMessage = AssistantMessage(
            id = "local-${UUID.randomUUID()}",
            role = AssistantRole.USER,
            text = message,
            createdAt = null,
        )
        _state.update {
            it.copy(
                sending = true,
                messages = it.messages + localUserMessage,
                error = null,
            )
        }

        scope.launch {
            try {
                val turn = gateway.send(conversationId, message)
                _state.update {
                    it.copy(
                        sending = false,
                        conversation = turn.conversation,
                        messages = it.messages + turn.reply,
                        error = null,
                    )
                }
            } catch (_: AiAssistantEntitlementRequiredException) {
                _state.update { it.copy(sending = false, error = "AI Assistant requires an active server entitlement.") }
            } catch (_: AiAssistantRateLimitedException) {
                _state.update { it.copy(sending = false, error = "AI Assistant is temporarily rate limited. Try again later.") }
            } catch (error: AiAssistantRequestRejectedException) {
                _state.update { it.copy(sending = false, error = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = error.message ?: "Could not send the assistant message.") }
            }
        }
    }

    fun newConversation() {
        if (_state.value.sending) return
        _state.value = AssistantState()
    }

    fun clear() {
        _state.value = AssistantState()
    }
}
