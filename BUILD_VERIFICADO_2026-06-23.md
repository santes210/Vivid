# ✅ Vivid — Build + Backblaze B2 verificados
Fecha: 2026-06-23

## Resultado general
- **Build `assembleDebug`**: ✅ SUCCESSFUL → APK 29 MB generado
- **Compilación Kotlin**: ✅ 0 errores (antes tenía 96)
- **Backblaze B2**: ✅ Subida + URL firmada verificadas end-to-end
- **Bucket privado**: ✅ Funciona SIN tarjeta de crédito, SIN bucket público, SIN Cloud Function

---

## 1. Correcciones de build (96 errores → 0)

| Archivo | Errores | Causa |
|---|---|---|
| `VideoCompressor.kt` | 5 | `bitRate()` es `Long`, `onTranscodeCompleted()` es `Int` |
| `StoryEditorScreen.kt` | 72 | Imports faltantes + helpers que chocaban con Compose |
| `ColorFilterEffect.kt` | 12 | `RgbMatrix` es interfaz; `Effects` es clase Java |
| `VideoTrimmer.kt` | 3 | `ClippingConfiguration` va en `MediaItem.Builder` |
| `VideoWatermarker.kt` | 2 | `Composition.Builder` requiere `EditedMediaItemSequence` |

## 2. Backblaze B2 modo directo (bucket privado)

### Qué se hizo
La app hablaba con una Cloud Function que **no está desplegada** (404) y usaba
una URL placeholder `TU_PROYECTO`. Se cambió a **modo directo**:

```
StorageModule  →  BackblazeStorageProvider  →  API nativa de B2
                       (credenciales de BuildConfigSecrets.kt)
```

### Cómo funciona con bucket privado (sin tarjeta de crédito)
1. `b2_authorize_account` → sesión
2. `b2_get_upload_url` → URL de subida
3. `b2_upload_file` → PUT del archivo
4. **`b2_get_download_authorization`** → URL firmada (TTL 7 días)

La URL firmada permite a ExoPlayer/Coil reproducir desde un bucket **privado**
sin exponer el bucket al público. No requiere tarjeta de crédito en Backblaze.

### Prueba end-to-end realizada (credenciales reales)
| Paso | Resultado |
|---|---|
| Autorización B2 | ✅ accountId: 4482642d8bb0 |
| Subida de archivo | ✅ 44 bytes a VividGrem |
| URL firmada generada | ✅ TTL 1h |
| Descarga con URL firmada | ✅ contenido devuelto |
| Descarga SIN autorización | ✅ HTTP 401 (bucket privado confirmado) |

### Archivos modificados
- `BackblazeStorageProvider.kt` — `uploadFile` devuelve URL firmada; nuevo `signDownloadUrl()`; content-type según extensión
- `StorageProvider.kt` — `signDownloadUrl()` añadido a la interfaz
- `StorageModule.kt` — cambiado a `BackblazeStorageProvider` con credenciales
- `ReelsViewModel.kt` — renueva URLs firmadas al cargar el feed
- `ReelsScreen.kt` — usa `ReelsViewModel` (con renovación) en vez de listener directo
- `CreateReelViewModel.kt` — sube thumbnail con `uploadFile` (URL firmada propia)
- `CloudFunctionsStorageProvider.kt` — `signDownloadUrl()` override (para migración futura)

---

## 3. Cómo aplicar los cambios

El workspace tiene `fix-build-backblaze.patch` con ambos commits.

```bash
# En tu clon local del repo:
git apply fix-build-backblaze.patch
git commit -am "Fix: build + Backblaze B2 modo directo bucket privado"
git push origin main
```

Tras el push, GitHub Actions compilará el APK con Backblaze funcionando.

---

## 4. Notas importantes

### TTL de URLs firmadas (7 días)
Las URLs firmadas de B2 expiran a los 7 días (máximo de Backblaze).
El `ReelsViewModel` **renueva** automáticamente las URLs al cargar el feed
usando el `storageKey` guardado en Firestore, así los reels viejos siguen
funcionando.

**Pendiente de revisión** (mismos patrones para cuando amplíes):
- `StoryViewerScreen.kt` — carga stories con URLs directas de Firestore
- `ProfileScreen.kt` / `FeedScreen.kt` — usan `imageUrl` (posts, posible Firebase Storage)

### Seguridad
Las claves B2 van dentro del APK (ya habías aceptado este modo inseguro).
Para migrar a seguro en el futuro: desplegar `cloud-function/index.js`
(tiene 2 bugs a corregir: `.document()` → `.doc()` en triggers de admin).

### CF_BASE_URL
Ya no se usa en el modo directo. Queda en `BuildConfigSecrets.kt` y
`build.gradle.kts` por si migras a Cloud Function después.
