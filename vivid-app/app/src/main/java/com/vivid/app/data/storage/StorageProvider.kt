package com.vivid.app.data.storage

/**
 * Abstracción sobre el backend de almacenamiento de archivos.
 *
 * ¿Por qué una interface?
 * ------------------------
 * Hoy usamos Backblaze B2 con credenciales embebidas (modo "direct").
 * Mañana, cuando quieras migrar a Cloud Functions / tu propio server,
 * solo cambias la implementación en [StorageModule] y todo lo demás
 * (ViewModels, UI) sigue funcionando sin tocar nada.
 *
 * Cualquier backend debe poder:
 *   1. Subir un archivo binario dado por un path local.
 *   2. Devolver la URL pública (o firmada) desde donde ExoPlayer
 *      pueda reproducir el video.
 */
interface StorageProvider {

    /**
     * Sube un archivo al bucket y devuelve la URL pública reproducible.
     *
     * @param localFilePath ruta absoluta al archivo en disco del dispositivo
     * @param remoteKey    ruta lógica dentro del bucket, p. ej.
     *                     "reels/{uid}/{timestamp}.mp4"
     * @param onProgress   callback con % (0..100) para actualizar la UI
     * @return URL final del archivo (string)
     */
    suspend fun uploadFile(
        localFilePath: String,
        remoteKey: String,
        onProgress: (Int) -> Unit = {}
    ): String

    /**
     * Borra un archivo del bucket (para eliminar reels).
     * Opcional — algunos backends no lo soportan, debe devolver false.
     */
    suspend fun deleteFile(remoteKey: String): Boolean = false
}
