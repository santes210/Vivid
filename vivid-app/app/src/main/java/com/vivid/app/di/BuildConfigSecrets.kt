package com.vivid.app.di

/**
 * Configuracion con credenciales embebidas.
 *
 * !! MODO INSEGURO - LEE ESTO !!
 * --------------------------------
 * Las claves de B2 estan en este archivo. Esto significa que si
 * subes el codigo a un repositorio publico, las claves quedan
 * expuestas para siempre (Google las indexa).
 *
 * El usuario decidio aceptar este riesgo porque:
 *   - No puede instalar Android SDK localmente (usa telefono)
 *   - Necesita que GitHub Actions compile el APK con las credenciales listas
 *   - Su repo lleva 2-4 dias sin visitas, considera el riesgo bajo
 *
 * Para migrar a MODO SEGURO en el futuro:
 *   1. Despliega la Cloud Function (las claves quedan en Firebase)
 *   2. Borra las constantes de aqui
 *   3. Cambia el build.gradle.kts para leer de env vars / local.properties
 */
object BuildConfigSecrets {

    // BACKBLAZE B2 - claves embebidas
    const val B2_ACCOUNT_ID       = "4482642d8bb0"
    const val B2_KEY_ID           = "0044482642d8bb00000000005"
    const val B2_APPLICATION_KEY  = "K0043ske+MzlEoRWXQtmJ18opgnipXQ"
    const val B2_BUCKET_ID        = "94c488b2a624f22d98eb0b10"
    const val B2_BUCKET_NAME      = "VividGrem"

    // CLOUD FUNCTION - URL base (PUBLICA, OK tenerla en el codigo)
    // Tras firebase deploy --only functions pega aqui la URL
    const val CF_BASE_URL = "https://us-central1-TU_PROYECTO.cloudfunctions.net"

    // HELPERS - expone los valores como tipos correctos para Gradle
    const val B2_KEY_ID_GRADLE           = B2_KEY_ID
    const val B2_APPLICATION_KEY_GRADLE  = B2_APPLICATION_KEY
    const val B2_BUCKET_ID_GRADLE        = B2_BUCKET_ID
    const val B2_BUCKET_NAME_GRADLE      = B2_BUCKET_NAME
    const val CF_BASE_URL_GRADLE         = CF_BASE_URL
}
