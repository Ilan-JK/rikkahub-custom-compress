package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ContextSummaryMode
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.datastore.CompressionPromptRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExistingSummaryMarkersTest {
    private val first = UIMessage.user("first summary").copy(isContextCheckpoint = true)
    private val second = UIMessage.user("second summary").copy(isContextCheckpoint = true)
    private val original = UIMessage.user("covered original")
    private val next = UIMessage.assistant("new information")
    private val history = listOf(original, first, original, second, next)

    @Test
    fun `all mode numbers only selected summaries and preserves new message roles`() {
        val result = history.limitContext(0, ContextSummaryMode.ALL).withExistingSummaryMarkers()

        assertEquals(listOf(MessageRole.USER, MessageRole.USER, MessageRole.ASSISTANT), result.map { it.role })
        assertEquals("<existing_summary index=\"1\">\nfirst summary\n</existing_summary>", result[0].toText())
        assertTrue(result[1].toText().startsWith("<existing_summary index=\"2\">"))
        assertSame(next, result[2])
        assertEquals("first summary", first.toText())
        assertEquals("second summary", second.toText())
    }

    @Test
    fun `latest mode wraps only the latest summary and excludes covered originals`() {
        val result = history.limitContext(0, ContextSummaryMode.LATEST).withExistingSummaryMarkers()

        assertEquals(2, result.size)
        assertTrue(result.first().toText().startsWith("<existing_summary index=\"1\">"))
        assertTrue(result.first().toText().contains("second summary"))
        assertFalse(result.first().toText().contains("first summary"))
        assertSame(next, result.last())
    }

    @Test
    fun `summary looking ordinary messages are not wrapped`() {
        val message = UIMessage.user("[Conversation Summary]\nordinary message")

        assertSame(message, listOf(message).withExistingSummaryMarkers().single())
    }

    @Test
    fun `compression instruction stays outside summaries for either prompt role`() {
        for (role in CompressionPromptRole.entries) {
            val result = listOf(first).withCompressionPrompt("compress new history", role)

            val summary = result.single { it.toText().startsWith("<conversation_history") }.toText()
            assertTrue(summary.contains("<existing_summary index=\"1\">\nfirst summary\n</existing_summary>"))
            assertFalse(summary.contains("compress new history"))
            assertEquals(1, result.count { it.toText() == "compress new history" })
            assertEquals(MessageRole.USER, result.last().role)
        }
    }

    @Test
    fun `compression packs only selected checkpoints and uncovered history in both reading modes`() {
        for (mode in ContextSummaryMode.entries) {
            val result = history.limitContext(0, mode).withCompressionPrompt("summarize", CompressionPromptRole.SYSTEM)
            val source = result.last().toText()
            assertTrue(source.contains("second summary"))
            assertTrue(source.contains("new information"))
            assertFalse(source.contains("covered original"))
            assertEquals(mode == ContextSummaryMode.ALL, source.contains("first summary"))
        }
    }

    @Test
    fun `normal chat keeps summary text without markers in both reading modes`() {
        for (mode in ContextSummaryMode.entries) {
            val result = (history + UIMessage.user("continue")).limitContext(0, mode).withContinuationHint()

            for (summary in result.filter { it.isContextCheckpoint }) {
                assertFalse(summary.toText().contains("<existing_summary"))
                assertFalse(summary.toText().contains("</existing_summary>"))
            }
            assertEquals("new information", result.first { it.role == MessageRole.ASSISTANT }.toText())
        }
        assertEquals("first summary", first.toText())
    }

    @Test
    fun `normal chat still adds continuation hint without summary markers`() {
        val result = listOf(first).withContinuationHint().single().toText()

        assertTrue(result.contains("The conversation history before this point"))
        assertFalse(result.contains("<existing_summary"))
        assertFalse(result.contains("</existing_summary>"))
    }
}
