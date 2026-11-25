package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.ContextCheckpointCoverage
import me.rerere.ai.ui.ModelSnapshot
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.checkpointOwners
import me.rerere.ai.ui.contextCheckpoints
import me.rerere.ai.ui.uncoveredContextIndices
import me.rerere.ai.util.InstantSerializer
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.datastore.Settings
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Conversation(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val title: String = "",
    val messageNodes: List<MessageNode>,
    val chatSuggestions: List<String> = emptyList(),
    val isPinned: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
    val customSystemPrompt: String? = null,
    val modeInjectionIds: Set<Uuid> = emptySet(),
    val lorebookIds: Set<Uuid> = emptySet(),
    // 会话开始后固定的配置，null 表示尚未开始、跟随助手
    val config: ConversationConfig? = null,
    // Absolute path inside the workspace rootfs
    val workspaceCwd: String? = null,
    // 所属文件夹（助手内分组），null 表示未归入任何文件夹
    val folderId: Uuid? = null,
    @Transient
    val newConversation: Boolean = false
) {
    val files: List<Uri>
        get() = messageNodes
            .flatMap { node -> node.messages.flatMap { it.parts } }
            .localFileUrls()
            .map { it.toUri() }

    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> {
            return messageNodes.map { node -> node.messages[node.selectIndex] }
        }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        val newNodes = this.messageNodes.toMutableList()

        messages.forEachIndexed { index, message ->
            val node = newNodes
                .getOrElse(index) { message.toMessageNode() }

            val newMessages = node.messages.toMutableList()
            var newMessageIndex = node.selectIndex
            if (newMessages.any { it.id == message.id }) {
                newMessages[newMessages.indexOfFirst { it.id == message.id }] = message
            } else {
                newMessages.add(message)
                newMessageIndex = newMessages.lastIndex
            }

            val newNode = node.copy(
                messages = newMessages,
                selectIndex = newMessageIndex
            )

            // 更新newNodes
            if (index > newNodes.lastIndex) {
                newNodes.add(newNode)
            } else {
                newNodes[index] = newNode
            }
        }

        return this.copy(
            messageNodes = newNodes
        )
    }

    /**
     * 写入一条消息：[nodeId] 节点里已有同 id 的消息就原地替换（不改变选中的分支），否则作为新分支加入并选中；
     * 节点不存在时追加到末尾。按节点 id 而不是下标定位，生成期间别的节点被增删、分支被切换，回复也不会写错位置。
     */
    fun upsertMessage(nodeId: Uuid, message: UIMessage): Conversation {
        // 生成中的回复几乎总在最后一个节点
        val index = messageNodes.indexOfLast { it.id == nodeId }
        if (index == -1) {
            return copy(messageNodes = messageNodes + MessageNode(id = nodeId, messages = listOf(message)))
        }
        val node = messageNodes[index]
        val existing = node.messages.indexOfFirst { it.id == message.id }
        val updated = if (existing == -1) {
            node.copy(messages = node.messages + message, selectIndex = node.messages.size)
        } else {
            node.copy(messages = node.messages.toMutableList().also { it[existing] = message })
        }
        return copy(messageNodes = messageNodes.toMutableList().also { it[index] = updated })
    }

    companion object {
        fun ofId(
            id: Uuid,
            assistantId: Uuid = DEFAULT_ASSISTANT_ID,
            messages: List<MessageNode> = emptyList(),
            newConversation: Boolean = false
        ) = Conversation(
            id = id,
            assistantId = assistantId,
            messageNodes = messages,
            newConversation = newConversation,
        )
    }
}

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    @Transient
    val isFavorite: Boolean = false,
) {
    val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
        throw IllegalStateException(
            "MessageNode has no valid current message: messages.size=${messages.size}, selectIndex=$selectIndex"
        )
    } else {
        messages[selectIndex]
    }

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER

    companion object {
        fun of(message: UIMessage) = MessageNode(
            messages = listOf(message),
            selectIndex = 0
        )
    }
}

/**
 * 最近一次回复结束时的上下文占用（token），取不到时返回 null。
 * 之后又插入了压缩检查点的话，记录的占用已经过时，同样返回 null。
 */
fun Conversation.lastContextTokens(): Int? {
    val messages = currentMessages
    val message = messages.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return null
    val checkpoint = messages.contextCheckpoints().lastOrNull()
    if (checkpoint != null && checkpoint.createdAt > message.createdAt) return null
    return message.contextTokens()
}

internal fun Conversation.uncompressedMessages(readAllSummaries: Boolean = true): List<UIMessage> {
    val messages = currentMessages
    if (!summariesEnabled) return messages.filterNot { it.isContextCheckpoint }
    return messages.slice(messages.uncoveredContextIndices(readAllSummaries))
}

internal fun Conversation.compressionHistory(selected: List<UIMessage>, beforeSummaryId: Uuid? = null): List<UIMessage> {
    val messages = withExplicitCheckpointCoverage().currentMessages
    val included = selected.mapTo(hashSetOf()) { it.id }
    val checkpoints = messages.contextCheckpoints().takeWhile { it.id != beforeSummaryId }
    checkpoints.mapTo(included) { it.id }
    return messages.filter { it.id in included }
}

internal fun UIMessage.countsForCompression(): Boolean =
    !isSynthetic && !isContextCheckpoint && !isNoticeOnly &&
        (role == MessageRole.USER || role == MessageRole.ASSISTANT) && isValidToUpload()

/** 正值只取最早的一段原文，负值从所有未覆盖原文中保留最近的条数。 */
internal fun List<UIMessage>.compressionSelection(range: Int, readAllSummaries: Boolean): List<Int> {
    val uncovered = uncoveredContextIndices(readAllSummaries)
    val candidates = if (range > 0) {
        val first = uncovered.indexOfFirst { this[it].countsForCompression() }
        if (first < 0) return emptyList()
        var start = first
        while (start > 0 && uncovered[start - 1] + 1 == uncovered[start]) start--
        var end = first + 1
        while (end < uncovered.size && uncovered[end - 1] + 1 == uncovered[end]) end++
        uncovered.subList(start, end)
    } else uncovered

    val count = candidates.count { this[it].countsForCompression() }
    val desired = when {
        range == 0 -> count
        range > 0 -> minOf(range, count)
        else -> (count.toLong() + range).coerceAtLeast(0).toInt()
    }
    if (desired == 0) return emptyList()
    if (desired == count) return candidates

    var seen = 0
    var boundary = candidates.indexOfFirst { this[it].countsForCompression() && ++seen == desired } + 1
    // 旧版导入的独立工具结果与前面的助手消息一起压缩。
    while (boundary < candidates.size && candidates[boundary - 1] + 1 == candidates[boundary] &&
        this[candidates[boundary]].role == MessageRole.TOOL
    ) boundary++
    return candidates.take(boundary)
}

/** 旧摘要的范围在改变摘要位置或删掉摘要前固定下来。 */
internal fun Conversation.withExplicitCheckpointCoverage(): Conversation {
    var start = 0
    val updated = messageNodes.mapIndexed { index, node ->
        if (!node.currentMessage.isContextCheckpoint) return@mapIndexed node
        val segmentStart = start
        start = index + 1
        val checkpoint = node.currentMessage
        if (checkpoint.checkpointCoverage != null) return@mapIndexed node
        val coverage = ContextCheckpointCoverage(messageNodes.messageIdsIn(segmentStart until index))
        node.copy(messages = node.messages.toMutableList().also {
            it[node.selectIndex] = checkpoint.copy(checkpointCoverage = coverage)
        })
    }
    return if (updated == messageNodes) this else copy(messageNodes = updated)
}

private fun List<MessageNode>.messageIdsIn(indices: Iterable<Int>): Set<Uuid> =
    indices.flatMapTo(mutableSetOf()) { index -> this[index].messages.map { it.id } }

internal fun Conversation.insertContextCheckpoint(
    selectedIndices: List<Int>,
    summary: String,
    summaryId: Uuid = Uuid.random(),
): Conversation {
    val conversation = withExplicitCheckpointCoverage()
    val nodes = conversation.messageNodes
    val messages = conversation.currentMessages
    val sequence = messages.contextCheckpoints().lastOrNull()?.checkpointCoverage?.sequence ?: 0
    val covered = nodes.messageIdsIn(selectedIndices) - messages.checkpointOwners().keys
    val checkpoint = UIMessage.user(summary).copy(
        id = summaryId,
        isContextCheckpoint = true,
        checkpointCoverage = ContextCheckpointCoverage(covered, sequence + 1),
    ).toMessageNode()
    val end = selectedIndices.last() + 1
    return copy(
        messageNodes = nodes.subList(0, end) + checkpoint + nodes.subList(end, nodes.size),
    )
}

/** 有工具调用在等待用户审批：这一轮还没结束，排队的消息要等审批完成后再发 */
internal val Conversation.isAwaitingToolApproval: Boolean
    get() = currentMessages.any { message -> message.getTools().any { it.isPending } }

/** 等待用户审批的工具调用，和 [isAwaitingToolApproval] 的判断一致 */
internal val Conversation.pendingToolCalls: List<UIMessagePart.Tool>
    get() = currentMessages.flatMap { message -> message.getTools().filter { it.isPending } }

/**
 * 去掉还有未执行、又没法继续执行的工具调用的消息（比如还在等待审批），这样的消息发给模型会被拒绝。
 * 已经批准、拒绝或回答过的工具调用之后会继续执行，保留。只处理下标小于 [untilNode] 的节点。
 */
internal fun Conversation.withoutUnresumableToolCalls(untilNode: Int = messageNodes.size): Conversation {
    val nodes = messageNodes.mapIndexedNotNull { index, node ->
        if (index >= untilNode) return@mapIndexedNotNull node
        val unexecuted = node.currentMessage.getTools().filter { !it.isExecuted }
        if (unexecuted.isEmpty() || unexecuted.any { it.canResumeExecution }) return@mapIndexedNotNull node
        val messages = node.messages.filter { it.id != node.currentMessage.id }
        if (messages.isEmpty()) return@mapIndexedNotNull null
        node.copy(messages = messages, selectIndex = (node.selectIndex - 1).coerceIn(messages.indices))
    }
    return if (nodes == messageNodes) this else copy(messageNodes = nodes)
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}

/**
 * 给还没有模型快照的消息补上快照，没有可补的消息时原样返回。
 *
 * 在保存会话时调用：新生成的消息由此带上快照，引入快照之前的旧消息也在会话下次保存时补齐。
 * 模型已经被删除的旧消息无从补起，保持原样。
 */
fun Conversation.fillModelSnapshots(settings: Settings): Conversation {
    val models = settings.providers.flatMap { it.models }.associateBy { it.id }
    val nodes = messageNodes.map { node ->
        val messages = node.messages.map { it.fillModelSnapshot(models) }
        if (messages == node.messages) node else node.copy(messages = messages)
    }
    return if (nodes == messageNodes) this else copy(messageNodes = nodes)
}

private fun UIMessage.fillModelSnapshot(models: Map<Uuid, Model>): UIMessage {
    if (modelSnapshot != null) return this
    val model = modelId?.let(models::get) ?: return this
    return copy(modelSnapshot = ModelSnapshot(modelId = model.modelId, displayName = model.displayName))
}

/** 本地附件引用，包含工具结果中的嵌套附件。 */
internal fun List<UIMessagePart>.localFileUrls(): Set<String> = buildSet {
    this@localFileUrls.forEach { part ->
        val url = when (part) {
            is UIMessagePart.Image -> part.url
            is UIMessagePart.Document -> part.url
            is UIMessagePart.Video -> part.url
            is UIMessagePart.Audio -> part.url
            is UIMessagePart.Tool -> {
                addAll(part.output.localFileUrls())
                null
            }

            else -> null
        }
        if (url?.startsWith("file://") == true) add(url)
    }
}

/** 替换本地附件的地址，覆盖范围与 [localFileUrls] 一致，包含工具结果中的嵌套附件。 */
internal fun UIMessagePart.mapLocalFileUrls(transform: (String) -> String): UIMessagePart {
    fun String.mapped() = if (startsWith("file://")) transform(this) else this
    return when (this) {
        is UIMessagePart.Image -> copy(url = url.mapped())
        is UIMessagePart.Document -> copy(url = url.mapped())
        is UIMessagePart.Video -> copy(url = url.mapped())
        is UIMessagePart.Audio -> copy(url = url.mapped())
        is UIMessagePart.Tool -> copy(output = output.map { it.mapLocalFileUrls(transform) })
        else -> this
    }
}
