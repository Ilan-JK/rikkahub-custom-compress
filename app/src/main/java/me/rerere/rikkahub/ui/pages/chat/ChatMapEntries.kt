package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.isCountableContextMessage
import kotlin.uuid.Uuid

internal data class PreviewRound(
    val id: Uuid,
    val firstIndex: Int,
    val lastIndex: Int,
    val user: UIMessage?,
    val reply: UIMessage?,
    val userText: String,
    val replyText: String,
    val replyParts: List<UIMessagePart>,
    val favorite: Boolean,
    val isCheckpoint: Boolean = false,
    val coveredBy: Uuid? = null,
    val coveredStartIndex: Int = firstIndex,
    val coveredMessageCount: Int = 0,
) {
    fun matches(query: String): Boolean =
        userText.contains(query, ignoreCase = true) || replyText.contains(query, ignoreCase = true)

    fun represents(index: Int, expandedCheckpoints: Set<Uuid>): Boolean =
        index in firstIndex..lastIndex ||
            (isCheckpoint && id !in expandedCheckpoints && index in coveredStartIndex until firstIndex)
}

internal fun List<MessageNode>.toPreviewRounds(): List<PreviewRound> {
    val groups = mutableListOf<MutableList<IndexedValue<MessageNode>>>()
    forEachIndexed { index, node ->
        // A checkpoint can split a user/assistant round and must never absorb the following reply.
        if (groups.isEmpty() || node.currentMessage.role == MessageRole.USER ||
            node.currentMessage.isContextCheckpoint ||
            groups.last().last().value.currentMessage.isContextCheckpoint
        ) {
            groups.add(mutableListOf())
        }
        groups.last().add(IndexedValue(index, node))
    }
    var coveredStart = 0
    val rounds = groups.map { group ->
        val first = group.first()
        val user = first.value.currentMessage.takeIf { it.role == MessageRole.USER }
        val replies = group.map { it.value.currentMessage }.filter { it.role != MessageRole.USER }
        val checkpoint = first.value.currentMessage.isContextCheckpoint
        PreviewRound(
            id = first.value.id,
            firstIndex = first.index,
            lastIndex = group.last().index,
            user = user,
            reply = replies.lastOrNull(),
            userText = user?.toPreviewText().orEmpty(),
            replyText = replies.map { it.toPreviewText() }.filter { it.isNotEmpty() }.joinToString(" "),
            replyParts = replies.flatMap { it.parts },
            favorite = group.any { it.value.isFavorite },
            isCheckpoint = checkpoint,
            coveredStartIndex = if (checkpoint) coveredStart else first.index,
            coveredMessageCount = if (checkpoint) {
                subList(coveredStart, first.index).count { it.currentMessage.isCountableContextMessage }
            } else 0,
        ).also {
            if (checkpoint) coveredStart = first.index + 1
        }
    }
    var checkpointId: Uuid? = null
    return rounds.asReversed().map { round ->
        if (round.isCheckpoint) {
            checkpointId = round.id
            round
        } else {
            round.copy(coveredBy = checkpointId)
        }
    }.asReversed()
}

internal fun List<PreviewRound>.visiblePreviewRounds(
    expandedCheckpoints: Set<Uuid> = emptySet(),
    query: String = "",
): List<PreviewRound> {
    // Keep the summary's expand control available when the search only matches its hidden originals.
    val matchingCheckpoints = if (query.isBlank()) emptySet() else {
        filter { it.matches(query) }.mapNotNull { it.coveredBy }.toSet()
    }
    return filter { round ->
        (round.coveredBy == null || round.coveredBy in expandedCheckpoints) &&
            (query.isBlank() || round.matches(query) || round.id in matchingCheckpoints)
    }
}

private val CodeFenceRegex = Regex("```[\\w-]*")
private val HeadingRegex = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
private val QuoteRegex = Regex("(?m)^\\s{0,3}>\\s?")
private val EmphasisRegex = Regex("\\*\\*|__|~~")
private val WhitespaceRegex = Regex("\\s+")

private fun UIMessage.toPreviewText(): String = toText()
    .replace(CodeFenceRegex, " ")
    .replace(HeadingRegex, "")
    .replace(QuoteRegex, "")
    .replace(EmphasisRegex, "")
    .replace(WhitespaceRegex, " ")
    .trim()
