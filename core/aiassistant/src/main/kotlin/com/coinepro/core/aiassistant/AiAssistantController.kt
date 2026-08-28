package com.coinepro.core.aiassistant

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.network.serverTextOrNull
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
            _state.update { it.copy(error = UiMessage.of(MessageKey.AI_MESSAGE_EMPTY)) }
            return
        }
        if (message.length > ASSISTANT_MAX_MESSAGE_CHARS) {
            _state.update { it.copy(error = UiMessage.of(MessageKey.AI_MESSAGE_TOO_LONG)) }
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
                if (conversationId != null && turn.conversation.id != conversationId) {
                    // Set directly rather than thrown. `AiAssistantRequestRejectedException`
                    // carries the *server's* wording, and the catch below passes it through as
                    // server copy — so throwing this would have relabelled the app's own sentence
                    // as something the server said.
                    _state.update {
                        it.copy(
                            sending = false,
                            error = UiMessage.of(MessageKey.AI_CONVERSATION_CHANGED),
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        sending = false,
                        conversation = turn.conversation,
                        messages = it.messages + turn.reply,
                        error = null,
                    )
                }
            } catch (_: AiAssistantEntitlementRequiredException) {
                _state.update { it.copy(sending = false, error = UiMessage.of(MessageKey.AI_ENTITLEMENT_REQUIRED)) }
            } catch (_: AiAssistantRateLimitedException) {
                _state.update { it.copy(sending = false, error = UiMessage.of(MessageKey.AI_GENERATION_FAILED)) }
            } catch (error: AiAssistantRequestRejectedException) {
                _state.update { it.copy(sending = false, error = UiMessage.fromServer(error.message, MessageKey.AI_GENERATION_FAILED)) }
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = UiMessage.fromServer(error.serverTextOrNull(), MessageKey.AI_GENERATION_FAILED)) }
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
