package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.Json
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class CompressionSettingsTest {
    @Test
    fun `old backups receive compression defaults`() {
        val value = Json.decodeFromString<CompressionSettings>("{}")
        assertEquals(50, value.messageThreshold)
        assertEquals(20, value.manualRange.earliestCount)
        assertEquals(20, value.manualRange.keepRecentCount)
        assertEquals(value.manualRange, value.automaticRange)
        assertEquals(AutoCompressionTrigger.TOKEN_RATIO, value.trigger)
        assertEquals(CompressionRequestSettings(), value.request)
        assertEquals(CompressionPromptRole.USER, value.request.promptRole)
        assertNull(value.modelId)
        assertEquals(ReasoningLevel.AUTO, value.reasoningLevel)
    }

    @Test
    fun `compression model and reasoning survive persistence without changing prompts or ranges`() {
        val settings = CompressionSettings(modelId = Uuid.random(), reasoningLevel = ReasoningLevel.HIGH, prompt = "custom")
        val restored = Json.decodeFromString<CompressionSettings>(Json.encodeToString(settings))

        assertEquals(settings, restored)
        assertEquals(settings.modelId, restored.normalized().modelId)
        assertEquals(ReasoningLevel.HIGH, restored.normalized().reasoningLevel)
    }

    @Test
    fun `compression model is independent of chat and fast models and does not silently fall back`() {
        val chat = Model(modelId = "chat")
        val summary = Model(modelId = "summary")
        val settings = Settings(
            chatModelId = chat.id,
            fastModelId = chat.id,
            providers = listOf(ProviderSetting.OpenAI(models = listOf(chat, summary))),
            compression = CompressionSettings(modelId = summary.id),
        )

        assertEquals(summary, settings.getCompressionModel())
        assertEquals(summary, settings.copy(chatModelId = Uuid.random()).getCompressionModel())
        assertNull(settings.copy(compression = settings.compression.copy(modelId = null)).getCompressionModel())
        assertNull(settings.copy(compression = settings.compression.copy(modelId = Uuid.random())).getCompressionModel())
        assertNull(settings.copy(providers = listOf(ProviderSetting.OpenAI(models = listOf(chat)))).getCompressionModel())
    }

    @Test
    fun `request options persist independently of prompts and ranges`() {
        val request = CompressionRequestSettings(
            promptRole = CompressionPromptRole.SYSTEM,
            includeAssistantPrompt = false,
            includeModeInjections = false,
            includeLorebooks = false,
            includeMemory = false,
            includeTools = false,
            includeTimeReminders = false,
            includeWorkspace = false,
            includeMessageTemplate = false,
        )
        val settings = CompressionSettings(request = request)
        assertEquals(settings, Json.decodeFromString<CompressionSettings>(Json.encodeToString(settings)))
        assertEquals(request, settings.normalized().request)
        assertEquals(CompressionRange(), settings.manualRange)
        assertEquals(CompressionRange(), settings.automaticRange)
    }

    @Test
    fun `switching modes keeps both numbers and independent manual automatic ranges`() {
        val range = CompressionRange(earliestCount = 12, keepRecentCount = 8)
        val settings = CompressionSettings(manualRange = range)
        assertEquals(12, range.copy(mode = CompressionRangeMode.EARLIEST).compressionCount(50))
        assertEquals(42, range.copy(mode = CompressionRangeMode.KEEP_RECENT).compressionCount(50))
        assertEquals(20, settings.automaticRange.keepRecentCount)
        assertEquals(settings, Json.decodeFromString<CompressionSettings>(Json.encodeToString(settings)))
    }

    @Test
    fun `invalid persisted numbers normalize without changing editable prompts`() {
        val settings = CompressionSettings(
            prompt = "", midTurnPrompt = "", messageThreshold = -1,
            manualRange = CompressionRange(earliestCount = 0, keepRecentCount = -1),
        ).normalized()
        assertEquals(1, settings.messageThreshold)
        assertEquals(1, settings.manualRange.earliestCount)
        assertEquals(0, settings.manualRange.keepRecentCount)
        assertEquals("", settings.prompt)
        assertEquals("", settings.midTurnPrompt)
    }
}
