package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.buildCompressPrompt
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.collectInjections
import me.rerere.rikkahub.data.datastore.CompressionPromptRole
import me.rerere.rikkahub.data.datastore.CompressionRequestSettings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.ai.transformers.transformMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionRequestTest {
    private val options = CompressionRequestSettings()
    private val assistant = Assistant(systemPrompt = "assistant", enableMemory = true)

    @Test
    fun `user role separates source history from complete editable instructions`() {
        val messages = listOf(UIMessage.system("system"), UIMessage.user("history"))
        val prompt = buildCompressPrompt("extra", "en", true, "main", "mid-turn")
        val result = messages.withCompressionPrompt(prompt, CompressionPromptRole.USER)
        assertEquals("system", result.first().toText())
        assertTrue(result[1].toText().startsWith("<conversation_history type=\"source_material\">"))
        assertTrue(result[1].toText().contains("<message index=\"1\" role=\"user\">\nhistory\n</message>"))
        assertEquals(MessageRole.USER, result.last().role)
        assertEquals("main\n\nmid-turn\n\nextra", result.last().toText())
    }

    @Test
    fun `system role combines every system message with compression instructions at the start`() {
        val first = UIMessage.user("history")
        val second = UIMessage.assistant("reply")
        val messages = listOf(UIMessage.system("assistant"), first, UIMessage.system("injection"), second)
        val result = messages.withCompressionPrompt("compress {{literal}}", CompressionPromptRole.SYSTEM)
        assertEquals(1, result.count { it.role == MessageRole.SYSTEM })
        assertEquals("assistant\ninjection\ncompress {{literal}}", result.first().toText())
        assertEquals(MessageRole.USER, result.last().role)
        assertTrue(result.last().toText().contains("role=\"user\">\nhistory\n</message>"))
        assertTrue(result.last().toText().contains("role=\"assistant\">\nreply\n</message>"))
        assertFalse(result.last().toText().contains("compress {{literal}}"))
        assertEquals(4, messages.size)
    }

    @Test
    fun `system role works with all optional context disabled`() {
        val history = UIMessage.user("history")
        val result = listOf(history).withCompressionPrompt("summary only", CompressionPromptRole.SYSTEM)
        assertEquals(listOf(MessageRole.SYSTEM, MessageRole.USER), result.map { it.role })
        assertEquals("summary only", result.first().toText())
        assertTrue(result.last().toText().contains("\nhistory\n"))
        assertTrue(result.last().toText().endsWith("</conversation_history>"))
        assertEquals("history", history.toText())
    }

    @Test
    fun `unfinished assistant prose is source material and never an assistant prefill`() {
        val unfinished = UIMessage.assistant("The evening wind lifted her hair, revealing")
        val input = listOf(UIMessage.user("Continue the story"), unfinished)
        for (role in CompressionPromptRole.entries) {
            val result = input.withCompressionPrompt("Output only a plot summary", role)

            assertEquals(MessageRole.USER, result.last().role)
            assertFalse(result.any { it.role == MessageRole.ASSISTANT })
            val source = result.single { it.toText().startsWith("<conversation_history") }.toText()
            assertTrue(source.contains("role=\"assistant\">\n${unfinished.toText()}\n</message>"))
            assertTrue(source.endsWith("</conversation_history>"))
            assertFalse(source.contains("Output only a plot summary"))
        }
        assertSame(unfinished, input.last())
    }

    @Test
    fun `source history preserves media and tool results without live tool calls`() {
        val image = UIMessagePart.Image(url = "https://example.com/image.png")
        val tool = UIMessagePart.Tool("call", "search", "query", listOf(UIMessagePart.Text("found"), image))
        val reasoning = UIMessagePart.Reasoning("thought")
        val message = UIMessage.assistant("").copy(parts = listOf(reasoning, tool))
        val result = listOf(message).withCompressionPrompt("summarize", CompressionPromptRole.SYSTEM)
        val source = result.last()

        assertEquals(MessageRole.USER, source.role)
        assertTrue(source.parts.contains(image))
        assertTrue(source.toText().contains("found"))
        assertTrue(source.toText().contains("<reasoning>\nthought\n</reasoning>"))
        assertFalse(source.parts.any { it is UIMessagePart.Tool || it is UIMessagePart.Reasoning })
        assertSame(reasoning, message.parts.first())
        assertSame(tool, message.parts.last())
    }

    @Test
    fun `assistant prompt exclusion also excludes conversation system override`() {
        val configured = assistant.copy(allowConversationSystemPrompt = true)
        assertEquals("override\nmemory\ntools", buildRequestSystemPrompt(configured, "override", "memory", listOf("tools"), options))
        assertEquals("\nmemory\ntools", buildRequestSystemPrompt(configured, "override", "memory", listOf("tools"), options.copy(includeAssistantPrompt = false)))
        assertEquals("assistant", buildRequestSystemPrompt(assistant, null, "memory", listOf("tools"), options.copy(includeMemory = false, includeTools = false)))
        assertEquals("", buildRequestSystemPrompt(assistant, null, "memory", listOf("tools"), options.copy(includeAssistantPrompt = false, includeMemory = false, includeTools = false)))
    }

    @Test
    fun `mode injection exclusion does not mutate the chatting assistant`() {
        val injection = PromptInjection.ModeInjection(content = "injected")
        val configured = assistant.copy(modeInjectionIds = setOf(injection.id))
        val filtered = configured.forCompression(options.copy(includeModeInjections = false))
        assertEquals(1, collectInjections(emptyList(), configured, listOf(injection), emptyList()).size)
        assertTrue(collectInjections(emptyList(), filtered, listOf(injection), emptyList()).isEmpty())
        assertEquals(setOf(injection.id), configured.modeInjectionIds)
        assertSame(configured, configured.forCompression(null))
    }

    @Test
    fun `time reminders are excluded only from compression when switched off`() {
        assertFalse(TimeReminderTransformer.includedInCompression(options.copy(includeTimeReminders = false)))
        assertTrue(TimeReminderTransformer.includedInCompression(null))
        assertTrue(PromptInjectionTransformer.includedInCompression(options))
    }

    @Test
    fun `lorebook and mode switches independently remove injections at every position`() {
        for (position in InjectionPosition.entries) {
            val mode = PromptInjection.ModeInjection(content = "mode", position = position)
            val lorebook = Lorebook(entries = listOf(PromptInjection.RegexInjection(content = "lore", constantActive = true, position = position)))
            val configured = assistant.copy(modeInjectionIds = setOf(mode.id), lorebookIds = setOf(lorebook.id))
            val history = listOf(UIMessage.user("history"))
            fun injected(value: CompressionRequestSettings) = transformMessages(
                history, configured.forCompression(value), listOf(mode), listOf(lorebook)
            ).joinToString("\n") { it.toText() }
            assertTrue(injected(options).contains("mode"))
            assertTrue(injected(options).contains("lore"))
            assertFalse(injected(options.copy(includeModeInjections = false)).contains("mode"))
            assertTrue(injected(options.copy(includeModeInjections = false)).contains("lore"))
            assertFalse(injected(options.copy(includeLorebooks = false)).contains("lore"))
            assertTrue(injected(options.copy(includeLorebooks = false)).contains("mode"))
            assertEquals("history", injected(options.copy(includeModeInjections = false, includeLorebooks = false)))
        }
    }

    @Test
    fun `normal chat and default compression keep the same system prefix`() {
        assertEquals(
            buildRequestSystemPrompt(assistant, null, "memory", listOf("tools"), null),
            buildRequestSystemPrompt(assistant, null, "memory", listOf("tools"), options),
        )
    }

    @Test
    fun `tool free requests retain call inputs outputs and media without changing local history`() {
        val output = UIMessagePart.Text("result data")
        val image = UIMessagePart.Image(url = "https://example.com/result.png")
        val tool = UIMessagePart.Tool("call", "search", "{query}", listOf(output, image))
        val history = UIMessage.assistant("before").copy(parts = listOf(tool))
        val result = listOf(history).withToolHistoryAsText().single()
        assertTrue(result.getTools().isEmpty())
        assertTrue(result.toText().contains("search"))
        assertTrue(result.toText().contains("{query}"))
        assertTrue(result.parts.contains(output))
        assertTrue(result.parts.contains(image))
        assertEquals(listOf(tool), history.parts)
    }
}
