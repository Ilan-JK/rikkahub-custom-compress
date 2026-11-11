package me.rerere.rikkahub.ui.pages.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal suspend fun observeRecentScroll(
    scrolling: Flow<Boolean>,
    onRecentScrollChange: (Boolean) -> Unit,
) {
    scrolling.distinctUntilChanged().collectLatest { inProgress ->
        if (inProgress) {
            onRecentScrollChange(true)
        } else {
            // Keep the jumper available after scrolling stops, even after a long fling.
            delay(1500)
            onRecentScrollChange(false)
        }
    }
}
