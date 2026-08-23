package com.coinepro.core.aiassistant

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
            turn = AssistantTurn(
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
    fun `blank message never reaches gateway`() = runTest {
        val gateway = FakeGateway(turn = null)
        val controller = AiAssistantController(gateway, this)

        controller.send("   ")
        advanceUntilIdle()

        assertEquals(0, gateway.sendCount)
        assertTrue(controller.state.value.error != null)
    }

    @Test
    fun `clear removes transcript and conversation on logout boundary`() = runTest {
        val gateway = FakeGateway(
            turn = AssistantTurn(
                AssistantConversationMeta("c1", AssistantHistoryPolicy.ACCOUNT, 30),
                AssistantMessage("r1", AssistantRole.ASSISTANT, "Reply", null),
            ),
        )
        val controller = AiAssistantController(gateway, this)
        controller.send("hello")
        advanceUntilIdle()

        controller.clear()

        assertTrue(controller.state.value.messages.isEmpty())
        assertNull(controller.state.value.conversation)
        assertFalse(controller.state.value.sending)
    }

    private class FakeGateway(
        private val turn: AssistantTurn?,
    ) : AiAssistantGateway {
        var sendCount: Int = 0
            private set

        override suspend fun send(conversationId: String?, message: String): AssistantTurn {
            sendCount++
            return requireNotNull(turn)
        }
    }
}
