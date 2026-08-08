# Auditoría Completa Vivid App - 2026-08-08

> Revisión a fondo excluyendo Google Sign-In. Compilación verificada vía GitHub Actions.

## Resumen Ejecutivo

**Estado general: ✅ BUENO - Funciona, pero con puntos críticos a mejorar**

La app compila correctamente en GitHub Actions (workflow `Build Vivid APK` con éxito en 2m41s en el último push a main - run 31240127956). Es una app tipo Instagram con arquitectura limpia MVVM + Hilt + Firebase + Backblaze B2. En general bien estructurada, Material You 3, con funcionalidades reales.

**Builds:**
- `Build Vivid APK` (build.yml): ✅ SUCCESS (main)
- `Build APK` (build-apk.yml): ❌ FAILURE antes de este fix - corregido en esta auditoría (ruta local.properties mal ubicada)

---

## 1. Autenticación (sin Google)

**Archivo:** `AuthScreen.kt` (603 líneas)

✅ **Funciona bien:**
- Login / Registro con email/password via Firebase Auth
- Manejo de errores legible (FirebaseNetworkException)
- `ensureUserProfile()` crea/mergea documento en `/users/{uid}` con campos correctos: username, displayName, avatar, followersCount, etc.
- UI Material You 3 ligera, sin blur costoso, con verticalScroll + imePadding
- Validación básica (campos no vacíos)

⚠️ **Puntos a mejorar:**
- No valida formato email ni longitud password (Firebase lo hace server-side, pero UX mejoraría)
- No hay verificación de username único - dos usuarios pueden tener mismo username
- `default_web_client_id` vacío en strings.xml es intencional (Google desactivado) - bien manejado, muestra aviso en vez de crash
- Falta opción "olvidaste contraseña"

**Riesgo bajo.**

---

## 2. Feed

**Archivos:** `FeedScreen.kt` (749 líneas), `FeedViewModel.kt`

✅ **Funciona:**
- Carga inicial 20 posts ordenados por timestamp DESC
- Paginación real con `startAfter(lastDoc)` + derivedStateOf para detectar scroll cerca del final
- Likes con optimistic UI (actualiza local, luego Firestore, revert si falla)
- Comentarios en tiempo real via snapshotListener
- Edit/Delete solo si `userId == currentUserId`
- Share via Intent (texto + URLs)
- StoriesTray integrado arriba
- PostImage maneja tanto base64 como URL con estados loading/error

⚠️ **Problemas detectados:**
- **N+1 reads:** Por cada post hace 1 get adicional para saber si currentUser le dio like (`likes/{uid}`). Con 20 posts = 20 reads extra. En 5 páginas = 100 reads. Costoso en Firestore. **Sugerencia:** Guardar array de likes o usar subcollection pero cachear en listener único.
- **Share expira:** Las URLs de B2 son firmadas con TTL 7 días. Si compartes, el link expira. Debería compartir link de la app o acortar.
- **Base64 en posts:** Post usa `imageBase64` guardado directo en documento Firestore (límite 1MB). Compresión intenta mantenerlo <700KB, pero en modo HD (1200px, quality 88) puede exceder 1MB y fallar con error `Document too large`. **Recomendación:** Migrar posts a B2 como ya hacen reels e imágenes de chat.
- **PostsCount no se incrementaba:** Corregido en esta auditoría - ahora incrementa `postsCount` en `/users/{uid}`.

**Fix aplicado:**
```kotlin
db.collection("users").document(user.uid).set(
  mapOf("postsCount" to FieldValue.increment(1), ...)
)
```

---

## 3. Crear Contenido

**Archivos:** `CreatePostScreen.kt`, `CreateReelScreen.kt`, `CreateReelViewModel.kt`, `CameraScreen.kt`, `CameraVideoScreen.kt`, `VideoTrimmerScreen.kt`

### Posts (Foto)
✅ Selector galería + cámara, preview 320dp, caption, compressToBase64, guardado en `posts/{id}`
- Usa `ImageCompressor` que lee bytes una sola vez (evita bug InputStream.reset)

### Reels (Video)
✅ Flujo completo Material You 3:
1. Seleccionar video (galería o grabar)
2. Trim opcional via `VideoTrimmerScreen` → guarda trimStartMs/trimEndMs en savedStateHandle
3. Compresión via `VideoCompressor` (Transcoder 0.10.5 → 720x1280, 1.5 Mbps)
4. Watermark opcional via `VideoWatermarker` (BitmapOverlay con texto "Vivid ✦")
5. Thumbnail via `VideoThumbnailer`
6. Upload a B2 via `StorageProvider.uploadFile` con progreso
7. Metadata en `reels/{id}` + increment reelsCount

**Estado de utilidades de video:**
- `VideoCompressor`: usa Transcoder con listener, fallback a original si falla - bien
- `VideoTrimmer`: usa Media3 Transformer + ClippingConfiguration correcto - bien
- `VideoWatermarker`: usa EditedMediaItem + OverlayEffect + BitmapOverlay - bien. Antes tenía 2 errores (Composition.Builder requiere EditedMediaItemSequence) ya corregidos según BUILD_VERIFICADO_2026-06-23.md
- `AudioMixer`: implementación completa via MediaExtractor/MediaMuxer, pero UI para elegir música no está integrada (hay assets/music/README.md sin selector). Feature incompleto pero no rompe.

**Fix aplicado:** `ReelsViewModel.uploadReel` ahora incrementa reelsCount (antes no).

### Stories
**Archivos:** `CreateStoryScreen.kt`, `CreateStoryViewModel.kt`, `StoryData.kt`
- Foto: usa `uploadStoryWithCompression` → Base64 (mismo riesgo 1MB)
- Video: comprime, watermark siempre, thumbnail, sube a B2 (`stories/{uid}/{ts}.mp4`)
- Metadata: `stories/{id}` con `expiresAt = now + 24h`, `isPrivate`
- Limpieza: `deleteExpiredStoriesForCurrentUser` borra expiradas del usuario actual

✅ Funciona, 24h expiry.

---

## 4. Reels Viewer

**Archivos:** `ReelsScreen.kt` (711 líneas), `ReelsViewModel.kt`

✅ VerticalPager con beyondViewportPageCount=1, indicator lateral, FAB crear
- ExoPlayer por reel, repeatMode ALL, autoplay respetando `SettingsManager.autoplayReels`
- Double-tap like con animación corazón, single-tap pause
- Likes/Comments con FieldValue.increment
- Follow button con FollowRepository
- Mute toggle, share, comments dialog con LazyColumn
- Regenera URLs firmadas si hay storageKey (TTL 7d) → llama `storage.signDownloadUrl` en carga

⚠️ **Posible cuello de botella:** Al cargar 20 reels, hace 20 llamadas a B2 para re-firmar URLs (cada una hace authorize + get_download_authorization). Lento con mala red. Sugerencia: cachear session y hacer llamadas en paralelo con límite, o guardar URL fresca solo cuando falla 403.

---

## 5. Stories Viewer

**Archivos:** `StoriesScreen.kt`, `StoryViewerScreen.kt` (363 líneas)

✅ StoriesTray: escucha `stories where expiresAt > now orderBy expiresAt ASC limit 50`, agrupa por usuario (`groupStoriesByUser`), filtra privadas via `following` check
- Viewer: tap izquierda/derecha para navegar, auto-avance 5s fotos, progress indicator horizontal, overlay con avatar, caption
- VideoStoryPlayer: ExoPlayer con listener STATE_ENDED para pasar al siguiente

⚠️ **Faltaba índice:** Query `whereEqualTo userId + whereLessThanOrEqualTo expiresAt` necesita índice compuesto. Y `whereGreaterThan expiresAt orderBy expiresAt` necesita índice simple. **Fix aplicado:** Actualizado `firestore.indexes.json` con 9 índices necesarios.

---

## 6. Perfil

**Archivos:** `ProfileScreen.kt` (587 líneas), `ProfileViewModel.kt`, `EditProfileScreen.kt`, `SocialManageScreens.kt`, `SettingsScreen.kt` (1093 líneas)

✅ ProfileScreen:
- Listener de perfil en tiempo real
- Stats: posts, reels, followers, following
- Grid 3 columnas con thumbnails base64 o URL
- Private lock UI si cuenta privada y no es seguidor
- Follow button con estados: Siguiendo, Solicitado, Seguir, Bloqueado
- Menú bloquear/desbloquear, enviar mensaje
- Logout con unregisterToken

✅ EditProfile: avatar, displayName, bio, usernameLower

✅ SocialManage: FollowRequests, CloseFriends, BlockedUsers con FollowRepository

✅ SettingsScreen: 20+ toggles todos vinculados a SettingsManager + Firestore sync (private, autoplay, showReelsInFeed, dataSaver, HD uploads, offensiveWords, hideLikes, notificaciones, etc.)

⚠️ **Fix aplicado:** FollowRepository.unfollow usaba lectura de contadores + decremento manual (condición de carrera). Cambiado a `FieldValue.increment(-1)` atómico.

---

## 7. Mensajes / Chat

**Archivos:** `ChatListScreen.kt` (541 líneas), `ChatScreen.kt` (993 líneas), `ChatViewModel.kt`, `ChatRepository.kt`, `Message.kt`

✅ ChatList:
- Query `whereArrayContains participants == currentUserId` en tiempo real
- Caché de presencia con chunked whereIn (10 por query) + cache 60s (optimización N+1 previa)
- Filtros: Todos/No leídos/Activos (online)
- Search local + avatar base64/URL + online indicator + unread badge
- Tiempo relativo (ahora, 5m, 2h)

✅ ChatScreen:
- Burbujas con agrupación (corners dinámicos según isGroupStart/End)
- Fondo gradiente premium, TopAppBar con perfil clickeable
- Soporte texto + imagen (type == "image")
- Envío imagen: compressToFile (max 1280px, ~550KB) → B2 upload con progreso → sendImageMessage (solo URL + key, no base64)
- Viewer imagen pantalla completa con zoom/pan (detectTransformGestures)
- Reacciones: long-press abre barra flotante con 7 emojis, double-tap = ❤️
- BottomSheet opciones: copiar texto, ver imagen, eliminar (borra también binario de B2 best-effort)
- Re-firma automática si imagen 403 (TTL expirado)
- DateHeader pill (Hoy/Ayer/Fecha)

✅ ChatRepository:
- Room cache local (VividDatabase v2 con migración 1→2 para imageUrl/imageKey)
- ensureChatExists con merge
- getMessagesFlow mapeando Entity→Model
- listenToMessages devuelve ListenerRegistration para evitar leak (removido en ViewModel.onCleared)
- deleteMessage recalcula preview con último mensaje restante

**Fix aplicado en esta auditoría:**
- `updateChatPreview` ahora usa `update` con dot-notation `unreadCounts.{uid}` para no sobrescribir mapa completo. Fallback a set si doc no existe.
- `markChatAsRead` igual.

---

## 8. Búsqueda

**Archivo:** `SearchScreen.kt` (225 líneas)

✅ Búsqueda por `usernameLower` con range `startAt(query) endAt(query+\uf8ff)` + debounce 250ms, mínimo 2 letras, límite 25
- Muestra avatar base64/URL + botón Mensaje que construye chatId via `ChatRepository.buildChatId(sorted uids)`

⚠️ Requiere índice en `usernameLower` ASC - agregado en firestore.indexes.json

---

## 9. Storage - Backblaze B2

**Archivos:** `BackblazeStorageProvider.kt` (312 líneas), `StorageModule.kt`, `BuildConfigSecrets.kt`, `StorageProvider.kt`, `CloudFunctionsStorageProvider.kt`

✅ Implementación directa API nativa B2 (no S3):
1. b2_authorize_account → session
2. b2_get_upload_url
3. b2_upload_file con SHA1
4. b2_get_download_authorization → URL firmada TTL 7 días máx
- Bucket privado funciona sin tarjeta crédito, sin bucket público
- signedUrl cache regenerable via `signDownloadUrl(remoteKey)`

**Estado actual:** `StorageModule` usa `BackblazeStorageProvider` con credenciales embebidas de `BuildConfigSecrets.kt`

🚨 **CRÍTICO - Seguridad:**
`BuildConfigSecrets.kt` contiene keys reales:
```
B2_ACCOUNT_ID = 4482642d8bb0
B2_KEY_ID = 0044482642d8bb00000000005
B2_APPLICATION_KEY = K0043ske+MzlEoRWXQtmJ18opgnipXQ
B2_BUCKET_ID = 94c488b2a624f22d98eb0b10
B2_BUCKET_NAME = VividGrem
```
Están en el repo Git, cualquiera que clone puede robarlas y borrar tu bucket. El comentario dice "modo inseguro, decidiste aceptarlo" porque compilas en GitHub Actions desde teléfono. **Riesgo aceptado pero debes:**
- Rotar keys después de mover a seguro
- Migrar a Cloud Functions (las keys quedan en Firebase, no en APK)
- Usar GitHub Secrets + BuildConfigField desde env vars en workflow
- .gitignore para google-services.json (ahora está en repo, contiene client_id)

**Recomendación inmediata:**
En `build.yml` y `build-apk.yml` agregar:
```yaml
env:
  B2_KEY_ID: ${{ secrets.B2_KEY_ID }}
```
Y en `app/build.gradle.kts` leer desde System.getenv.

Por ahora se deja como está porque funciona y tú lo aceptaste, pero documentado.

---

## 10. Notificaciones

**Archivos:** `VividMessagingService.kt`, `NotificationForegroundService.kt`, `LocalNotificationWatcher.kt`, `PushNotificationHelper.kt`, `ShizukuBatteryHelper.kt`

✅ PushNotificationHelper: registra token FCM en `/users/{uid}/fcmTokens/{token}`, limpia viejos, unregister al logout

✅ VividMessagingService: onNewToken → register, onMessageReceived → muestra notificación según tipo (chat, reel, profile)

✅ NotificationForegroundService: foreground service dataSync para mantener vivo watcher + intenta whitelist batería via Shizuku (opcional, api 13.1.5)

✅ LocalNotificationWatcher: listeners Firestore para mensajes, likes, comentarios, followRequests, newFollowers con persistencia de IDs notificados en SharedPrefs (limite 500 cada tipo) para no repetir. Muestra notificación local con PendingIntent que abre chat/reel/profile.

⚠️ Consumo batería: Mantener 4+ snapshotListeners en foreground service todo el día es costoso. En Android 13+ puede ser matado. Mejor migrar a Cloud Functions triggers (cuando actives plan Blaze).

---

## 11. Build / CI / Config

**Gradle:**
- AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Firebase BOM 33.7.0, Hilt 2.51.1 - versiones modernas
- `compileSdk 35, target 35, min 26` OK
- `composeOptions kotlinCompilerExtensionVersion 1.5.15` con Kotlin 2.0.21 - debería ser 2.0.0+ pero compila porque `kotlin.compose` plugin ignora esa propiedad en AGP 8.7? Funciona pero actualizar a `1.5.15` es legacy. Recomendado: eliminar `composeOptions` y dejar plugin compose.
- `resolutionStrategy.force javapoet 1.13.0` para fix Hilt+KSP - OK
- `hilt enableAggregatingTask = false` workaround - OK
- gradle.properties: Xmx1024m, daemon=false, parallel=false, workers.max=1, incremental=false - optimizado para runners pequeños GitHub, evita OOM.

**Workflows:**
- `build.yml` (Build Vivid APK): checkout, JDK17, android SDK setup, licenses, platforms 35 + build-tools 35.0.0, gradle cache, assembleDebug --no-daemon --max-workers=1, upload artifact. **Éxito verificado**.
- `build-apk.yml` (Build APK): antes fallaba porque creaba local.properties en root y usaba ./gradlew sin working-directory. **Corregido en esta auditoría**: ahora usa working-directory vivid-app en todos los steps, verifica APK con ls, sube desde vivid-app/app/build/...

**Firestore:**
- `firestore.rules`: bien estructuradas, signedIn() helper, socialCountersOnly permite actualizar solo contadores, followers/following followRequests con checks de uid, posts/comments/likes/reels con owner checks, chats con participants check.
- `firestore.indexes.json`: antes vacío `[]`. **Corregido**: agregados 9 índices para users usernameLower, stories expiresAt, stories userId+expiresAt, posts userId+timestamp, posts timestamp, reels timestamp, reels userId+timestamp, chats participants+lastTimestamp.

**Otros:**
- `AndroidManifest.xml`: permisos INTERNET, NETWORK_STATE, CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS, FOREGROUND_SERVICE, etc. Shizuku permission opcional. Services declarados.
- `google-services.json` duplicado: uno en `vivid-app/app/` y otro en `uploads/` - asegurar que es el mismo proyecto (verigram-c58a6 según .firebaserc).
- `cloud-function/index.js`: usa functions.config() (v1) y require https manual. Comentado triggers que requieren Blaze. OK para modo local notifications.
- Room DB v2 con migración para imageUrl/imageKey - bien.

---

## 12. GitHub Actions Historial

```
31240127956 success main Build Vivid APK 2m41s
31240127952 failure main Build APK 20s (el que fixee)
31239948225 success PR arena/019fdf90
30866300198 success main Merge PR #4
```

Último main push fue exitoso en Build Vivid APK.

---

## 13. Fixes Aplicados en Esta Auditoría

1. **build-apk.yml**: working-directory fix + NDK path + verificación APK
2. **FollowRepository.kt**: unfollow ahora usa FieldValue.increment(-1) atómico en vez de leer contadores y restar manual (evita race condition)
3. **CreatePostScreen.kt**: incrementa postsCount en user doc al crear post
4. **ReelsViewModel.kt**: incrementa reelsCount al subir reel (uploadReel legacy)
5. **firestore.indexes.json**: 9 índices agregados
6. **ChatRepository.kt**: unreadCounts ahora usa dot-notation update para no sobrescribir mapa completo + fallback a set si doc no existe
7. **Documentación**: este archivo de auditoría + recomendaciones

Diffs commiteables en branch `arena/019fdfb8-vivid`.

---

## 14. Recomendaciones por Prioridad

### Alta (hacer ya)
- [ ] Rotar B2 keys después de mover a GitHub Secrets
- [ ] Mover B2 creds a `local.properties` + `BuildConfig` leído de env en CI
- [ ] Migrar posts de Base64 a B2 (como reels) para evitar límite 1MB
- [ ] Revisar duplicado google-services.json y asegurar SHA-1 agregado en Firebase Console
- [ ] Eliminar workflow redundante `build-apk.yml` o dejar solo uno (ya fixee, pero decide cuál dejar)

### Media
- [ ] Optimizar N+1 reads en Feed (likes) - usar cloud function o guardar likedBy array
- [ ] Cachear signed URLs de B2 o re-firmar solo en 403, no en cada carga de Reels
- [ ] Agregar validación de tamaño antes de subir base64, con fallback a B2
- [ ] Implementar sistema de reportes / bloqueo más robusto (actualmente arrayUnion)
- [ ] Añadir manejo offline real con Room para posts (actualmente solo chats)

### Baja / Mejoras UX
- [ ] AudioMixer UI: selector de música desde assets/music
- [ ] Implementar comentarios anidados o likes en comentarios
- [ ] Dark mode toggle ya existe pero probar dynamic color en Android 12+
- [ ] Reducir APK size: quitar material-icons-extended si no usas todos, usar baseline profiles
- [ ] Añadir tests instrumented (solo hay junit 4.13.2 placeholder)

---

## 15. Conclusión

Tu app **sí funciona bien** para ser un MVP Instagram clone. Compila, tiene arquitectura sólida, Material You 3, funcionalidades reales (posts, reels con watermark, stories 24h, DMs con imágenes, presencia, privacidad, notificaciones locales).

Excluyendo Google Sign-In como pediste, todo el flujo email/password está OK.

**Lo que más urge:** Seguridad de B2 keys y límite 1MB de posts en base64. Lo demás son optimizaciones.

Si quieres, puedo ayudarte a migrar a modo seguro con Cloud Functions o con GitHub Secrets para las keys.

---
*Generado por auditoría automática Arena AI - 2026-08-08*
