# Vivid - APK en GitHub Actions

Esta versión corrige los fallos vistos en las capturas.

## Errores corregidos

### 1. Memoria insuficiente de Gradle

Error anterior:

```text
java.lang.OutOfMemoryError: Java heap space
Gradle build daemon disappeared unexpectedly
```

Solución aplicada en `vivid-app/gradle.properties` y `.github/workflows/build.yml`:

- Heap de Gradle aumentado a `-Xmx3g`.
- `MaxMetaspaceSize=1g`.
- `--max-workers=1` para reducir consumo de memoria.
- Desactivado paralelismo innecesario en CI.

### 2. Errores Kotlin de referencias no resueltas

Errores nuevos:

```text
CreatePostScreen.kt:108:36 Unresolved reference 'PhotoLibrary'
CreatePostScreen.kt:121:36 Unresolved reference 'PhotoCamera'
CreatePostScreen.kt:217:36 Unresolved reference 'CloudUpload'
CreatePostScreen.kt:277:18 Unresolved reference 'await'
FeedScreen.kt:15:47 Unresolved reference 'Cloud'
FeedScreen.kt:285:49 Unresolved reference 'Cloud'
FeedScreen.kt:293:13 Unresolved reference 'Image'
```

Solución aplicada:

- Se reemplazaron iconos que no vienen en `material-icons-core` por iconos disponibles.
- Se agregó el import faltante:

```kotlin
import kotlinx.coroutines.tasks.await
```

- Se agregó el import faltante para Compose Image:

```kotlin
import androidx.compose.foundation.Image
```

## Archivos modificados importantes

- `.github/workflows/build.yml`
- `vivid-app/gradle.properties`
- `vivid-app/build.gradle.kts`
- `vivid-app/app/proguard-rules.pro`
- `vivid-app/app/src/main/java/com/vivid/app/presentation/create/CreatePostScreen.kt`
- `vivid-app/app/src/main/java/com/vivid/app/presentation/feed/FeedScreen.kt`

## Cómo generar el APK

1. Descomprime este ZIP.
2. Reemplaza/sube los archivos en tu repositorio de GitHub.
3. Haz commit y push a `main` o `master`.
4. Ve a **Actions → Build Vivid APK**.
5. Cuando termine correctamente, descarga el artifact:

```text
vivid-debug-apk
```

Dentro estará el APK debug, normalmente:

```text
app-debug.apk
```
