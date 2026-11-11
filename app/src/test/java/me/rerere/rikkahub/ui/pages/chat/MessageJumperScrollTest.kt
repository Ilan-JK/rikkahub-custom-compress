package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageJumperScrollTest {
    @Test
    fun `opening an idle conversation does not show the jumper`() = runTest {
        val scrolling = MutableStateFlow(false)
        var recent = false
        backgroundScope.launch { observeRecentScroll(scrolling) { recent = it } }
        runCurrent()

        advanceTimeBy(2000)
        runCurrent()

        assertFalse(recent)
    }

    @Test
    fun `long scroll keeps the full display interval after it stops`() = runTest {
        val scrolling = MutableStateFlow(false)
        var recent = false
        backgroundScope.launch { observeRecentScroll(scrolling) { recent = it } }
        runCurrent()

        scrolling.value = true
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertTrue(recent)

        scrolling.value = false
        runCurrent()
        advanceTimeBy(1499)
        runCurrent()
        assertTrue(recent)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(recent)
    }

    @Test
    fun `brief scroll shows the jumper after release`() = runTest {
        val scrolling = MutableStateFlow(false)
        var recent = false
        backgroundScope.launch { observeRecentScroll(scrolling) { recent = it } }
        runCurrent()

        scrolling.value = true
        runCurrent()
        advanceTimeBy(100)
        scrolling.value = false
        runCurrent()

        assertTrue(recent)
        advanceTimeBy(1500)
        runCurrent()
        assertFalse(recent)
    }

    @Test
    fun `scrolling again cancels the old hide timer and restarts it on release`() = runTest {
        val scrolling = MutableStateFlow(true)
        var recent = false
        backgroundScope.launch { observeRecentScroll(scrolling) { recent = it } }
        runCurrent()

        scrolling.value = false
        runCurrent()
        advanceTimeBy(1000)
        scrolling.value = true
        runCurrent()
        advanceTimeBy(2000)
        runCurrent()
        assertTrue(recent)

        scrolling.value = false
        runCurrent()
        advanceTimeBy(1499)
        runCurrent()
        assertTrue(recent)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(recent)
    }
}
