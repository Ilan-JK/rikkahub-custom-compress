package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.deleteMessage
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatMapEntriesTest {
    private fun checkpoint(text: String) = UIMessage.user(text).copy(isContextCheckpoint = true)
    private fun nodes(vararg messages: UIMessage) = messages.map { it.toMessageNode() }

    @Test
    fun `uncompressed rounds retain source indexes and merged replies`() {
        val rounds = nodes(
            UIMessage.assistant("greeting"), UIMessage.user("question"),
            UIMessage.assistant("tool call"), UIMessage.assistant("reply"),
        ).toPreviewRounds().visiblePreviewRounds()

        assertEquals(listOf(0, 1), rounds.map { it.firstIndex })
        assertEquals(listOf(0, 3), rounds.map { it.lastIndex })
        assertEquals("tool call reply", rounds.last().replyText)
    }

    @Test
    fun `covered originals are hidden by default and can be expanded and collapsed`() {
        val rounds = nodes(
            UIMessage.user("old"), UIMessage.assistant("reply"), checkpoint("A"),
            UIMessage.user("new"), UIMessage.assistant("new reply"),
        ).toPreviewRounds()
        val summary = rounds.single { it.isCheckpoint }

        assertEquals(listOf(2, 3), rounds.visiblePreviewRounds().map { it.firstIndex })
        assertEquals(2, summary.coveredMessageCount)
        assertEquals(listOf(0, 2, 3), rounds.visiblePreviewRounds(setOf(summary.id)).map { it.firstIndex })
        assertEquals(listOf(2, 3), rounds.visiblePreviewRounds(emptySet()).map { it.firstIndex })
    }

    @Test
    fun `each summary expands only its newly covered segment`() {
        val rounds = nodes(
            UIMessage.user("first"), checkpoint("A"), UIMessage.assistant("second"), checkpoint("B"),
            UIMessage.user("tail"),
        ).toPreviewRounds()
        val summaries = rounds.filter { it.isCheckpoint }

        assertEquals(listOf(1, 3, 4), rounds.visiblePreviewRounds().map { it.firstIndex })
        assertEquals(listOf(0, 1, 3, 4), rounds.visiblePreviewRounds(setOf(summaries[0].id)).map { it.firstIndex })
        assertEquals(listOf(1, 2, 3, 4), rounds.visiblePreviewRounds(setOf(summaries[1].id)).map { it.firstIndex })
        assertEquals(listOf(1, 1), summaries.map { it.coveredMessageCount })
        assertEquals(listOf(0, 2), summaries.map { it.coveredStartIndex })
    }

    @Test
    fun `summary splits a round and never absorbs its following assistant reply`() {
        val rounds = nodes(UIMessage.user("question"), checkpoint("A"), UIMessage.assistant("reply"))
            .toPreviewRounds()

        assertEquals(listOf(1, 2), rounds.visiblePreviewRounds().map { it.firstIndex })
        assertEquals("", rounds[1].replyText)
        assertEquals("reply", rounds[2].replyText)
        assertEquals(2, rounds[2].lastIndex)
    }

    @Test
    fun `hidden matches expose the summary control without revealing originals`() {
        val rounds = nodes(UIMessage.user("secret needle"), checkpoint("A"), UIMessage.user("tail"))
            .toPreviewRounds()
        val summary = rounds.single { it.isCheckpoint }

        assertEquals(listOf(1), rounds.visiblePreviewRounds(query = "NEEDLE").map { it.firstIndex })
        assertEquals(listOf(0, 1), rounds.visiblePreviewRounds(setOf(summary.id), "needle").map { it.firstIndex })
        assertTrue(rounds.visiblePreviewRounds(query = "missing").isEmpty())
    }

    @Test
    fun `current hidden message maps to summary while expanded message maps to original`() {
        val rounds = nodes(UIMessage.user("old"), checkpoint("A"), UIMessage.user("tail"))
            .toPreviewRounds()
        val summary = rounds[1]

        assertTrue(summary.represents(0, emptySet()))
        assertFalse(summary.represents(0, setOf(summary.id)))
        assertTrue(rounds[0].represents(0, setOf(summary.id)))
        assertEquals(1, summary.firstIndex)
    }

    @Test
    fun `deleting latest checkpoint restores its originals and keeps earlier coverage`() {
        val latest = checkpoint("B")
        val conversation = Conversation.ofId(Uuid.random(), messages = nodes(
            UIMessage.user("first"), checkpoint("A"), UIMessage.user("second"), latest,
            UIMessage.assistant("tail"),
        ))
        val rounds = conversation.deleteMessage(latest.id)!!.messageNodes.toPreviewRounds().visiblePreviewRounds()

        assertEquals(listOf(1, 2), rounds.map { it.firstIndex })
        assertEquals("second", rounds.last().userText)
        assertEquals("tail", rounds.last().replyText)
    }

    @Test
    fun `deleting earlier checkpoint invalidates dependent summaries and reveals originals`() {
        val first = checkpoint("A")
        val conversation = Conversation.ofId(Uuid.random(), messages = nodes(
            UIMessage.user("first"), first, UIMessage.assistant("second"), checkpoint("B"),
        ))
        val rounds = conversation.deleteMessage(first.id)!!.messageNodes.toPreviewRounds()

        assertFalse(rounds.any { it.isCheckpoint })
        assertEquals(rounds, rounds.visiblePreviewRounds())
        assertEquals("first", rounds.single().userText)
        assertEquals("second", rounds.single().replyText)
    }

    @Test
    fun `counts exclude checkpoints synthetic messages and system entries`() {
        val rounds = nodes(
            UIMessage.system("system"), UIMessage.user("internal").copy(isSynthetic = true),
            UIMessage.user("user"), checkpoint("A"), checkpoint("B"),
        ).toPreviewRounds()

        assertEquals(listOf(1, 0), rounds.filter { it.isCheckpoint }.map { it.coveredMessageCount })
        assertEquals(listOf(3, 4), rounds.visiblePreviewRounds().map { it.firstIndex })
    }

    @Test
    fun `empty history and selected branches use their current contents`() {
        assertTrue(emptyList<me.rerere.rikkahub.data.model.MessageNode>().toPreviewRounds().isEmpty())
        val original = UIMessage.user("old")
        val selected = UIMessage.user("selected")
        val branch = original.toMessageNode().copy(messages = listOf(original, selected), selectIndex = 1)
        val rounds = listOf(branch).toPreviewRounds()

        assertEquals(branch.id, rounds.single().id)
        assertEquals("selected", rounds.single().userText)
    }
}
