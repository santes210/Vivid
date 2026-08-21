package com.vivid.shared.repository

/**
 * Interfaz para el proveedor de almacenamiento de archivos (imágenes, videos).
 * La implementación usa Backblaze B2 vía Cloudflare Worker como broker.
 *
 * Las URLs generadas son firmadas temporalmente; la plataforma decide
 * cómo manejar la expiración y re-firmado.
 */
interface StorageProvider {

    /**
     * Sube un archivo al almacenamiento remoto.
     * @param filePath Ruta local del archivo a subir.
     * @param storageKey Clave/ruta en el bucket (ej: "posts/uid/timestamp.jpg").
     * @return URL pública o firmada del archivo subido.
     */
    suspend fun uploadFile(filePath: String, storageKey: String): String

    /**
     * Elimina un archivo del almacenamiento remoto.
     * @param storageKey Clave del archivo a eliminar.
     */
    suspend fun deleteFile(storageKey: String)

    /**
     * Obtiene una URL firmada para un archivo existente.
     * @param storageKey Clave del archivo.
     * @return URL firmada temporalmente.
     */
    suspend fun getSignedUrl(storageKey: String): String
}
