# Vivid - cómo generar el APK en GitHub

Corregí la configuración que estaba causando el fallo en GitHub Actions:

- `java.lang.OutOfMemoryError: Java heap space`
- `Gradle build daemon disappeared unexpectedly`
- `Process completed with exit code 1`

El problema principal era memoria insuficiente para Gradle/D8 al hacer `mergeExtDexDebug`. El proyecto tenía un heap muy bajo para Android + Kotlin + Compose + Firebase + Hilt.

## Archivos modificados

- `.github/workflows/build.yml`
  - Usa JDK 17.
  - Instala Android SDK 35 y Build Tools 35.0.0.
  - Compila con `--max-workers=1` para bajar el pico de memoria.
  - Sube el APK generado como artifact.

- `vivid-app/gradle.properties`
  - Sube Gradle heap a `-Xmx3g`.
  - Sube metaspace a `1g`.
  - Desactiva paralelismo/configure-on-demand para CI.
  - Evita daemons extra de Kotlin.

- `vivid-app/build.gradle.kts`
  - Declara también los plugins `ksp` y `hilt` a nivel raíz con `apply false`.

- `vivid-app/app/proguard-rules.pro`
  - Agregado para que el build release no falle por archivo faltante.

## Cómo usarlo

1. Descomprime este ZIP.
2. Sube/reemplaza estos archivos en tu repositorio GitHub.
3. Haz commit y push a `main` o `master`.
4. En GitHub entra a **Actions → Build Vivid APK**.
5. Cuando termine correctamente, descarga el artifact llamado **vivid-debug-apk**.

El APK estará dentro del artifact como algo parecido a:

```text
app-debug.apk
```

También puedes ejecutarlo manualmente desde **Actions → Build Vivid APK → Run workflow**.
