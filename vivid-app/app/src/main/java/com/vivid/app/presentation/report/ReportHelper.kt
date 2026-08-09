package com.vivid.app.presentation.report

import android.content.Context
import com.vivid.app.util.composeEmail

object ReportHelper {
    // ← Reemplazá esta dirección por tu correo real para recibir reportes
    const val REPORT_EMAIL = "poncho2010santes@gmail.com"

    fun sendPostReport(
        context: Context,
        postId: String,
        username: String,
        caption: String,
        reason: String
    ): Boolean {
        val body = buildString {
            append("Reporte recibido en Vivid")
            append("\n\nPost ID: $postId")
            append("\nUsuario publicado: @$username")
            append("\nCaption (resumen): ${caption.take(300)}")
            append("\nMotivo seleccionado: $reason")
            append("\nFecha: ${System.currentTimeMillis()}")
            append("\n---")
            append("\nPor favor revisá las políticas de convivencia y actuá en consecuencia.")
        }
        return composeEmail(
            context = context,
            to = REPORT_EMAIL,
            subject = "Reporte de publicación en Vivid",
            body = body
        )
    }

    fun sendUserReport(
        context: Context,
        userId: String,
        username: String,
        reason: String
    ): Boolean {
        val body = buildString {
            append("Reporte recibido en Vivid")
            append("\n\nUsuario reportado: @$username")
            append("\nUID: $userId")
            append("\nMotivo seleccionado: $reason")
            append("\nFecha: ${System.currentTimeMillis()}")
            append("\n---")
            append("\nRevisá las políticas y, si corresponde, bloqueá o cerrá la cuenta.")
        }
        return composeEmail(
            context = context,
            to = REPORT_EMAIL,
            subject = "Reporte de usuario en Vivid",
            body = body
        )
    }
}
