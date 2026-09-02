package com.jiahan.smartcamera.common

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behaviour, not goldens -- this file deliberately does not extend `BaseScreenshotTest`, since
 * there is nothing here worth a picture. It borrows the same Robolectric configuration because
 * `ScrollToTopEffect` needs a laid-out `LazyColumn` to scroll, which means a real composition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel5
)
class ScrollToTopEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var consumedCount = 0
    private lateinit var listState: LazyListState

    private fun setContent(scrollToTop: () -> Long?, hasItems: () -> Boolean) {
        composeRule.setContent {
            listState = rememberLazyListState()
            ScrollToTopEffect(
                scrollToTop = scrollToTop(),
                listState = listState,
                hasItems = hasItems(),
                onConsumed = { consumedCount++ }
            )
            LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                items((1..50).toList()) { index ->
                    Text(text = "item $index", modifier = Modifier.height(40.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollAwayFromTop() {
        composeRule.runOnIdle { runBlocking { listState.scrollToItem(20) } }
        composeRule.waitForIdle()
    }

    /**
     * The regression this pins: a tab re-tap can land before the feed has rows, and `hasItems` is
     * read inside the effect. Keyed on `scrollToTop` alone the request was dropped for good --
     * `consumedCount` stayed 0 here and the list never returned to the top.
     */
    @Test
    fun `runs a scroll requested before the list had items, once they arrive`() {
        var hasItems by mutableStateOf(false)
        setContent(scrollToTop = { 1L }) { hasItems }
        scrollAwayFromTop()

        assertEquals(0, consumedCount)

        hasItems = true
        composeRule.waitForIdle()

        assertEquals(1, consumedCount)
        assertEquals(0, composeRule.runOnIdle { listState.firstVisibleItemIndex })
    }

    @Test
    fun `scrolls to the top and consumes when items are already present`() {
        var scrollToTop by mutableStateOf<Long?>(null)
        setContent(scrollToTop = { scrollToTop }) { true }
        scrollAwayFromTop()

        // The tab re-tap: MainViewModel stamps a timestamp, which is what the effect keys on.
        scrollToTop = 1L
        composeRule.waitForIdle()

        assertEquals(1, consumedCount)
        assertEquals(0, composeRule.runOnIdle { listState.firstVisibleItemIndex })
    }

    @Test
    fun `does nothing while no scroll is pending`() {
        var hasItems by mutableStateOf(false)
        setContent(scrollToTop = { null }) { hasItems }
        scrollAwayFromTop()

        hasItems = true
        composeRule.waitForIdle()

        assertEquals(0, consumedCount)
        assertEquals(20, composeRule.runOnIdle { listState.firstVisibleItemIndex })
    }
}