package com.vivid.app.theme

/**
 * Documentación del sistema de diseño de Vivid.
 *
 * Vive en código (y no en un `.md`) por la convención del repo: ver
 * `.gitignore` → "Sin documentación .md en el repo".
 *
 * ---
 * ## 1. Color — paleta "Vivid Sunset" + dinámico armonizado
 *
 * Antes la paleta de marca era, literalmente, la baseline de Material 3
 * (`0xFF6750A4`, `0xFFEADDFF`, `0xFF7D5260`…): la que trae cualquier plantilla
 * de Android Studio. Con ella, Vivid parecía una plantilla.
 *
 * Piezas:
 *  - `scripts/generate_vivid_palette.py` — generador, **fuente de verdad**.
 *  - [VividBrandColors] / [VividBrandColorsDark] — salida generada, 36 roles.
 *  - [VividAccentColors] + [VividAccents] — acentos de producto.
 *  - `theme/ColorHarmony.kt` — `Color.harmonizeWith` y [VividAccents].
 *
 * Semillas en OkLCh (tono = `L*` de CIELAB, el mismo eje que HCT):
 *
 * | Rampa           | Matiz | Croma | Intención                          |
 * |-----------------|-------|-------|------------------------------------|
 * | primary         | 6°    | 0.230 | magenta-coral: la marca            |
 * | secondary       | 8°    | 0.075 | rosa apagado: soporte              |
 * | tertiary        | 68°   | 0.150 | ámbar: atardecer, celebración      |
 * | error           | 27°   | 0.160 | rojo, distinto del magenta de marca|
 * | neutral         | 40°   | 0.008 | gris CÁLIDO: las fotos respiran    |
 * | neutral variant | 30°   | 0.022 | contornos y superficies variantes  |
 *
 * Regenerar tras cambiar las semillas:
 * ```
 * python3 scripts/generate_vivid_palette.py > \
 *   vivid-app/app/src/main/java/com/vivid/app/theme/VividColors.kt
 * ```
 *
 * Con color dinámico (Android 12+, `SettingsManager.dynamicColorEnabled`) el
 * esquema sale del wallpaper y los acentos de producto NO se sustituyen: se
 * armonizan girando su matiz como mucho 15° hacia el color del sistema. Se
 * leen siempre con `LocalVividAccents.current`, nunca importando
 * [VividAccentColors] a pelo (así funcionan igual en marca, en dinámico y en
 * un `@Preview`).
 *
 * ---
 * ## 2. Material 3 Expressive — APIs reales, no imitaciones
 *
 * El KDoc de la app prometía "M3 Expressive" en todas partes, pero material3
 * estable (1.4.0) **no tiene** esas APIs: viven en la rama [MATERIAL3_VERSION]
 * (`1.5.0-alpha`). Lo que había era una emulación a mano: `tween(320)` sueltos,
 * `RoundedCornerShape` bautizados "expressive" y `CircularProgressIndicator`
 * con `strokeWidth`.
 *
 * Ahora `gradle/libs.versions.toml` fija material3 fuera del Compose BoM (el
 * BoM resolvería 1.4.x) y el módulo opta por
 * `ExperimentalMaterial3ExpressiveApi` y `ExperimentalSharedTransitionApi`.
 *
 * | API                                              | Dónde se usa                              |
 * |--------------------------------------------------|-------------------------------------------|
 * | `MaterialExpressiveTheme` + `MotionScheme.expressive()` | `theme/Theme.kt`                   |
 * | `MotionScheme` (spatial / effects)               | `theme/Motion.kt` → [VividMotion]         |
 * | `MaterialShapes` (las 35) + `Morph`              | `theme/Shape.kt` → [VividMaterialShapes], `ui/components/VividMorphShape.kt` |
 * | `LoadingIndicator` / `ContainedLoadingIndicator` | estados, Explorar, detalle, perfil, feed  |
 * | `ButtonGroup` (+ overflow)                       | filtros por tema de Explorar              |
 * | `HorizontalFloatingToolbar`                      | acciones del detalle de publicación       |
 * | `WideNavigationRail`                             | navegación en tabletas                    |
 *
 * **Riesgo asumido:** es alpha, las firmas pueden cambiar entre versiones. Los
 * usos están concentrados a propósito en esos siete archivos. Plan B si una
 * API desaparece: volver a 1.4.0 y sustituir `MaterialExpressiveTheme` →
 * `MaterialTheme`, `LoadingIndicator` → `CircularProgressIndicator`,
 * `ButtonGroup` → `LazyRow` de `FilterChip`, `WideNavigationRail` →
 * `NavigationRail`, `HorizontalFloatingToolbar` → `BottomAppBar`.
 * [VividMotion] ya aísla al resto de la app del `MotionScheme`.
 *
 * ### Las 35 formas
 *
 * `MaterialShapes` trae 35 polígonos (Circle, Square, Slanted, Arch,
 * SemiCircle, Oval, Pill, Triangle, Arrow, Fan, Diamond, ClamShell, Pentagon,
 * Gem, Sunny, VerySunny, Cookie 4/6/7/9/12, Ghostish, Clover 4/8, Burst,
 * SoftBurst, Boom, SoftBoom, Flower, Puffy, PuffyDiamond, PixelCircle,
 * PixelTriangle, Bun, Heart). Están todas en [VividMaterialShapes.Catalog] y
 * el preview `MaterialShapesCatalogPreview` las pinta con su nombre.
 *
 * Lo importante no es el catálogo sino que son `RoundedPolygon`: se
 * **interpolan**. `ui/components/VividMorphShape.kt` expone
 * `rememberVividMorph(start, end, progress)` y `pressMorphShape(interaction)`,
 * usados en el botón de Crear y en el FAB del rail (círculo → galleta al
 * pulsar). La app consume roles ([VividMaterialShapes] `Like`, `Celebration`,
 * `EmptyStateContainer`…), nunca `MaterialShapes.X` directo.
 *
 * ---
 * ## 3. Continuidad — transiciones compartidas, hápticos y previews
 *
 * **Transiciones** (`ui/motion/VividSharedTransition.kt`):
 * `VividSharedTransitionHost` envuelve el `NavHost`; los destinos que
 * participan se declaran con `sharedComposable`; los elementos se marcan con
 * `Modifier.vividSharedElement(VividSharedKeys.…)`, que se degrada a no-op sin
 * scope (previews, tests) o con movimiento reducido. Pares implementados:
 * miniatura del grid ⇄ imagen del detalle, avatar de feed/chat/buscador ⇄
 * avatar del perfil, nombre de usuario ⇄ título del perfil.
 *
 * **Hápticos** (`ui/haptics/VividHaptics.kt`): vocabulario cerrado
 * (`toggleOn`, `toggleOff`, `confirm`, `reject`, `tick`, `longPress`,
 * `gestureThreshold`) en vez de llamadas sueltas. Respeta
 * `Ajustes → Apariencia → Respuesta háptica`.
 *
 * **Like** (`ui/components/VividLikeButton.kt`): rebote con `spring` + anillo,
 * solo cuando el gesto lo hace el usuario; háptico; color de acento
 * armonizado; y `DoubleTapLikeBox` para el doble toque sobre la foto (nunca
 * quita el like).
 *
 * **Espaciado** (`theme/VividSpace.kt` → [VividSpace]): ritmo 4 / 8 / 12 /
 * 16 / 24 / 32 / 48. Las pantallas no inventan `Spacer(Modifier.height(16.dp))`
 * cuando cabe en la escala: usan el token. Los 2 dp de un hairline y los
 * paddings intermedios (6 / 10 / 14…) se quedan literales. Los radios van
 * en [VividExpressiveShapes], nunca un `RoundedCornerShape` literal a mano.
 *
 * **Previews** (`ui/preview/`): `@VividPreview` (claro + oscuro) y
 * `@VividPreviewA11y` (además fuente 150 % y ancho 320 dp), con
 * `VividPreviewSurface`. Hay previews de like, avatares, estados, paleta,
 * `PostCard`, cabecera de perfil, chat, ajustes, barra inferior y rail.
 * Regla: si un composable no se puede previsualizar sin Firebase, casi siempre
 * es que tiene una dependencia de más.
 *
 * ---
 * ## Estado de verificación
 *
 * Verificado en CI (PR #42, workflow `Build Vivid APK`): `assembleDebug` y
 * `lintVitalRelease` en verde con material3 [MATERIAL3_VERSION].
 *
 * Dos trampas que costó descubrir y que conviene revisar en cada subida de
 * alpha, porque son cambios silenciosos de API:
 *
 *  1. **`ButtonGroup`**: el estado del menú de overflow se llama `isShowing`.
 *     Se llamaba `isExpanded` y lo renombraron en 1.5.0-alpha06.
 *  2. **`RoundedPolygon.toShape()` de material3 es `@Composable`**, así que no
 *     sirve para constantes de un `object` ni dentro de `drawBehind`. Por eso
 *     existe `theme/PolygonShapes.kt` (`toVividShape()`), que además normaliza
 *     con `calculateBounds()` en vez de asumir el espacio del polígono.
 *
 * Al cambiar de versión de material3:
 * ```
 * cd vivid-app && ./gradlew :app:assembleDebug && ./gradlew :app:lintVitalRelease
 * ```
 */
object VividDesignSystem {

    /**
     * Versión de material3 de la que dependen las APIs Expressive.
     * Debe coincidir con `material3` en `gradle/libs.versions.toml`.
     */
    const val MATERIAL3_VERSION: String = "1.5.0-alpha26"

    /** Nombre de la paleta de marca, para mostrar en Ajustes → Acerca de. */
    const val PALETTE_NAME: String = "Vivid Sunset"
}

