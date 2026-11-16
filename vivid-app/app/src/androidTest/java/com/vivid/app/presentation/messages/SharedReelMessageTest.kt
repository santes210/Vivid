package com.vivid.app.presentation.messages

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SharedReelMessageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedReelOnlyOpensAfterTapAndUsesInChatCallback() {
        var requestedId: String? = null
        compose.setContent {
            MaterialTheme {
                MessageBubble(
                    message = Message(id = "m1", text = "Mira este reel en Vivid:\nvivid://reel/r1"),
                    isMine = false,
                    isGroupStart = true,
                    isGroupEnd = true,
                    onOpenReel = { requestedId = it }
                )
            }
        }
        compose.runOnIdle { assertNull(requestedId) }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.chat_reel_card)
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("r1", requestedId) }
    }
}
