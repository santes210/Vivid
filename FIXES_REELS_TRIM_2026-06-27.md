# Fixes Reels + Trim - 2026-06-27

## 1) Los videos sí subían pero no aparecían en Reels

Se corrigió el flujo de carga de Reels:

- `ReelsViewModel.refreshReels()` ahora es público y recarga desde Firestore.
- `ReelsScreen` refresca automáticamente al volver a la pantalla (`ON_RESUME`).
- Se llena también `thumbnailUrl` en el modelo `Reel`.
- Al publicar se guarda `thumbnailStorageKey` para renovar miniaturas firmadas.
- Si falla actualizar `reelsCount`, ya no se marca como fallo la publicación cuando el video y metadata ya fueron guardados.
- `firestore.rules` ahora permite actualizar `reelsCount` como contador social.

Archivos principales:

- `vivid-app/app/src/main/java/com/vivid/app/presentation/reels/ReelsViewModel.kt`
- `vivid-app/app/src/main/java/com/vivid/app/presentation/reels/ReelsScreen.kt`
- `vivid-app/app/src/main/java/com/vivid/app/presentation/create/CreateReelViewModel.kt`
- `firestore.rules`

## 2) Al cortar el video y tocar listo/continuar se perdía el video

Causa: la pantalla de trim solo hacía `popBackStack()`; no guardaba el video recortado ni preservaba bien el URI seleccionado.

Se corrigió:

- `CreateReelScreen` guarda el URI con `rememberSaveable`, así no se pierde al navegar.
- `VideoTrimmer` ahora se ejecuta al confirmar.
- Se guarda el archivo recortado en cache y se devuelve como `trimmedVideo` a la pantalla Crear Reel.
- Al volver de Trim, el preview queda con el video recortado, sin tener que buscarlo otra vez.

Archivos principales:

- `vivid-app/app/src/main/java/com/vivid/app/presentation/create/CreateReelScreen.kt`
- `vivid-app/app/src/main/java/com/vivid/app/navigation/VividNavigation.kt`
- `vivid-app/app/src/main/java/com/vivid/app/presentation/create/CreateReelViewModel.kt`

## Verificación

Se verificó compilación Kotlin con:

```bash
cd vivid-app
./gradlew :app:compileDebugKotlin --stacktrace --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

El assemble completo en este sandbox se queda sin tiempo/memoria en D8, pero Kotlin ya compila y los cambios de código pasan esa fase.
