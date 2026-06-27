# Vivid - App completa con fixes incluidos

Este ZIP incluye la carpeta completa del proyecto Vivid con los fixes aplicados en el código fuente.

## Fix principal: `b2_upload_file falló (405)`

Causa: la app estaba llamando a `b2_upload_file` usando HTTP `PUT`.
Backblaze B2 Native API espera `POST` para `b2_upload_file`, por eso respondía `405 Method Not Allowed`.

Archivos modificados:

- `vivid-app/app/src/main/java/com/vivid/app/data/storage/BackblazeStorageProvider.kt`
- `vivid-app/app/src/main/java/com/vivid/app/data/storage/CloudFunctionsStorageProvider.kt`

Cambios:

- `.put(...)` -> `.post(...)`
- `X-Bz-File-Name` ahora va URL-encoded
- Se agrega `X-Bz-Content-Sha1` también en CloudFunctionsStorageProvider
- Se evita `file.readBytes()` para videos grandes; ahora se usa stream/asRequestBody

## Fix de build

También se incluye el arreglo de resolución de KSP en:

- `vivid-app/settings.gradle.kts`

Y ajuste de memoria en:

- `vivid-app/gradle.properties`

## Compilar

```bash
cd vivid-app
./gradlew assembleDebug
```

## Patches incluidos

- `b2-upload-405-fix.patch`
- `build-fix-2026-06-23.patch`

Nota: el código contiene credenciales embebidas de Backblaze. Si el repo/APK es público, conviene rotar esas claves cuando puedas.
