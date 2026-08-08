package com.vivid.app.presentation.report

import android.content.Context
import android.content.Intent
import android.net.Uri

object ReportHelper {
    // ← Reemplazá esta dirección por tu correo real para recibir reportes
    const val REPORT_EMAIL = "poncho2010santes@gmail.com"

    fun sendPostReport(
        context: Context,
        postId: String,
        username: String,
        caption: String,
        reason: String
    ) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de publicación en Vivid")
            putExtra(
                Intent.EXTRA_TEXT,
                buildString {
                    append("Reporte recibido en Vivid")
                    append("\n\nPost ID: $postId")
                    append("\nUsuario publicado: @$username")
                    append("\nCaption (resumen): ${caption.take(300)}")
                    append("\nMotivo seleccionado: $reason")
                    append("\nFecha: ${System.currentTimeMillis()}")
                    append("\n---")
                    append("\nPor favor revisá las políticas de convivencia y actuá en consecuencia.")
                }
            )
        }
        context.startActivity(Intent.createChooser(intent, "Enviar reporte"))
    }

    fun sendUserReport(
        context: Context,
        userId: String,
        username: String,
        reason: String
    ) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de usuario en Vivid")
            putExtra(
                Intent.EXTRA_TEXT,
                buildString {
                    append("Reporte recibido en Vivid")
                    append("\n\nUsuario reportado: @$username")
                    append("\nUID: $userId")
                    append("\nMotivo seleccionado: $reason")
                    append("\nFecha: ${System.currentTimeMillis()}")
                    append("\n---")
                    append("\nRevisá las políticas y, si corresponde, bloqueá o cerrá la cuenta.")
                }
            )
        }
        context.startActivity(Intent.createChooser(intent, "Enviar reporte"))
    }
}
