package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.checkpointOwners
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationCheckpointTest {
    @Test
    fun `disabled summaries restore all original messages and retain their coverage on reload`() {
        val a = UIMessage.user("a")
        val b = UIMessage.assistant("b")
        val tail = UIMessage.user("tail")
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(a, b, tail).map { it.toMessageNode() },
        ).insertContextCheckpoint(listOf(0), "c1").insertContextCheckpoint(listOf(2), "c2")
        val disabled = source.copy(config = ConversationConfig(summariesEnabled = false))
        val reloaded = Json.decodeFromString<Conversation>(Json.encodeToString(disabled))
        val originals = listOf(a, b, tail)

        assertEquals(source.messageNodes, reloaded.messageNodes)
        for (readAll in listOf(false, true)) {
            assertEquals(originals, reloaded.uncompressedMessages(readAll))
            assertEquals(originals, reloaded.currentMessages.limitContext(0, readAll, reloaded.summariesEnabled))
        }
        val enabled = reloaded.copy(config = ConversationConfig(summariesEnabled = true))
        assertEquals(source.currentMessages.limitContext(0, true), enabled.currentMessages.limitContext(0, true, enabled.summariesEnabled))
        assertEquals(listOf(tail), enabled.uncompressedMessages())
    }

    @Test
    fun `partial ranges do not cross the latest checkpoint`() {
        val old = UIMessage.user("covered")
        val checkpoint = UIMessage.user("summary").copy(isContextCheckpoint = true)
        val uncompressed = listOf(UIMessage.user("a"), UIMessage.assistant("b"), UIMessage.user("c"))
        val messages = listOf(old, checkpoint) + uncompressed

        assertEquals(listOf(2, 3, 4), messages.compressionSelection(0, false))
        assertEquals(listOf(2), messages.compressionSelection(1, false))
        assertEquals(listOf(2, 3, 4), messages.compressionSelection(100, false))
        assertEquals(listOf(2, 3), messages.compressionSelection(-1, false))
        assertEquals(emptyList<Int>(), messages.compressionSelection(-100, false))
        assertEquals(emptyList<Int>(), messages.compressionSelection(Int.MIN_VALUE, false))
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

        val result = source.insertContextCheckpoint(selectedIndices = listOf(0, 1), summary = "summary")

        assertEquals(nodes.subList(0, 2), result.messageNodes.subList(0, 2))
        assertEquals(nodes.subList(2, 4), result.messageNodes.subList(3, 5))
        val checkpoint = result.messageNodes[2].currentMessage
        assertTrue(checkpoint.isContextCheckpoint)
        assertEquals("summary", checkpoint.toText())
        // 只有检查点之后的消息会继续发送给模型
        assertEquals(result.currentMessages.subList(2, 5), result.currentMessages.limitContext(0))
    }

    @Test
    fun `deleting an older legacy summary releases only its messages and persists after reload`() {
        val a1 = UIMessage.user("a1")
        val b1 = UIMessage.assistant("b1")
        val c1 = UIMessage.user("c1").copy(isContextCheckpoint = true)
        val a2 = UIMessage.user("a2")
        val c2 = UIMessage.user("c2").copy(isContextCheckpoint = true)
        val tail = UIMessage.user("tail")
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(a1, b1, c1, a2, c2, tail).map { it.toMessageNode() },
        )

        val updated = source.deleteMessage(c1.id)!!
        val reloaded = Json.decodeFromString<Conversation>(Json.encodeToString(updated))
        val messages = reloaded.currentMessages
        val remaining = messages.single { it.isContextCheckpoint }

        assertEquals(mapOf(a2.id to c2.id), messages.checkpointOwners())
        assertEquals(listOf(a1, b1, tail), reloaded.uncompressedMessages())
        assertEquals(listOf(remaining, tail), messages.limitContext(0))
        assertEquals(listOf(a1, b1, remaining, tail), messages.limitContext(0, readAllSummaries = true))
        assertEquals(listOf(remaining, tail), messages.limitContext(1, readAllSummaries = true))

        val latest = messages.slice(messages.compressionSelection(0, readAllSummaries = false))
        assertEquals(listOf(tail), latest)
        assertEquals(listOf(remaining, tail), reloaded.compressionHistory(latest).limitContext(0))
        assertEquals(emptyList<Int>(), messages.dropLast(1).compressionSelection(0, false))

        val all = messages.slice(messages.compressionSelection(0, readAllSummaries = true))
        assertEquals(listOf(a1, b1, tail), all)
        assertEquals(listOf(a1, b1, remaining, tail), reloaded.compressionHistory(all).limitContext(0, true))
        assertEquals(listOf(a1, b1), messages.slice(messages.compressionSelection(10, true)))
        val selected = messages.slice(messages.compressionSelection(-1, true))
        assertEquals(listOf(a1, b1, remaining), reloaded.compressionHistory(selected).limitContext(0, true))
    }

    @Test
    fun `recompressing an earlier span keeps its summary in timeline order`() {
        val a = UIMessage.user("a")
        val b = UIMessage.user("b")
        val tail = UIMessage.user("tail")
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(a, b, tail).map { it.toMessageNode() },
        )
        val c1 = Uuid.random()
        val c2 = Uuid.random()
        val c3 = Uuid.random()
        val restored = source.insertContextCheckpoint(listOf(0), "c1", c1)
            .insertContextCheckpoint(listOf(2), "c2", c2)
            .deleteMessage(c1)!!
        val recompressed = restored.insertContextCheckpoint(
            restored.currentMessages.compressionSelection(1, readAllSummaries = true), "c3", c3,
        )

        assertEquals(listOf(c2, tail.id), recompressed.currentMessages.limitContext(0).map { it.id })
        assertEquals(listOf(c3, c2, tail.id), recompressed.currentMessages.limitContext(0, true).map { it.id })
        assertEquals(listOf(a, tail), recompressed.deleteMessage(c3)!!.uncompressedMessages())
        assertEquals(listOf(b, tail), recompressed.deleteMessage(c2)!!.uncompressedMessages())
    }
}
