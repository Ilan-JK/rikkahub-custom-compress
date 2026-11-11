package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.CompressionRange
import me.rerere.rikkahub.data.datastore.CompressionRangeMode
import me.rerere.rikkahub.data.datastore.AutoCompressionTrigger
import me.rerere.rikkahub.data.datastore.CompressionSettings
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationCompressionTest {
    private fun conversation(vararg messages: UIMessage) = Conversation.ofId(
        Uuid.random(), messages = messages.map { it.toMessageNode() },
    )
    private fun checkpoint(text: String) = UIMessage.user(text).copy(isContextCheckpoint = true)

    @Test
    fun `only ordinary selected messages after newest checkpoint count`() {
        val conversation = conversation(
            UIMessage.user("old"), checkpoint("A"), UIMessage.assistant("covered"), checkpoint("B"),
            UIMessage.system("system"), UIMessage.user("new"), UIMessage.assistant("reply"),
            UIMessage.user("internal").copy(isSynthetic = true),
        )
        assertEquals(2, conversation.uncompressedMessageCount())
        assertEquals(6, conversation.compressionEndIndex(CompressionRange(CompressionRangeMode.EARLIEST, earliestCount = 1)))
        assertEquals(6, conversation.compressionEndIndex(CompressionRange(keepRecentCount = 1)))
        assertNull(conversation.compressionEndIndex(CompressionRange(keepRecentCount = 2)))
    }

    @Test
    fun `earliest count clamps and keeping zero compresses all`() {
        val conversation = conversation(UIMessage.user("one"), UIMessage.assistant("two"))
        assertEquals(2, conversation.compressionEndIndex(CompressionRange(CompressionRangeMode.EARLIEST)))
        assertEquals(2, conversation.compressionEndIndex(CompressionRange(keepRecentCount = 0)))
        assertNull(conversation().compressionEndIndex(CompressionRange()))
    }

    @Test
    fun `editing covered text invalidates downstream summaries but keeps originals`() {
        val original = UIMessage.user("old")
        val conversation = conversation(original, checkpoint("A"), UIMessage.assistant("reply"), checkpoint("B"))
        val edited = conversation.editMessage(original.id, listOf(UIMessagePart.Text("new")))
        assertEquals(listOf("new", "reply"), edited.currentMessages.map { it.toText() })
        assertEquals(2, edited.messageNodes.first().messages.size)
    }

    @Test
    fun `tools inside one assistant message count once`() {
        val toolMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Tool(toolCallId = "id", toolName = "tool", input = "{}")),
        )
        assertEquals(2, conversation(UIMessage.user("run"), toolMessage).uncompressedMessageCount())
    }

    @Test
    fun `count trigger works without token usage but needs compressible content and enabled feature`() {
        val conversation = conversation(UIMessage.user("one"), UIMessage.assistant("two"))
        val settings = Settings(
            enableAutoCompress = true,
            compression = CompressionSettings(
                trigger = AutoCompressionTrigger.MESSAGE_COUNT, messageThreshold = 2,
                automaticRange = CompressionRange(keepRecentCount = 1),
            ),
        )
        assertEquals(true, conversation.shouldAutoCompress(settings, 0))
        assertEquals(false, conversation.shouldAutoCompress(settings.copy(enableAutoCompress = false), 0))
        assertEquals(false, conversation.shouldAutoCompress(settings.copy(
            compression = settings.compression.copy(messageThreshold = 3),
        ), 0))
        assertEquals(false, conversation.shouldAutoCompress(settings.copy(
            compression = settings.compression.copy(automaticRange = CompressionRange(keepRecentCount = 2)),
        ), 0))
        assertEquals(false, conversation.shouldAutoCompress(settings.copy(
            compression = settings.compression.copy(trigger = AutoCompressionTrigger.TOKEN_RATIO),
        ), 100))
        assertEquals(false, conversation.shouldAutoCompress(settings.copy(
            assistants = listOf(Assistant(id = conversation.assistantId, contextMessageLimit = 5)),
        ), 0))
    }

    @Test
    fun `deleting or switching covered messages invalidates later summaries`() {
        val first = UIMessage.user("one")
        val alternate = UIMessage.user("other")
        val conversation = conversation(first, checkpoint("A"), UIMessage.assistant("two"), checkpoint("B"))
        assertEquals(listOf("two"), conversation.deleteMessage(first.id)!!.currentMessages.map { it.toText() })
        val branched = conversation.copy(messageNodes = conversation.messageNodes.mapIndexed { index, node ->
            if (index == 0) node.copy(messages = listOf(first, alternate)) else node
        })
        val switched = branched.selectBranch(branched.messageNodes.first().id, 1)
        assertEquals(listOf("other", "two"), switched.currentMessages.map { it.toText() })
    }

    @Test
    fun `unchanged selection and deleting an unselected alternative preserve summaries`() {
        val first = UIMessage.user("one")
        val other = UIMessage.user("other")
        val summary = checkpoint("A")
        val conversation = conversation(first, summary).let { original ->
            original.copy(messageNodes = listOf(
                original.messageNodes.first().copy(messages = listOf(first, other)),
                original.messageNodes.last(),
            ))
        }
        assertEquals(conversation, conversation.selectBranch(conversation.messageNodes.first().id, 0))
        assertEquals(listOf("one", "A"), conversation.deleteMessage(other.id)!!.currentMessages.map { it.toText() })
    }

    @Test
    fun `count trigger waits for tool approval and execution`() {
        val settings = Settings(
            enableAutoCompress = true,
            compression = CompressionSettings(
                trigger = AutoCompressionTrigger.MESSAGE_COUNT, messageThreshold = 1,
                automaticRange = CompressionRange(keepRecentCount = 0),
            ),
        )
        val tool = UIMessagePart.Tool(
            toolCallId = "call", toolName = "tool", input = "{}",
            approvalState = me.rerere.ai.ui.ToolApprovalState.Pending,
        )
        fun withTool(value: UIMessagePart.Tool) = conversation(
            UIMessage.user("run"), UIMessage(role = MessageRole.ASSISTANT, parts = listOf(value)),
        )
        assertEquals(false, withTool(tool).shouldAutoCompress(settings, 0))
        assertEquals(false, withTool(tool.copy(approvalState = me.rerere.ai.ui.ToolApprovalState.Approved)).shouldAutoCompress(settings, 0))
        assertEquals(true, withTool(tool.copy(
            approvalState = me.rerere.ai.ui.ToolApprovalState.Approved,
            output = listOf(UIMessagePart.Text("done")),
        )).shouldAutoCompress(settings, 0))
    }
}
