package com.vivid.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Abre un enlace web (http/https) en el navegador por defecto.
 *
 * A partir de Android 11 (API 30) es necesario declarar <queries> en el
 * AndroidManifest para que resolveActivity() no devuelva null. Aun así,
 * intentamos lanzar directamente y capturamos la excepción por si no hay
 * ninguna app que pueda manejar el enlace.
 *
 * @return true si se pudo abrir, false si no hay navegador/disponible.
 */
fun openUrl(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return launchExternalIntent(context, intent)
}

/**
 * Redacta un correo electrónico en la app de correo del usuario.
 *
 * @param to destinatario principal.
 * @param subject asunto del correo.
 * @param body cuerpo del correo (opcional).
 * @return true si se pudo abrir una app de correo, false en caso contrario.
 */
fun composeEmail(
    context: Context,
    to: String,
    subject: String,
    body: String? = null
): Boolean {
    val mailto = buildString {
        append("mailto:")
        append(Uri.encode(to))
        append("?subject=").append(Uri.encode(subject))
        if (!body.isNullOrEmpty()) {
            append("&body=").append(Uri.encode(body))
        }
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Compatibilidad con clientes de correo que ignoran el data mailto
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (body != null) putExtra(Intent.EXTRA_TEXT, body)
    }
    return launchExternalIntent(context, intent)
}

/**
 * Lanza un intent externo de forma segura, usando un chooser como respaldo.
 *
 * FLAG_ACTIVITY_NEW_TASK es obligatorio al lanzar desde un contexto que no
 * es una Activity (por ejemplo un Composable).
 */
fun launchExternalIntent(context: Context, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        try {
            val chooser = Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e2: Exception) {
            false
        }
    }
}
