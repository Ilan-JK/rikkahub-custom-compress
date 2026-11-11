package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionPromptTest {
    @Test
    fun `custom prompt replaces defaults without hidden summary instructions`() {
        assertEquals("custom Chinese\n\nextra", buildCompressPrompt("extra", "Chinese", prompt = "custom {{locale}}"))
    }

    @Test
    fun `mid turn instructions are editable and removable`() {
        assertEquals("custom\n\nresume Chinese", buildCompressPrompt("", "Chinese", true, "custom", "resume {{locale}}"))
        assertEquals("custom", buildCompressPrompt("", "Chinese", true, "custom", ""))
    }
}
