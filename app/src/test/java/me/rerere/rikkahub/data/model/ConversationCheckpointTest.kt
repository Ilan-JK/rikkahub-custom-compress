package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationCheckpointTest {
    @Test
    fun `partial ranges do not cross the latest checkpoint`() {
        val old = UIMessage.user("covered")
        val checkpoint = UIMessage.user("summary").copy(isContextCheckpoint = true)
        val uncompressed = listOf(UIMessage.user("a"), UIMessage.assistant("b"), UIMessage.user("c"))
        val messages = listOf(old, checkpoint) + uncompressed

        assertEquals(5, compressionEndIndex(messages, 0))
        assertEquals(3, compressionEndIndex(messages, 1))
        assertEquals(5, compressionEndIndex(messages, 100))
        assertEquals(4, compressionEndIndex(messages, -1))
        assertEquals(2, compressionEndIndex(messages, -100))
        assertEquals(2, compressionEndIndex(messages, Int.MIN_VALUE))
    }

    @Test
    fun `reading all summaries keeps their order and never restores covered messages`() {
        val c1 = UIMessage.user("c1").copy(isContextCheckpoint = true)
        val c2 = UIMessage.user("c2").copy(isContextCheckpoint = true)
        val tail = UIMessage.user("next")
        val messages = listOf(UIMessage.user("a"), c1, UIMessage.user("b"), c2, tail)

        assertEquals(listOf(c2, tail), messages.limitContext(0))
        assertEquals(listOf(c1, c2, tail), messages.limitContext(0, readAllSummaries = true))
    }

    @Test
    fun `context checkpoint is inserted after the anchor node without touching other nodes`() {
        val nodes = List(4) { UIMessage.user("message $it").toMessageNode() }
        val source = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = source.insertContextCheckpoint(afterNodeId = nodes[1].id, summary = "summary")!!

        assertEquals(nodes.subList(0, 2), result.messageNodes.subList(0, 2))
        assertEquals(nodes.subList(2, 4), result.messageNodes.subList(3, 5))
        val checkpoint = result.messageNodes[2].currentMessage
        assertTrue(checkpoint.isContextCheckpoint)
        assertEquals("summary", checkpoint.toText())
        // 只有检查点之后的消息会继续发送给模型
        assertEquals(result.currentMessages.subList(2, 5), result.currentMessages.limitContext(0))
    }

    @Test
    fun `context checkpoint is not inserted when the anchor node is gone`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(UIMessage.user("message").toMessageNode()),
        )

        assertNull(source.insertContextCheckpoint(afterNodeId = Uuid.random(), summary = "summary"))
    }
}
