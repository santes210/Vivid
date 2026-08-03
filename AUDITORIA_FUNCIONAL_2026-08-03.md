# Auditoría funcional de Vivid — 2026-08-03

Revisión estática completa del código fuente (`vivid-app`). La compilación en
GitHub Actions ya funciona, así que esta ronda se centró en **errores de
ejecución**: funciones que compilaban pero se rompían o hacían nada al usarlas.

---

## ✅ Funciones completamente funcionales (verificadas en código)

| Área | Estado | Detalle |
|---|---|---|
| Registro / Login email | ✅ | Firebase Auth + creación del doc `users/{uid}` con contadores en 0 |
| Feed | ✅ | Posts en vivo (Firestore listener + Room), likes con contador atómico, comentarios en `posts/{id}/comments`, editar caption, eliminar post |
| Stories | ✅ | Crear (foto base64 o video por B2), duración 24h, privacidad (dueño/seguidores), visor foto+video, agrupadas por usuario |
| Reels | ✅ | Trim real (Media3), compresión, watermark, miniatura, subida B2, metadata, `reelsCount` se incrementa, likes y comentarios reales |
| Mensajes (DM) | ✅ | Chats en tiempo real (snapshot listener), enviar/borrar mensajes, reacciones, no leídos, presencia online |
| Buscar personas | ✅ | Búsqueda real en `users` por `usernameLower` (sin demos) |
| Seguir / Bloquear / Mejores amigos | ✅ | Batches atómicos con contadores, solicitudes a cuentas privadas, aceptar/rechazar |
| Perfil | ✅ | Edición completa con avatar, grid posts+reels en vivo, toggle de cuenta privada |
| Ajustes | ✅ | ~40 opciones todas cableadas (SettingsManager + Firestore), verificar correo, reset password, cerrar sesión |
| Cámara | ✅ | CameraX foto y video, devuelve capturas al editor |
| Storage (Backblaze B2) | ✅ | Subida nativa con URLs firmadas (bucket privado), fix del 405 aplicado |
| Utilidades de video | ✅ | Compressor, Trimmer, Watermarker, Thumbnailer, AudioMixer implementados |

---

## 🔧 Estaba roto y quedó corregido en esta ronda

1. **Las notificaciones no aparecían nunca (bug crítico).**
   `VividMessagingService` y `LocalNotificationWatcher` publican en los canales
   `messages_channel` y `general_channel`, que **nadie creaba**. En Android 8+
   el sistema descarta silenciosamente notificaciones en canales inexistentes.
   → `VividApplication` ahora crea ambos canales al arrancar.

2. **Tocar una notificación con la app abierta no hacía nada.**
   Los deep links solo se leían en `onCreate`; `onNewIntent` actualizaba
   variables planas que Compose nunca volvía a leer.
   → Nuevo `util/DeepLinkBus.kt` con `StateFlow`s; `MainActivity` publica y
   `VividNavigation` recolecta/navega/limpia.

3. **El perfil siempre mostraba "0 Publicaciones".**
   El contador `postsCount` del doc de usuario nunca se incrementaba al crear
   un post (sí se hacía con `reelsCount` al crear reels).
   → `CreatePostScreen` incrementa y `FeedScreen` decrementa al eliminar.
   Además el post ahora guarda `userProfilePicture`, `likesCount` y
   `commentsCount` iniciales.

4. **Borrar elementos dejaba archivos huérfanos en Backblaze.**
   `BackblazeStorageProvider.deleteFile()` era un stub que regresaba `false`.
   → Implementación real (`b2_list_file_names` + `b2_delete_file_version`) y
   "Limpiar stories vencidas" ahora también borra el video y la miniatura del bucket.

5. **Dato inconsistente en stories de video.**
   `mediaUrl = if (isVideo) thumbnailUrl else thumbnailUrl` (siempre la miniatura).
   → Ahora guarda el video en `mediaUrl` cuando es video.

6. **Botón "Continuar con Google" (medio roto, lado código).**
   El código ya era completo, pero si faltaba el Web Client ID el selector de
   Google moría con DEVELOPER_ERROR sin explicación.
   → Ahora detecta el ID vacío y muestra un mensaje claro con los pasos para
   arreglarlo en Firebase Console; el error 12501 (cancelar) ya no se reporta
   como fallo.

---

## ⚠️ Roto/pendiente pero NO es código (requieren acción en consolas)

1. **Google Sign-In no funcionará hasta configurar Firebase.**
   `google-services.json` tiene `"oauth_client": []`. Pasos:
   1. Firebase Console → Authentication → activar Google.
   2. En *Configuración del proyecto → tus apps Android*: agregar el SHA-1 y
      SHA-256 del keystore debug/release.
   3. Volver a descargar `google-services.json` y reemplazar
      `vivid-app/app/google-services.json`.

2. **Notificaciones push a app CERRADA.**
   El watcher local funciona con la app viva/en segundo plano; pushes con la
   app muerta requieren la Cloud Function de `cloud-function/index.js`
   (plan Blaze). Sin ella, sin push con app cerrada. El token FCM ya se
   registra bien en `users/{uid}/fcmTokens`.

3. **Las URLs de medios expiran a los 7 días** (máx. de URLs firmadas B2 —
   reproducciones viejas se regeneran vía `storageKey`, posts con solo
   `videoUrl` guardada pueden dejar de reproducirse tras 7 días).

4. **Credenciales B2 embebidas en el APK** (`BuildConfigSecrets.kt`).
   Con repo público, cualquiera puede extraerlas: **rota las claves** y migra
   a la Cloud Function cuando se pueda.

5. **2FA y "Herramientas de marca"** son toggles/informativos locales, no
   seguridad real (Firebase no tiene 2FA conectado).

---

## Sobre el log de fallo pegado en el chat

Ese error (`build.gradle.kts:34 Unresolved reference: util` por
`java.util.Properties()`) corresponde a un **commit intermedio del PR
anterior**. El `build.gradle.kts` actual en `main` ya no contiene esa línea;
la rama de ese PR compiló en verde en sus últimos intentos, y el workflow de
hoy queda sin cambios, así que **Actions seguirá compilando normal**.

---

## Archivos tocados en esta ronda

- `vivid-app/app/src/main/java/com/vivid/app/util/DeepLinkBus.kt` (nuevo)
- `.../VividApplication.kt` (canales de notificación)
- `.../MainActivity.kt` (deep links reactivos)
- `.../navigation/VividNavigation.kt` (consume y limpia el bus)
- `.../data/storage/BackblazeStorageProvider.kt` (deleteFile real)
- `.../presentation/stories/StoryData.kt` (limpieza con borrado B2)
- `.../presentation/stories/CreateStoryViewModel.kt` (mediaUrl correcto)
- `.../presentation/create/CreatePostScreen.kt` (postsCount + campos del post)
- `.../presentation/feed/FeedScreen.kt` (decrementar postsCount al borrar)
- `.../presentation/profile/SettingsScreen.kt` (pasa el provider a la limpieza)
- `.../presentation/auth/AuthScreen.kt` (Google: guard + mensajes claros)
