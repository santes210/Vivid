package com.vivid.app.presentation.messages

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpressiveVideoControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun accessibleSeekButtonsClampAtVideoBoundaries() {
        val seeks = mutableListOf<Long>()
        compose.setContent {
            MaterialTheme {
                ExpressiveVideoControls(5_000, 12_000, true, false, false,
                    false, false, 1f, false, onSeek = { seeks += it }, onPlayPause = {},
                    onMuted = {}, onRepeat = {}, onSpeed = {}, onFill = {}, onShare = {})
            }
        }
        compose.onNodeWithContentDescription(text(R.string.viewer_back_ten)).performClick()
        compose.onNodeWithContentDescription(text(R.string.viewer_forward_ten)).performClick()
        compose.runOnIdle { assertEquals(listOf(0L, 12_000L), seeks) }
    }

    @Test fun unknownDurationDisablesSeekingButKeepsPlayAccessible() {
        compose.setContent {
            MaterialTheme {
                ExpressiveVideoControls(0, 0, false, false, false,
                    false, false, 1f, false, onSeek = {}, onPlayPause = {},
                    onMuted = {}, onRepeat = {}, onSpeed = {}, onFill = {}, onShare = {})
            }
        }
        compose.onNodeWithContentDescription(text(R.string.viewer_seek)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.viewer_back_ten)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.viewer_play)).assertIsEnabled()
    }
}
