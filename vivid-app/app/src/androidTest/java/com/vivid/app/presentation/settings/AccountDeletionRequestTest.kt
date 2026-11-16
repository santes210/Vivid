package com.vivid.app.presentation.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountDeletionRequestTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun cancelDoesNotOpenEmailOrCopyData() {
        var opened = 0
        var copied = 0
        compose.setContent {
            MaterialTheme {
                AccountDeletionRequest(true, { opened++; true }, { copied++ })
            }
        }
        compose.onNodeWithText(text(R.string.account_deletion_title)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_explanation)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.account_deletion_cancel)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_request_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opened); assertEquals(0, copied) }
    }

    @Test
    fun failedEmailLaunchKeepsDialogAndOffersCopy() {
        var copied = 0
        compose.setContent {
            MaterialTheme {
                AccountDeletionRequest(true, { false }, { copied++ })
            }
        }
        compose.onNodeWithText(text(R.string.account_deletion_title)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_open_email)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_no_email)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.account_deletion_copy)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, copied) }
    }

    @Test
    fun successfulEmailLaunchClosesDialogWithoutClaimingDeletion() {
        var opened = 0
        compose.setContent {
            MaterialTheme {
                AccountDeletionRequest(true, { opened++; true }, {})
            }
        }
        compose.onNodeWithText(text(R.string.account_deletion_title)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_open_email)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_request_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun signedOutUserCannotSendOrCopyAnAccountRequest() {
        compose.setContent {
            MaterialTheme {
                AccountDeletionRequest(false, { error("Must not open email") }, { error("Must not copy") })
            }
        }
        compose.onNodeWithText(text(R.string.account_deletion_title)).performClick()
        compose.onNodeWithText(text(R.string.account_deletion_sign_in)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.account_deletion_open_email)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.account_deletion_copy)).assertIsNotEnabled()
    }
}
