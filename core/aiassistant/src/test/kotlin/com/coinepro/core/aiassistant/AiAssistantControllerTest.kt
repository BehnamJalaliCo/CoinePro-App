package com.coinepro.core.aiassistant

import com.coinepro.core.common.UiMessage
import com.coinepro.core.common.MessageKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantControllerTest {
    @Test
    fun `successful send keeps server conversation and structured context`() = runTest {
        val gateway = FakeGateway(
            turns = ArrayDeque(
                listOf(
                    AssistantTurn(
                        conversation = AssistantConversationMeta(
                            id = "conversation-1",
                            historyPolicy = AssistantHistoryPolicy.EPHEMERAL,
                            retentionDays = null,
                        ),
                        reply = AssistantMessage(
                            id = "reply-1",
                            role = AssistantRole.ASSISTANT,
                            text = "Gold is trading near the latest verified quote.",
                            createdAt = null,
                            context = listOf(
                                AssistantContextItem(
                                    kind = AssistantContextKind.MARKET,
                                    title = "XAUUSD",
                                    summary = "Latest quote",
                                    source = "marketdata",
                                    asOf = "2026-08-23T00:00:00Z",
                                    freshness = AssistantFreshness.FRESH,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val controller = AiAssistantController(gateway, this)

        controller.send("What is happening with gold?")
        advanceUntilIdle()

        assertFalse(controller.state.value.sending)
        assertEquals("conversation-1", controller.state.value.conversation?.id)
        assertEquals(2, controller.state.value.messages.size)
        assertEquals(AssistantRole.USER, controller.state.value.messages.first().role)
        assertEquals(AssistantRole.ASSISTANT, controller.state.value.messages.last().role)
        assertEquals(AssistantFreshness.FRESH, controller.state.value.messages.last().context.first().freshness)
    }

    @Test
    fun `existing conversation cannot silently switch ids`() = runTest {
        val gateway = FakeGateway(
            turns = ArrayDeque(
                listOf(
                    turn("conversation-1", "reply-1"),
                    turn("conversation-2", "reply-2"),
                ),
            ),
        )
        val controller = AiAssistantController(gateway, this)

        controller.send("first")
        advanceUntilIdle()
        controller.send("second")
        advanceUntilIdle()

        assertEquals("conversation-1", controller.state.value.conversation?.id)
        assertEquals(3, controller.state.value.messages.size)
        // The key, not the sentence. The controller used to write English prose into this field
        // and the test asserted on a fragment of it, so the assertion broke the moment the copy was
        // translated — which is the same coupling that let the English ship in the first place.
        assertEquals(
            UiMessage.of(MessageKey.AI_CONVERSATION_CHANGED),
            controller.state.value.error,
        )
    }

    @Test
    fun `blank message never reaches gateway`() = runTest {
        val gateway = FakeGateway(turns = ArrayDeque())
        val controller = AiAssistantController(gateway, this)

        controller.send("   ")
        advanceUntilIdle()

        assertEquals(0, gateway.sendCount)
        assertTrue(controller.state.value.error != null)
    }

    @Test
    fun `clear removes transcript and conversation on logout boundary`() = runTest {
        val gateway = FakeGateway(
            turns = ArrayDeque(listOf(turn("c1", "r1", AssistantHistoryPolicy.ACCOUNT, 30))),
        )
        val controller = AiAssistantController(gateway, this)
        controller.send("hello")
        advanceUntilIdle()

        controller.clear()

        assertTrue(controller.state.value.messages.isEmpty())
        assertNull(controller.state.value.conversation)
        assertFalse(controller.state.value.sending)
    }

    private fun turn(
        conversationId: String,
        replyId: String,
        policy: AssistantHistoryPolicy = AssistantHistoryPolicy.EPHEMERAL,
        retentionDays: Int? = null,
    ) = AssistantTurn(
        AssistantConversationMeta(conversationId, policy, retentionDays),
        AssistantMessage(replyId, AssistantRole.ASSISTANT, "Reply", null),
    )

    private class FakeGateway(
        private val turns: ArrayDeque<AssistantTurn>,
    ) : AiAssistantGateway {
        var sendCount: Int = 0
            private set

        override suspend fun send(conversationId: String?, message: String): AssistantTurn {
            sendCount++
            return turns.removeFirst()
        }
    }
}
