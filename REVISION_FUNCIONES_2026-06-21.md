# Revisión de funciones - 2026-06-21

## Corregido en esta ronda

### Stories
- Ya se pueden subir stories desde `CreatePostScreen`.
- Las stories duran 24 horas.
- Las stories privadas solo son visibles para:
  - el dueño de la cuenta
  - seguidores del dueño
- `StoriesTray` y `StoryViewerRoute` ya filtran stories según privacidad.
- `StoryViewerScreen` ya soporta `mediaBase64`, `avatarBase64` y caption.

### Ajustes
- Privacidad de cuenta ahora también responde al toque en toda la fila.
- Verificar correo: funcional.
- Compartir app/proyecto: funcional.
- Ajustes de la app: funcional.
- Limpiar stories vencidas: funcional.

### Feed
- Comentarios funcionales con Firestore en subcolección `posts/{postId}/comments`.

### Reels
- El botón lateral ahora pausa/reanuda.
- `ReelsViewModel.uploadReel(...)` ya tiene implementación real con Firebase Storage + Firestore.

### Cámara / Crear
- Cámara devuelve foto a `CreatePostScreen`.
- Galería funciona.
- Crear ahora permite elegir entre `Publicación` y `Story`.

### Código pendiente detectado y resuelto
- `FeedViewModel.likePost(...)` ya no está vacío.

## Observaciones
- La compilación `:app:compileDebugKotlin` sí pasa.
- En el sandbox local, `assembleDebug` llega muy lejos pero el proceso puede morir al final por limitaciones del entorno.
- Para GitHub Actions, el proyecto quedó más listo que antes para generar APK.
