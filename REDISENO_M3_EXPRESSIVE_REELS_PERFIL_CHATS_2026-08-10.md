# Rediseño Material You 3 Expressive — Reels, Perfil y Chats — 2026-08-10

> Implementado sobre M3 1.4.0 estable + Compose BOM 2025.04.01 (sin cambios de
> dependencias). Tres pantallas modernizadas: Reels, Perfil y Chats.

---

## 7. Reels — inmersivo y oscuro (sin fondo dinámico)

| Archivo | Cambio |
|---|---|
| `presentation/reels/ReelsScreen.kt` | • **Controles en contenedores negros translúcidos consistentes**: los botones de acción (like, comentarios, compartir, silenciar) ahora viven cada uno dentro de un círculo `Color.Black.copy(alpha = 0.32f)` de 46dp; el header "Reels" es una píldora translúcida compacta; la info del creador va en una tarjeta translúcida redondeada (18dp). |
| | • **Mejor contraste**: contenedores más opacos (0.32–0.5), texto blanco puro y degradado inferior más fuerte (0.9). |
| | • **Información del creador agrupada**: avatar con anillo blanco + username + botón de seguir + caption (máx. 2 líneas) dentro de un solo contenedor (antes estaban flotando por separado). |
| | • **Botón de seguir más claro**: píldora blanca "Seguir" con texto negro; al seguir se vuelve translúcida con "Siguiendo"/"Solicitado". |
| | • **Bottom sheet de comentarios totalmente M3**: se reemplazó el `AlertDialog` por `ModalBottomSheet` con drag handle, título + subtítulo, lista de comentarios y composer M3 con botón de envío circular (respeta teclado con `imePadding`). |
| | • **Indicador de pausa menos grande**: de ~92dp a 48dp (icono 24dp). |
| | • **Menos texto/íconos flotantes simultáneos**: al pausar se ocultan la info del creador y la barra de acciones; corazón de doble tap reducido a 84dp; se eliminó el caption "Sin descripción" cuando no hay texto. |

## 8. Perfil

| Archivo | Cambio |
|---|---|
| `presentation/profile/ProfileScreen.kt` | • **Header más limpio**: se eliminó la `Card` envolvente; layout plano con espaciado coherente. |
| | • **Avatar como hero**: 116dp con anillo degradado (primary → tertiary). |
| | • **Estadísticas en grupo coherente**: fila de 4 stats dentro de una `Surface` tonal (`surfaceContainer`, 20dp) con separadores verticales. |
| | • **Editar perfil como `FilledTonalButton`** de ancho completo. |
| | • **Acciones secundarias en menú**: en el perfil propio, Ajustes y Cerrar sesión se agrupan en el menú ⋮ (antes eran 2 íconos en la top bar). |
| | • **Tabs modernas**: `PrimaryTabRow` (píldora indicadora M3 2024/Expressive) sin divider, con íconos + etiquetas. |
| | • **Grid sin tarjetas**: celdas planas con radio 6dp y separación de 2dp. |
| | • **Skeleton**: header con placeholders pulsantes (avatar, nombre, @usuario, stats, botón) y celdas de skeleton en la grid mientras no llega la primera snapshot de Firestore. Respeta `LocalVividAnimationsEnabled`. |

## 9. Chats

| Archivo | Cambio |
|---|---|
| `presentation/messages/ChatScreen.kt` | • **Burbujas tonales del usuario**: las mías usan `primaryContainer`/`onPrimaryContainer`; las del otro usan `surfaceContainerHighest`/`onSurface`. Se eliminaron los degradados y las sombras (0 elevación). |
| | • **Forma por grupo de mensajes**: radio exterior 20dp, esquina interna 6dp según inicio/fin de grupo, y separación vertical 10dp entre grupos / 1dp dentro del grupo. |
| | • **Composer tipo dock**: barra inferior de ancho completo (`surfaceContainer`) anclada al borde, sin tarjeta flotante ni sombra; campo píldora relleno (`surfaceContainerHighest`), botón adjuntar tonal y envío/voz. Incluye `imePadding()` para el teclado. |
| | • **Adjuntos en bottom sheet**: el menú "+" ahora es un `ModalBottomSheet` M3 ("Adjuntar") con Enviar foto / Ver perfil / Copiar chat ID. |
| | • **Reacciones en menú expresivo**: barra de reacciones con `surfaceContainerHighest` plana (28dp), sin sombras ni bordes, emojis 27sp. |
| | • **Separadores de fecha discretos**: solo texto centrado pequeño (sin píldora ni sombra). |
| | • **Estados enviado/recibido/leído más pequeños**: ticks de 12dp, hora a 9sp; leído en `tertiary` (se ve en ambos temas). |
| | • **Mejor visualización de notas de voz**: waveform que avanza con la reproducción (barras pintadas según progreso real del `ExoPlayer`), botón play/pausa tonal de 40dp y contador "transcurrido / total" mientras suena. |

---

## Verificación recomendada

```bash
cd vivid-app
./gradlew :app:compileDebugKotlin --stacktrace --no-daemon
```

> Nota: en este entorno no hay JDK/Android SDK, así que la compilación debe
> correrse en CI (GitHub Actions) o localmente. Las APIs usadas existen en
> material3 1.4.0 (`PrimaryTabRow`, `ModalBottomSheet`, `rememberModalBottomSheetState`,
> `VerticalDivider`) y en Compose Foundation 1.8 (`imePadding`, `mutableFloatStateOf`).
