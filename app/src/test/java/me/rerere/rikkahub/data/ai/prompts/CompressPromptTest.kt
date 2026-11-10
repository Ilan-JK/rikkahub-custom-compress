package me.rerere.rikkahub.data.ai.prompts

import org.junit.Assert.assertEquals
import org.junit.Test

class CompressPromptTest {
    @Test
    fun `empty custom prompt does not restore the built in instructions`() {
        assertEquals("", buildCompressPrompt(prompt = "", additionalPrompt = "", locale = "English"))
    }

    @Test
    fun `custom prompt fully replaces the built in instructions`() {
        val prompt = buildCompressPrompt(
            prompt = "Summarize in three sentences.",
            additionalPrompt = "",
            locale = "English",
        )

        assertEquals("Summarize in three sentences.", prompt)
    }

    @Test
    fun `locale is replaced without interpreting other braces`() {
        val prompt = buildCompressPrompt(
            prompt = """Use {locale}. Return {"goals": []}. Keep {{ message }} and {content}.""",
            additionalPrompt = "",
            locale = "English",
        )

        assertEquals("""Use English. Return {"goals": []}. Keep {{ message }} and {content}.""", prompt)
    }

    @Test
    fun `additional instructions are appended verbatim as before`() {
        val prompt = buildCompressPrompt(
            prompt = "Use {locale}.",
            additionalPrompt = "Keep file paths and {locale}.",
            locale = "English",
        )

        assertEquals("Use English.\n\nAdditional instructions from user: Keep file paths and {locale}.", prompt)
    }

    @Test
    fun `blank additional instructions are omitted`() {
        assertEquals(
            DEFAULT_COMPRESS_PROMPT.replace("{locale}", "English"),
            buildCompressPrompt(
                prompt = DEFAULT_COMPRESS_PROMPT,
                additionalPrompt = " \n\t",
                locale = "English",
            ),
        )
    }
}
