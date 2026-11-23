package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressionSummaryTest {
    private fun reply(vararg parts: UIMessagePart) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = parts.toList(),
    )

    @Test
    fun `plain text reply is the summary`() {
        val summary = reply(UIMessagePart.Text("  [Summary of previous conversation]\n## User Goals\n...  "))
            .compressionSummaryOrNull()

        assertEquals("[Summary of previous conversation]\n## User Goals\n...", summary)
    }

    @Test
    fun `reply with a tool call is rejected even if it has text`() {
        val summary = reply(
            UIMessagePart.Text("Let me check the file first."),
            UIMessagePart.Tool(toolCallId = "call_1", toolName = "workspace_shell", input = "{}"),
        ).compressionSummaryOrNull()

        assertNull(summary)
    }

    @Test
    fun `inline think tags are stripped`() {
        val summary = reply(UIMessagePart.Text("<think>planning the summary</think>\n[Summary of previous conversation]"))
            .compressionSummaryOrNull()

        assertEquals("[Summary of previous conversation]", summary)
    }

    @Test
    fun `reasoning parts are not part of the summary`() {
        val summary = reply(
            UIMessagePart.Reasoning(reasoning = "planning the summary"),
            UIMessagePart.Text("[Summary of previous conversation]"),
        ).compressionSummaryOrNull()

        assertEquals("[Summary of previous conversation]", summary)
    }

    @Test
    fun `reply without text gives a blank summary`() {
        assertEquals("", reply().compressionSummaryOrNull())
    }

    @Test
    fun `a new user message sends previous replies without their thinking`() {
        val first = UIMessage.user("Hello")
        val answer = reply(UIMessagePart.Reasoning("previous thinking"), UIMessagePart.Text("Hi"))
        val next = UIMessage.user("Good evening")
        val history = listOf(first, answer, next)

        assertEquals(
            listOf(first, answer.copy(parts = listOf(UIMessagePart.Text("Hi"))), next),
            history.trimRequestReasoning(keepCurrentToolTurn = true),
        )
        assertEquals(UIMessagePart.Reasoning("previous thinking").reasoning,
            (history[1].parts.first() as UIMessagePart.Reasoning).reasoning)
    }

    @Test
    fun `a summary request strips inline and structured thinking but keeps tool results`() {
        val tool = UIMessagePart.Tool(toolCallId = "call_1", toolName = "lookup", input = "{}",
            output = listOf(UIMessagePart.Text("Lookup result")))
        val history = listOf(
            UIMessage.user("Find it"),
            reply(UIMessagePart.Text("<think>old inline thinking</think>"), tool),
            reply(UIMessagePart.Reasoning("no answer yet")),
            reply(UIMessagePart.Reasoning("structured thinking"), UIMessagePart.Text("Found it")),
            UIMessage.user("Summarize"),
        )

        val sent = history.trimRequestReasoning(keepCurrentToolTurn = false)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.ASSISTANT, MessageRole.USER), sent.map { it.role })
        assertEquals(listOf(tool), sent[1].parts.filterNot { it is UIMessagePart.Text && it.text.isBlank() })
        assertEquals(listOf(UIMessagePart.Text("Found it")), sent[2].parts)
    }

    @Test
    fun `a tool continuation retains reasoning only for the current turn`() {
        val oldReply = reply(UIMessagePart.Reasoning("old thought"), UIMessagePart.Text("Old answer"))
        val currentReply = reply(
            UIMessagePart.Reasoning("required for tool continuation"),
            UIMessagePart.Tool(toolCallId = "call_2", toolName = "lookup", input = "{}"),
        )
        val history = listOf(oldReply, UIMessage.user("Look it up"), currentReply)

        val sent = history.trimRequestReasoning(keepCurrentToolTurn = true)
        assertEquals(listOf(UIMessagePart.Text("Old answer")), sent.first().parts)
        assertEquals(currentReply, sent.last())
        assertEquals(history, history.trimRequestReasoning(keepCurrentToolTurn = true, replayAllForTools = true))
    }
}
