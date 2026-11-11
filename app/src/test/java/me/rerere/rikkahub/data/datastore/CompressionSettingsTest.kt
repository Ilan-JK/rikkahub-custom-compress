package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionSettingsTest {
    @Test
    fun `old backups receive compression defaults`() {
        val value = Json.decodeFromString<CompressionSettings>("{}")
        assertEquals(50, value.messageThreshold)
        assertEquals(20, value.manualRange.earliestCount)
        assertEquals(20, value.manualRange.keepRecentCount)
        assertEquals(value.manualRange, value.automaticRange)
        assertEquals(AutoCompressionTrigger.TOKEN_RATIO, value.trigger)
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
