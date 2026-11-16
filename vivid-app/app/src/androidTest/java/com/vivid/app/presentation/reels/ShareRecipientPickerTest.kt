package com.vivid.app.presentation.reels

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShareRecipientPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectingSeveralPeopleDoesNotSendAnything() {
        var chosen = emptySet<String>()
        compose.setContent {
            var selected by remember { mutableStateOf(emptySet<String>()) }
            MaterialTheme {
                ShareRecipientPicker(
                    listOf(ShareRecipient("a", "u1", "Ana"), ShareRecipient("b", "u2", "Luis")),
                    selected, emptyMap()
                ) { id ->
                    selected = if (id in selected) selected - id else selected + id
                    chosen = selected
                }
            }
        }
        compose.onNodeWithText("Ana").performClick().assertIsOn()
        compose.onNodeWithText("Luis").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(setOf("a", "b"), chosen) }
        compose.onNodeWithText("Ana").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(setOf("b"), chosen) }
    }

    @Test fun successfulRecipientCannotBeAccidentallySelectedAgain() {
        compose.setContent {
            MaterialTheme {
                ShareRecipientPicker(listOf(ShareRecipient("a", "u1", "Ana")), setOf("a"),
                    mapOf("a" to ReelShareStatus.SENT)) { error("Must not toggle a successful send") }
            }
        }
        compose.onNodeWithText("Ana").assertIsNotEnabled()
    }
}
