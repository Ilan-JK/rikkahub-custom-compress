package me.rerere.rikkahub.data.ai

import android.os.SystemClock
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/** Keep a small, short-lived local record. Credentials, custom headers/body and raw media are excluded. */
internal object RequestInspection {
    private data class Entry(val time: Long, val steps: List<String>)
    private val entries = linkedMapOf<Uuid, Entry>()
    private const val RetentionMs = 30 * 60 * 1000L

    @Synchronized
    fun record(id: Uuid, step: StepRequest) {
        val details = describeRequest(step)
        val previous = read(id)
        entries.remove(id)
        entries[id] = Entry(SystemClock.elapsedRealtime(), (previous + details).takeLast(16))
        while (entries.size > 12) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun read(id: Uuid): List<String> {
        val now = SystemClock.elapsedRealtime()
        entries.entries.removeAll { now - it.value.time >= RetentionMs }
        return entries[id]?.steps.orEmpty()
    }
}

internal fun describeRequest(step: StepRequest): String = buildString {
    val params = step.params
    appendLine("Model: ${params.model.modelId}")
    appendLine("Temperature: ${params.temperature} | Top P: ${params.topP} | Max tokens: ${params.maxTokens} | Reasoning: ${params.reasoningLevel}")
    if (params.tools.isNotEmpty()) {
        for (tool in params.tools) {
            appendLine("Tool: ${tool.name} — ${tool.description}")
            runCatching { tool.parameters() }.getOrNull()?.let { appendLine(it) }
        }
    }
    for (message in step.messages) {
        appendLine()
        appendLine("[${message.role}]")
        for (part in message.parts) {
            when (part) {
                is UIMessagePart.Text -> appendLine(part.text)
                is UIMessagePart.Image, is UIMessagePart.Video, is UIMessagePart.Audio, is UIMessagePart.Document ->
                    appendLine("[${part::class.simpleName}]")
                else -> appendLine(part)
            }
        }
    }
}.take(96_000)
