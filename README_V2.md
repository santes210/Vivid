# Vivid v2.1 - con credenciales embebidas

ZIP con TODO el proyecto Vivid + las nuevas features + credenciales B2 dentro del codigo.

## Como usar

1. Descomprime
2. Sube a GitHub (o usa git init + push)
3. GitHub Actions compila el APK (workflow .github/workflows/build-apk.yml)
4. Descarga el APK de Actions > Artifacts

NO necesitas agregar secrets a GitHub - las credenciales estan en el codigo.

## Archivos clave

- `vivid-app/app/build.gradle.kts` - define CF_BASE_URL como BuildConfig field
- `vivid-app/app/src/main/java/com/vivid/app/di/StorageModule.kt` - lee de BuildConfig.CF_BASE_URL
- `vivid-app/app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt` - claves B2 (referencia)

## Desplegar Cloud Function (una vez)

```bash
cd vivid-app/cloud-function
npm install
firebase functions:config:set \
  b2.key_id="0044482642d8bb00000000005" \
  b2.application_key="K0043ske+MzlEoRWXQtmJ18opgnipXQ" \
  b2.bucket_id="94c488b2a624f22d98eb0b10" \
  b2.bucket_name="VividGrem"
firebase deploy --only functions
```

Copia la URL que devuelve Firebase y cambiala en `build.gradle.kts`:
```kotlin
val CF_BASE_URL_VALUE = "https://us-central1-TU_PROYECTO.cloudfunctions.net"
```

