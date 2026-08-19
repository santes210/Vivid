package com.vivid.app.util

/**
 * Changelog de Vivid, mantenido en código (sin archivos .md) para que además
 * de documentar los cambios se muestre dentro de la app:
 * Ajustes → Acerca de → Novedades.
 *
 * Convención:
 * - La PRIMERA entrada de la lista es la versión más reciente.
 * - Al publicar una versión nueva (ver el esquema MAJOR.MINOR.PATCH-build
 *   documentado en app/build.gradle.kts) se agrega una entrada nueva arriba.
 * - Las notas son frases cortas orientadas al usuario.
 */
data class VividRelease(
    val version: String,
    val notes: List<String>
)

object VividChangelog {

    val releases: List<VividRelease> = listOf(
        VividRelease(
            version = "2.2.0",
            notes = listOf(
                "Reporte de crashes con Firebase Crashlytics y monitoreo de rendimiento con Performance Monitoring (gratis, sin pasos de pago).",
                "Estados de error y reintento consistentes: pantallas de sin conexión, avisos de timeout y botón Reintentar en feed, reels, explorar, búsqueda y chats.",
                "El feed ya no se queda cargando para siempre: si el servidor no responde en 15 s aparece Reintentar.",
                "Corrección de fuga del listener de sesión en la pantalla principal.",
                "Versionado documentado: versión + número de build automático en cada release de GitHub Actions."
            )
        ),
        VividRelease(
            version = "antes de 2.2.0",
            notes = listOf(
                "Feed y reels con caché local: la app abre más rápido y muestra contenido guardado sin conexión.",
                "Stories con editor, música y recortes de video.",
                "Mensajes directos con reacciones, notas de voz y estados de envío."
            )
        )
    )
}
