package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.ui.ContextSummaryMode
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_MID_TURN_COMPRESS_PROMPT
import kotlin.uuid.Uuid

@Serializable
enum class CompressionRangeMode { EARLIEST, KEEP_RECENT }

@Serializable
enum class AutoCompressionTrigger { TOKEN_RATIO, MESSAGE_COUNT }

@Serializable
enum class CompressionPromptRole { USER, SYSTEM }

@Serializable
data class CompressionRequestSettings(
    val promptRole: CompressionPromptRole = CompressionPromptRole.USER,
    val includeAssistantPrompt: Boolean = true,
    val includeModeInjections: Boolean = true,
    val includeLorebooks: Boolean = true,
    val includeMemory: Boolean = true,
    val includeTools: Boolean = true,
    val includeTimeReminders: Boolean = true,
    val includeWorkspace: Boolean = true,
    val includeMessageTemplate: Boolean = true,
)

@Serializable
data class CompressionRange(
    val mode: CompressionRangeMode = CompressionRangeMode.KEEP_RECENT,
    val earliestCount: Int = 20,
    val keepRecentCount: Int = 20,
) {
    fun compressionCount(available: Int): Int = when (mode) {
        CompressionRangeMode.EARLIEST -> earliestCount.coerceAtLeast(1).coerceAtMost(available)
        CompressionRangeMode.KEEP_RECENT -> (available - keepRecentCount.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun normalized() = copy(
        earliestCount = earliestCount.coerceIn(1, 99999),
        keepRecentCount = keepRecentCount.coerceIn(0, 99999),
    )
}

@Serializable
data class CompressionSettings(
    val modelId: Uuid? = null,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val prompt: String = DEFAULT_COMPRESS_PROMPT,
    val midTurnPrompt: String = DEFAULT_MID_TURN_COMPRESS_PROMPT,
    val manualRange: CompressionRange = CompressionRange(),
    val automaticRange: CompressionRange = CompressionRange(),
    val trigger: AutoCompressionTrigger = AutoCompressionTrigger.TOKEN_RATIO,
    val messageThreshold: Int = 50,
    val summaryMode: ContextSummaryMode = ContextSummaryMode.LATEST,
    val request: CompressionRequestSettings = CompressionRequestSettings(),
) {
    fun normalized() = copy(
        manualRange = manualRange.normalized(),
        automaticRange = automaticRange.normalized(),
        messageThreshold = messageThreshold.coerceIn(1, 99999),
    )
}
