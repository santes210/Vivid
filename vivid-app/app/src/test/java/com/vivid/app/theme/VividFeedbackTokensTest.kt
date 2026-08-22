package com.vivid.app.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VividFeedbackTokensTest {

    @Test
    fun `dialog corner is the 28dp expressive radius`() {
        assertEquals(28f, VividFeedbackTokens.DialogCorner.value)
    }

    @Test
    fun `snackbar max width fits a tablet column`() {
        assertEquals(600f, VividFeedbackTokens.SnackbarMaxWidth.value)
    }

    @Test
    fun `snackbar and dialog share the Dialog shape`() {
        assertSame(VividExpressiveShapes.Dialog, VividFeedbackTokens.DialogShape)
        assertSame(VividExpressiveShapes.Snackbar, VividFeedbackTokens.SnackbarShape)
        assertSame(VividExpressiveShapes.Dialog, VividExpressiveShapes.Snackbar)
    }
}
