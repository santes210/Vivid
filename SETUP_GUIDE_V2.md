# Vivid v2.1 - Guia completa

Stack: Kotlin + Jetpack Compose + Material You 3 + Firebase + Backblaze B2 (via Cloud Functions) + Media3 + Hilt.

## !! SEGURIDAD CON GITHUB ACTIONS !!

Si subes tu APK a GitHub y compilas con Actions:

### 1. Configurar GitHub Secrets
Ve a tu repo en GitHub -> Settings -> Secrets and variables -> Actions -> New repository secret:

| Nombre | Valor |
|---|---|
| `CF_BASE_URL` | URL de tu Cloud Function (la que te dio `firebase deploy`) |

NO agregues `B2_KEY_ID`, `B2_APPLICATION_KEY`, `BUCKET_ID` como secrets de GitHub.
Esas claves SOLO van en Firebase Functions Config (secrets del backend).

### 2. El workflow ya esta incluido
Archivo: `.github/workflows/build-apk.yml`
- Lee `CF_BASE_URL` desde GitHub Secrets
- Genera `local.properties` con ese valor
- Compila el APK

### 3. Que se commitea y que NO

| SI commitear | NO commitear |
|---|---|
| `build.gradle.kts` | `local.properties` |
| `.github/workflows/build-apk.yml` | `BuildConfigSecrets.kt` |
| Codigo fuente (`app/src/main/...`) | `*.keystore`, `*.jks` |
| `cloud-function/index.js` | Credenciales B2 en cualquier archivo |

`.gitignore` ya incluye todo lo sensible.

---

## INSTALACION (paso a paso)

### Paso 1 - Cloud Function
```bash
cd cloud-function
npm install

firebase functions:config:set \
  b2.key_id="0044482642d8bb00000000005" \
  b2.application_key="K0043ske+MzlEoRWXQtmJ18opgnipXQ" \
  b2.bucket_id="94c488b2a624f22d98eb0b10" \
  b2.bucket_name="VividGrem"

firebase deploy --only functions
```

Copia la URL que te devuelve Firebase (ej. `https://us-central1-mi-proyecto.cloudfunctions.net`).

### Paso 2 - Configurar GitHub Secret
En tu repo GitHub -> Settings -> Secrets:
- Nombre: `CF_BASE_URL`
- Valor: la URL del paso 1

### Paso 3 - Codigo Android
Copia todo de `app/` a tu proyecto. Sync Gradle. Run.

### Paso 4 - Local dev (opcional)
Si compilas localmente sin GitHub Actions, crea `local.properties`:
```
sdk.dir=/Users/tu/Library/Android/sdk
CF_BASE_URL=https://us-central1-mi-proyecto.cloudfunctions.net
```

---

## LAS 8 FEATURES

### 1. Material You 3
- `theme/Type.kt` - escala M3 completa
- `theme/Shape.kt` - esquinas 4/8/16/24/32
- `theme/VividColors.kt` - paleta fallback
- `theme/Theme.kt` - dynamicColor + edge-to-edge

### 2. Stories con video
- `CreateStoryScreen.kt`, `CreateStoryViewModel.kt`
- `StoryViewerScreen.kt` (reproduce fotos y videos)
- Auto-borrado a 24h

### 3. Stickers y Texto en Stories (NUEVO)
- `StoryOverlay.kt` - modelo de datos (TextOverlay + StickerOverlay)
- `StoryOverlayRenderer.kt` - renderiza overlays sobre Bitmap via Canvas
- `StoryEditorScreen.kt` - editor completo:
  - Arrastrar, rotar, escalar con gestos
  - Biblioteca de stickers categorizados
  - Color picker para texto
  - Exporta bitmap final listo para B2

### 4. Marca de agua
- `VideoWatermarker.kt` - Media3 BitmapOverlay "Vivid X"
- Toggle en CreateReelScreen
- Obligatorio en Stories

### 5. Miniaturas automaticas
- `VideoThumbnailer.kt` - MediaMetadataRetriever
- JPEG 480px, ~30KB

### 6. Trim de video
- `VideoTrimmer.kt` + `VideoTrimmerScreen.kt`
- UI dual-thumb estilo IG
- Media3 ClippingConfiguration

### 7. Filtros de color (NUEVO)
- `ColorFilterEffect.kt` - 8 filtros via Media3 RgbMatrix
- Normal, Sepia, B&N, Contraste, Calido, Frio, Vivido, Fade
- GPU-accelerated, sin re-encoding extra

### 8. Musica libre de regalias (NUEVO)
- `AudioMixer.kt` - mezcla audio con video
- Coloca MP3 en `app/src/main/assets/music/`
- README explica fuentes libres

### 9. Analytics (NUEVO)
- `ReelAnalyticsTracker.kt`
- Metricas por reel: views, watchTime, completedViews
- Flush cada 5s para no abusar de Firestore

### 10. Push notifications (NUEVO)
- CF triggers: `onReelLike`, `onReelComment`, `onFollow`
- `PushNotificationHelper.kt` - registra token FCM al login
- Auto-colapsa pushes duplicados con `tag`

---

## ESTRUCTURA (35 archivos)

```
vivid-b2-integration/
├── .github/workflows/build-apk.yml        NEW: GitHub Actions
├── .gitignore                             NEW: secretos excluidos
├── SETUP_GUIDE.md
├── local.properties.template
├── app/
│   ├── build.gradle.kts                  NEW: lee de env vars
│   ├── res/drawable/vivid_watermark.xml
│   ├── theme/{Theme,Type,Shape,VividColors}.kt
│   ├── storage/{StorageProvider,CloudFunctionsStorageProvider,VideoCompressor}.kt
│   ├── di/{StorageModule,BuildConfigSecrets}.kt  (BuildConfigSecrets sin secretos)
│   ├── util/
│   │   ├── VideoCompressor.kt (existente)
│   │   ├── VideoWatermarker.kt
│   │   ├── VideoThumbnailer.kt
│   │   ├── VideoTrimmer.kt
│   │   ├── ColorFilterEffect.kt          NEW
│   │   ├── AudioMixer.kt                 NEW
│   │   ├── ReelAnalyticsTracker.kt       NEW
│   │   └── PushNotificationHelper.kt     NEW
│   ├── presentation/
│   │   ├── create/{CreatePostScreen,CreateReelScreen,CreateReelViewModel,
│   │   │           CameraVideoScreen,VideoTrimmerScreen}.kt
│   │   ├── reels/{ReelsScreen,ReelsViewModel}.kt
│   │   └── stories/
│   │       ├── CreateStoryScreen.kt
│   │       ├── CreateStoryViewModel.kt
│   │       ├── StoryViewerScreen.kt
│   │       ├── StoryOverlay.kt           NEW
│   │       └── StoryEditorScreen.kt      NEW
│   ├── navigation/VividNavigation.kt
│   └── src/main/assets/music/README.md   NEW
└── cloud-function/
    ├── index.js                          (con 3 triggers de push nuevos)
    └── package.json
```

---

## COMO PROBAR CADA FEATURE

### Stickers/Texto en Stories
1. Crear Story
2. Elige foto o graba video
3. En el editor, toca "Texto" o "Sticker"
4. Arrastra, rota, escala con un dedo
5. Siguiente -> publica

### Filtros de color
1. Crear Reel
2. Selecciona video
3. Antes de publicar, elige filtro del carrusel
4. El video se pre-visualiza con el filtro

### Analytics
1. Reproduce un reel
2. Espera 5 segundos -> Firestore actualiza `totalWatchTimeSec`
3. Scrollea a otro reel -> `watchTime` se flushea
4. Mira en Firestore: `reels/{id}/viewsCount`, `totalWatchTimeSec`, `completedViews`

### Push notifications
1. Deploya la CF (Paso 1 arriba)
2. Login en la app -> registra FCM token automaticamente
3. Desde otro usuario, dale like a tu reel
4. Llega push "X le dio like a tu reel"

### Musica
1. Coloca MP3 en `app/src/main/assets/music/`
2. En CreateReelScreen selecciona el MP3
3. El audio del video se reemplaza con la cancion

---

## TROUBLESHOOTING

| Error | Solucion |
|---|---|
| "BuildConfigSecrets.kt: PEGA_AQUI_TU" | Solo placeholder, no se usa en runtime. Reemplazar la URL publica de CF |
| "CF_BASE_URL not found" en build | Crear local.properties con CF_BASE_URL=... o agregar GitHub Secret |
| Push no llega | Verificar `firebase functions:config:get` tiene las claves B2 |
| Filtro sale verde/loco | La matriz 4x5 es RGB - revisa que la diagonal sea ~1.0 |
| AudioMixer falla | Media3 Transformer requiere Android 8.0+. Verifica minSdk |
| Story editor superpone mal | El offset usa fracciones 0-1. Asegurate que el canvas se midio |

---

## LIMITES DE BACKBLAZE B2 (10 GB gratis)

| Tipo | Tamano | Cantidad |
|---|---|---|
| Reel comprimido (60s) | ~6 MB | ~1,700 |
| Story video (15s) | ~2 MB | ~5,000 |
| Thumbnail JPEG | ~30 KB | ~340,000 |
| Filtered reel (60s) | ~6 MB | ~1,700 |
| Audio MP3 background | ~3 MB | ~3,300 |

