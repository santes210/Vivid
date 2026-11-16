package com.vivid.app.presentation.messages

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImageZoomControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun zoomButtonsProvideAnAlternativeToPinching() {
        var zooms = 0
        var resets = 0
        compose.setContent {
            MaterialTheme {
                ImageZoomControls(1f, true, onZoomIn = { zooms++ }, onZoomOut = {}, onReset = { resets++ })
            }
        }
        compose.onNodeWithContentDescription(text(R.string.image_zoom_out)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.image_zoom_in)).performClick()
        compose.onNodeWithContentDescription(text(R.string.image_zoom_reset)).performClick()
        compose.runOnIdle { assertEquals(1, zooms); assertEquals(1, resets) }
    }

    @Test fun maximumZoomDisablesFurtherEnlargement() {
        compose.setContent {
            MaterialTheme { ImageZoomControls(4f, true, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription(text(R.string.image_zoom_in)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.image_zoom_out)).assertIsEnabled()
    }

    @Test fun controlsWaitUntilTheImageHasLoaded() {
        compose.setContent {
            MaterialTheme { ImageZoomControls(1f, false, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription(text(R.string.image_zoom_in)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.image_zoom_reset)).assertIsNotEnabled()
    }
}
