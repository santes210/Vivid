# Fixes UI + Feed + Perfil + Ajustes - 2026-06-28

## Reels UI mejorada

- Overlay superior tipo encabezado.
- Tap sobre el reel para pausar/reproducir.
- Indicador de carga mientras el video prepara.
- Botón de play grande cuando está pausado.
- Respeta el ajuste `autoplayReels` guardado en Firestore.

## Feed actualizado con videos

- El feed de Inicio ahora mezcla `posts` con `reels`.
- Los reels se muestran como video reproducible en el feed.
- Los likes de reels actualizan la colección `reels`.
- Respeta el ajuste `showReelsInFeed`: si está apagado, Inicio solo muestra posts.

## Perfil actualizado con videos nuevos

- El perfil ahora escucha `posts` y `reels` en tiempo real.
- La grid muestra fotos y reels juntos ordenados por fecha.
- Los reels se identifican con icono de play.
- Al abrir un reel desde el perfil se reproduce el video.
- El contador muestra `Posts/Reels`.

## Ajustes con más funciones funcionales

Agregado:

- Verificar correo: envía email de verificación.
- Cambiar contraseña: envía email de reset.
- Autoplay en Reels: guarda preferencia y Reels la respeta.
- Mostrar reels en feed: guarda preferencia y Feed la respeta.
- Ahorro de datos: guarda preferencia en Firestore.
- Resumen de contenido: fotos, reels, seguidores, siguiendo, etc.
- Limpiar caché local: borra temporales.
- Exportar resumen de cuenta: copia datos al portapapeles.

## Verificación

```bash
cd vivid-app
./gradlew :app:compileDebugKotlin --stacktrace --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.
