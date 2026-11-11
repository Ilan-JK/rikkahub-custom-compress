package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.CompressionRange
import me.rerere.rikkahub.data.datastore.AutoCompressionTrigger
import me.rerere.rikkahub.data.datastore.Settings

internal val UIMessage.isCountableContextMessage: Boolean
    get() = !isContextCheckpoint && !isSynthetic && !isNoticeOnly &&
        (role == MessageRole.USER || role == MessageRole.ASSISTANT)

internal fun Conversation.uncompressedMessageCount(): Int {
    val start = messageNodes.indexOfLast { it.currentMessage.isContextCheckpoint } + 1
    return messageNodes.drop(start).count { it.currentMessage.isCountableContextMessage }
}

/** Exclusive node boundary, shared by triggering, validation and the compression request. */
internal fun Conversation.compressionEndIndex(range: CompressionRange): Int? {
    val start = messageNodes.indexOfLast { it.currentMessage.isContextCheckpoint } + 1
    val candidates = (start until messageNodes.size).filter {
        messageNodes[it].currentMessage.isCountableContextMessage
    }
    val count = range.compressionCount(candidates.size)
    if (count == 0) return null
    return if (count == candidates.size) messageNodes.size else candidates[count]
}

internal fun Conversation.shouldAutoCompress(settings: Settings, contextLength: Int): Boolean {
    if (!settings.enableAutoCompress || settings.getAssistantOf(this).contextMessageLimit > 0) return false
    if (currentMessages.any { message -> message.getTools().any { it.isPending || it.canResumeExecution } }) return false
    if (compressionEndIndex(settings.compression.automaticRange) == null) return false
    return when (settings.compression.trigger) {
        AutoCompressionTrigger.MESSAGE_COUNT -> uncompressedMessageCount() >= settings.compression.messageThreshold
        AutoCompressionTrigger.TOKEN_RATIO -> contextLength > 0 &&
            (lastContextTokens()?.let { it >= contextLength * settings.autoCompressThreshold } == true)
    }
}
