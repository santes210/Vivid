# Mejoras sugeridas para Vivid — 2026-08-03

> Nota: esta lista **omite el Google Sign-In** (se deja intacto, como pediste).
> El login screen ya fue rediseñado a Material You 3 y optimizado para gama baja
> (ver `AuthScreen.kt`).

---

## ✅ Implementado (2026-08-03) — Sistema de mensajes completo

El sistema de mensajes fue renovado y ahora soporta **imágenes por Backblaze B2**
(por lo que Firestore ya no se satura con binarios):

| Archivo | Cambio |
|---|---|
| `domain/repository/ChatRepository.kt` | `sendImageMessage()` (solo URL + key en Firestore), `listenToMessages()` ahora devuelve el `ListenerRegistration` (**fix del leak**), no-leídos con `FieldValue.increment` (se acumulan de verdad), `deleteMessage()` también borra el archivo de B2, preview del chat con "📷 Imagen" y `lastMessageType` |
| `data/storage/BackblazeStorageProvider.kt` | `deleteFile()` **implementado de verdad** (b2_list_file_names + b2_delete_file_version), antes devolvía `false` siempre |
| `presentation/messages/ChatViewModel.kt` | Pipeline de envío: comprimir → subir a B2 con progreso → guardar mensaje. Estados de subida con reintento/descartar, listener removido en `onCleared()`, `refreshImageUrl()` re-firma URLs caducadas (TTL 7 días de B2) |
| `presentation/messages/ChatScreen.kt` | Menú "+" → **Enviar foto** (Photo Picker), burbuja de progreso (comprimir/subir/fallo), burbujas de imagen con tap → **visor a pantalla completa**, opciones adaptadas (Ver imagen / Eliminar, que borra también de B2) |
| `presentation/messages/ChatListScreen.kt` | Presencia con **1 query `whereIn` por lote de 10** + caché de 60s (antes: 1 lectura por usuario por cada evento = N+1), preview de "📷 Imagen" |
| `presentation/messages/Message.kt` + `MessageEntity.kt` + `VividDatabase.kt` | Modelo con `type`, `imageUrl`, `imageKey` + **migración Room v1→v2** (datos existentes intactos) |
| `util/ImageCompressor.kt` | `compressToFile()`: JPEG máx 1280px / ~550 KB para subir a B2 (rápido en conexiones lentas) |
| `util/LocalNotificationWatcher.kt` | Los mensajes de imagen ahora notifican "📷 Imagen" en vez de quedarse mudos |

**Cómo funciona:** la imagen se comprime en el teléfono → se sube a tu bucket B2
(`chat_images/{chatId}/{id}.jpg`) con la URL firmada → Firestore solo guarda
`{type:"image", imageUrl, imageKey}` (documento de ~100 bytes). Si la URL firmada
caduca (7 días), la app la re-firma sola al fallar la carga.

---



## 🔴 Prioridad alta (rendimiento en teléfonos antiguos)

### 1. Activar R8 / minify en el build de release
**Dónde:** `vivid-app/app/build.gradle.kts` → `release { isMinifyEnabled = false }`

El release compila **sin ofuscar ni eliminar código muerto**. Con `material-icons-extended`
(decenas de miles de clases de iconos) el APK queda enorme, el cold start es más lento y
la RAM en gama baja sufre. Firebase ya trae sus reglas de ProGuard en el BOM; solo hay que
activar `isMinifyEnabled = true` + `shrinkResources = true` y probar.

### 2. El watcher de notificaciones (Foreground Service + listeners de Firestore)
**Dónde:** `util/LocalNotificationWatcher.kt`, `notifications/NotificationForegroundService.kt`

El servicio en primer plano mantiene **varios snapshot listeners de Firestore activos 24/7**
(mensajes, seguidores, actividad de reels) → batería y RAM en segundo plano, especialmente
en teléfonos viejos. Alternativas:
- **WorkManager** con ejecución periódica (15–30 min) en lugar del servicio persistente, o
- subir a FCM real (requiere plan Blaze) y eliminar el watcher.

### 3. Leak real: listener de mensajes que nunca se remueve
**Dónde:** `domain/repository/ChatRepository.kt` → `listenToMessages()` (línea ~196)

Registra un `addSnapshotListener` y **nunca lo remueve**: cada vez que abres un chat se
acumula un listener permanente en el repositorio (singleton). Memoria + red + batería.
**Fix:** que `listenToMessages` devuelva el `ListenerRegistration` y llamar `remove()` en
`ChatViewModel.onCleared()` (o en un `DisposableEffect`).

### 4. Base64 embebido en Firestore
**Dónde:** `PostEntity.imageBase64`, `avatarBase64`, posts/reels/chat en `FeedScreen`,
`ProfileScreen`, `SearchScreen`, `ChatScreen`

Las imágenes viajan como Base64 **dentro de los documentos de Firestore**: documentos de
cientos de KB, más red, más RAM al decodificar. Ya tienes Backblaze/Storage + Coil con caché
de disco de 250 MB (bien configurado). **Fix:** guardar solo la URL del storage en Firestore
y subir el binario por separado. Es el cambio de mayor impacto en fluidez del feed.

### 5. Splash screen inexistente (pantalla blanca al arrancar)
**Dónde:** `AndroidManifest.xml`, `themes.xml`

No hay `androidx.core:core-splashscreen` ni `windowBackground` con marca: al abrir la app en
un teléfono viejo se ve una pantalla blanca hasta que Compose dibuja, lo que se percibe como
"lento". **Fix:** agregar `core-splashscreen` con el logo de Vivid (cuesta 5 minutos y cambia
mucho la percepción).

### 6. UserPresenceHelper escribe en Firestore en cada onStart/onStop
**Dónde:** `MainActivity.kt`, `util/UserPresenceHelper.kt`

Cada pausa/reanudación escribe `isOnline`/`lastActiveAt`. **Fix:** throttle/debounce
(no escribir más de una vez por minuto) o usar un timeout de presencia en el servidor.

### 7. Reels: autoplay activado por defecto
**Dónde:** `SettingsManager.autoplayReels = true`, `ReelsScreen.kt`

En gama baja reproducir video automáticamente consume CPU/GPU/batería. Sugerencia: que el
default sea `false` en dispositivos con poca RAM (o solo en Wi-Fi), y ya que tienes el switch
en Ajustes, mantenerlo.

### 8. El setting "Animaciones suaves" no hace nada
**Dónde:** `SettingsManager.smoothAnimationsEnabled` (solo se guarda, no se usa)

El switch existe en Ajustes pero ninguna animación lo consulta. **Fix:** usarlo para reducir
duraciones (`tween`) o desactivar animaciones en gama baja, o quitarlo para no confundir.

---

## 🟡 Prioridad media (confiabilidad y calidad)

### 9. El feed no tiene paginación real
**Dónde:** `FeedViewModel.loadPostsFromFirestore()` → `limit(20)` sin cursor

Siempre trae los mismos 20 posts y nunca carga más. **Fix:** paginar con
`startAfter(lastVisible)` + scroll infinito en `FeedScreen` (LazyColumn ya existe).

### 10. El switch de "2FA" en Ajustes es cosmético
**Dónde:** `SettingsManager.twoFactorAuthEnabled`

Solo guarda un booleano; no hay 2FA real (ni email OTP ni TOTP). O se implementa con
Firebase Identity Platform, o se quita/etiqueta como "próximamente" para no prometer
seguridad que no existe.

### 11. Strings hardcodeados en español por todo el código
**Dónde:** prácticamente todas las pantallas (`strings.xml` solo tiene `app_name`)

Imposible localizar y más difícil mantener. **Fix:** mover los textos a `res/values/strings.xml`.

### 12. `versionCode = 1` fijo
**Dónde:** `app/build.gradle.kts`

Para publicar actualizaciones en Play Store hace falta incrementarlo en cada release
(puede automatizarse con `versionCode = versionMajor * 10000 + ...`).

### 13. Registro sin verificación de email
**Dónde:** `AuthViewModel.register()`

Se crea la cuenta y entra directo, sin `sendEmailVerification()`. Opcional: enviar el correo
de verificación y bloquear el acceso hasta verificar (reduce cuentas basura).

---

## 🟢 Prioridad baja (pulido)

### 14. Coil con crossfade siempre activo
**Dónde:** `core/coil/VividImageLoader.kt` → `.crossfade(true)`

En gama baja conviene `crossfade(false)` o un fade muy corto (menos GPU en listas largas).
El resto de la configuración (memoria 25%, disco 250 MB) ya está muy bien.

### 15. Percepción de carga en el login
Ya lo dejé ligero (sin blur, sin sombras, sin animaciones infinitas, `displaySmall` en vez
de `displayLarge`, colorScheme cacheado en `Theme.kt`, `adjustResize` para el teclado).
Si aún se siente lento en un teléfono viejo, lo siguiente es #1 (R8) y #5 (splash), que
afectan el arranque de toda la app, no solo el login.

### 16. Accesibilidad
- En el login dejé `contentDescription` en todos los iconos (ojo/cerrar/leading).
- En el resto de la app conviene revisar targets de toque ≥ 48dp y contraste.
- `IconButton` de 32dp del error del login es pequeño para accesibilidad; se puede subir a 40dp.

### 17. Edge-to-edge manual
**Dónde:** `theme/Theme.kt` pinta `statusBarColor`/`navigationBarColor` a mano.
Con `activity-compose` 1.9+ se puede usar `enableEdgeToEdge()` y simplificar (opcional).

### 18. Workarounds de build
`hilt.enableAggregatingTask = false`, `kotlin.incremental=false`, `javapoet` forzado y
`workers.max=1` son para estabilidad en CI (memoria 1 GB). En una PC con más RAM se pueden
relajar para builds locales más rápidos (opcional, no urgente).

---

## Resumen de lo que ya cambié (esta sesión)

| Archivo | Cambio |
|---|---|
| `presentation/auth/AuthScreen.kt` | Rediseño completo a Material You 3 (hero tonal, `FilledTextField` con iconos, toggle de contraseña, botones 52dp, error en `errorContainer`, logo G de Google en Canvas, transición ligera login/registro, scroll + `imePadding`). Lógica de auth y Google **intacta**. |
| `theme/Theme.kt` | `colorScheme` cacheado con `remember` (no se regenera la paleta dinámica en cada recomposición). |
| `AndroidManifest.xml` | `windowSoftInputMode="adjustResize"` para que el teclado nunca tape el formulario. |

*Verificado a mano contra las versiones del proyecto (Compose BOM 2024.12.01, Material3 1.3.1,
Kotlin 2.0.21): no cambia firmas públicas, no agrega dependencias y no toca el flujo de auth.*
