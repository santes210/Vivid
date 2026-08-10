# Vivid → Material 3 Expressive (upgrade 1.5.0-alpha23)

Fecha: 2026-08-10
Estado: **código aplicado — falta validar el build en CI/local** (este entorno no tiene JDK ni Android SDK).

> Antes: la app estaba en **Material 3 1.4.0 estable** y, aunque el tema se llamaba
> "Expressive", **no usaba los APIs Expressive reales**. Eso cambió en este upgrade.

---

## 1. Diagnóstico: ¿qué faltaba para ser "Expressive"?

Los APIs que dan "figuras y animaciones personales" de Material 3 Expressive son:

1. **MotionScheme** — físicas de muelle (springs con rebote) en vez de animaciones de duración + easing.
2. **Shape morphing** — los componentes cambian su forma al pulsar/activar (círculo → squircle → píldora).
3. **Tipografía *Emphasized*** — variantes `…Emphasized` para jerarquía editorial.
4. **Componentes nuevos**: `LoadingIndicator`/`ContainedLoadingIndicator`, `FilledTonalIconToggleButton`, listas segmentadas expresivas (`SegmentedListItem`), top bars expresivas (`LargeFlexibleTopAppBar`, etc.), `ButtonGroup`, `FloatingToolbar`.

**El problema:** todo eso vive SOLO en la rama **`1.5.0-alpha`** de `androidx.compose.material3`
(marcado `@ExperimentalMaterial3ExpressiveApi`). **No existe en 1.4.0 estable** — la rama
`1.4.0` fue congelada y los APIs Expressive se movieron a `1.5.0-alpha`. Por eso, en 1.4.0,
usar `MotionScheme.expressive()`, `…Emphasized`, `LoadingIndicator`, etc. **da error de compilación.**

Y un salto a `1.5.0-alpha` no es "cambiar una versión": verifiqué los POM en Google Maven y
`1.5.0-alpha23/25` exige **Compose 1.12 (beta)** y **Kotlin 2.1.20** (stdlib). Eso arrastra a
todo el toolchain (Kotlin → KSP → Room/Hilt). Por eso el upgrade es coordinado.

---

## 2. Versiones aplicadas (coordinadas y coherentes)

| Artefacto | Antes | Ahora | Por qué |
|---|---|---|---|
| `material3` | `1.4.0` | `1.5.0-alpha23` | Es donde viven los APIs Expressive. Elegí **alpha23** (la que mencionaste): ya gradúa listas y top bars expresivas a no-experimental. La alpha más reciente es **alpha25**. |
| Compose `ui`/`ui-graphics`/`ui-tooling`/`ui-tooling-preview`/`ui-test-*` | BOM `2025.04.01` (Compose 1.8) | `1.12.0-alpha03` (explicita) | Requisito de material3 `1.5.0-alpha23` (lo confirman los POM). |
| `kotlin` (y plugin Compose) | `2.0.21` | `2.1.20` | stdlib que exige material3 `1.5.0-alpha23`. |
| `ksp` | `2.0.21-1.0.25` | `2.1.20-1.0.32` | **KSP1** para Kotlin 2.1.20 → mantiene compatibilidad con **Room 2.6.1** y **Hilt 2.51.1**. No usé KSP2 (`2.1.20-2.0.x`) a propósito: Room 2.6.1 no soporta KSP2. |
| `agp` | `8.7.3` | `8.7.3` | Sin cambio (compatible con Gradle 8.9 + Kotlin 2.1.20). |
| `composeOptions.kotlinCompilerExtensionVersion` | `1.5.15` | **eliminado** | Con el plugin `org.jetbrains.kotlin.plugin.compose`, el compilador queda ligado a la versión de Kotlin (2.1.20). Dejar `1.5.15` rompería el build. |

Se añadió el opt-in global en `app/build.gradle.kts`:

```kotlin
"-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
```

---

## 3. Cambios de código (tema)

| Archivo | Qué se hizo |
|---|---|
| `gradle/libs.versions.toml` | Versiones coordinadas (tabla anterior) + `composeVersion = "1.12.0-alpha03"`. |
| `app/build.gradle.kts` | Opt-in Expressive; eliminado `composeOptions`. |
| `theme/Motion.kt` | Nuevo `VividMotionScheme = MotionScheme.expressive()` (springs con rebote). |
| `theme/Theme.kt` | `MaterialTheme(..., motionScheme = VividMotionScheme)`. **Este es el cambio de mayor impacto**: aplica el movimiento Expressive a todos los componentes M3 de la app de golpe. |
| `theme/Type.kt` | Variantes `…Emphasized` para toda la escala (display/headline/title/body/label). |
| `theme/Shape.kt` | Tokens de morphing (`Pill`, `Squircle`, `IconChecked`, `ButtonMorph*`, etc.). |

El `LocalVividAnimationsEnabled` sigue disponible para respetar reducción de movimiento.

---

## 4. ⚠️ Riesgos que hay que validar al compilar (CI/local)

Este entorno no puede compilar. Corre:

```bash
cd vivid-app
./gradlew :app:compileDebugKotlin --stacktrace
```

Puntos a revisar si algo falla:

1. **Room 2.6.1 + KSP 2.1.20**: uso KSP1 (`2.1.20-1.0.32`) para mantenerlo. Si Room diera error de KSP,
   la solución es subir `room` a `2.7.x` (cambia mínimamente las APIs de `@Database`/DAO en casos raros).
2. **Hilt 2.51.1**: debería funcionar con KSP1 2.1.20. Si fallara, subir `hilt` a `2.56+`.
3. **Mezcla de versiones Compose**: material3 `1.5.0-alpha23` trae foundation/ui/runtime `1.12.0-alpha03`;
   `material-icons-extended` sigue en `1.7.8` (del BOM) — es normal y compila (los icons solo aportan datos).
4. Si querías la **alpha más nueva (alpha25)**: cambia `material3 = "1.5.0-alpha25"` en el toml.
   Solo añade correcciones y un par de renombres (`FilledTonalToggleButton`, `shapesFor`) que esta app **no usa**, así que es seguro a posteriori.

---

## 5. Cómo llevar Expressive a las pantallas (paso a paso)

### 5.1 Loading / skeletons → `LoadingIndicator` (reemplaza `CircularProgressIndicator`)
En pantallas de carga cortas (<5 s). Ejemplo con `LocalVividAnimationsEnabled`:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VividLoading() {
    if (!LocalVividAnimationsEnabled.current) return  // estado final / nada
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // p.ej. un bucle; si no, LoadingIndicator con progress = { 0.5f } estático
    }
    LoadingIndicator(progress = { progress.value }, modifier = Modifier.size(40.dp))
}
```

### 5.2 Like / acciones → botón de icono con morphing (`FilledTonalIconToggleButton`)
Ideal para el corazón de doble tap en **Reels** y **Feed**:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VividLikeButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconToggleButtonShapes(
            shape = VividExpressiveShapes.IconResting,   // CircleShape
            checkedShape = VividExpressiveShapes.IconChecked,
            pressedShape = VividExpressiveShapes.IconPressed
        )
    ) {
        Icon(if (checked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null)
    }
}
```

### 5.3 Listas → `SegmentedListItem` (lista expresiva, ya no-experimental en alpha23)
Útil para ajustes, comentarios agrupados y el composer de **Chats**.

### 5.4 Top bars → variantes expresivas (`LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`)
Para **Perfil** (hero) y **Reels**.

### 5.5 Tipografía Emphasized en momentos hero
Usa `MaterialTheme.typography.titleLargeEmphasized` / `headlineMediumEmphasized` en splash,
perfil y títulos de Reels.

### 5.6 Reducción de movimiento
Envuelve lo decorativo con `if (LocalVividAnimationsEnabled.current)`. Y si implementas el
switch, cambia `VividMotionScheme` por `MotionScheme.standard()` cuando esté desactivado.

---

## 7. Formas nuevas aplicadas a toda la app (2026-08-10)

Además del motion scheme y la tipografía, se **sustituyeron las esquinas redondeadas planas
por squircles** (superelipses) en todo el árbol de UI:

- **`theme/Shape.kt`**: nuevo `SquircleShape(exponent = 4f)` — un `Shape` que dibuja una
  superelipse real (curvatura continua, la silueta característica de M3 Expressive) en lugar
  de esquinas redondeadas con tramos rectos. El sistema `VividShapes` y todos los tokens de
  `VividExpressiveShapes` (cards, botones, campos, modales, morphing) ahora usan squircles.
- **Pantallas (16 archivos + navegación)**: se reemplazó `RoundedCornerShape(<n>.dp)` por
  `SquircleShape()` en Feed, Reels, Profile, Chats, Stories, Create, Settings, y la barra de
  navegación (botón "Crear" y pestañas). Se añadió `import com.vivid.app.theme.SquircleShape`
  a cada archivo y se limpiaron los imports sin uso.
- **Excepciones intencionales** (se conservan `RoundedCornerShape`):
  - Burbujas de chat con esquinas asimétricas por grupo de mensajes.
  - Bottom sheets / modales con solo las esquinas superiores redondeadas
    (`RoundedCornerTop(28.dp)`).
  - `RoundedCornerShape(50)` (porcentual, ya es un círculo) en VideoTrimmer.

Resultado: tarjetas, botones, chips, campos, imágenes y skeletons ahora tienen la silueta
"squircle" suave y continua, no solo esquinas redondeadas — el rasgo visual que distingue
Expressive del M3 clásico.

---

## 8. Librería de formas rica (2026-08-10) — vocabulario Expressive

Como PixelPlayer, se añadió un **vocabulario amplio de formas** en
`theme/ExpressiveGeometry.kt` (todas son `Shape` reales, se usan en `Surface`,
`clip(...)`, `Button`, etc.):

| Forma | Clase | Tokens en `VividExpressiveShapes` |
|---|---|---|
| **Squircle** (superelipse, curvatura continua) | `SquircleShape(exponent)` | `Squircle`, `SquircleSoft`, `SquircleSharp`, `SquircleHero`, `SquircleTight` |
| **Estrella** de N puntas | `StarShape(points, innerRatio)` | `Star`, `StarSharp`, `StarSoft`, `StarFour`, `StarSix`, `Sparkle` |
| **Ráfaga / sol** | `BurstShape(rays, innerRatio)` | `Burst`, `BurstFine`, `BurstCoarse` |
| **Pétalo / gota** | `TeardropShape(orientation)` | `DropDown`, `DropUp`, `DropRight`, `DropLeft`, `Petal` |
| **Corazón** | `HeartShape` | `Heart` |
| **Diamante / gema** | `DiamondShape(exponent)` | `Diamond`, `DiamondSharp`, `DiamondSoft` |
| **Festón / olas** (borde ondulado) | `ScallopShape(waves, onTop)` | `Scallop`, `ScallopBottom`, `ScallopWave` |
| **Diente de sierra** (zigzag) | `SawtoothShape(teeth, onTop)` | `Sawtooth`, `SawtoothBottom` |
| **Píldora** (stadium) | `PillShape` (foundation) | `Pill`, `PillSmall`, `TabPill` |

**Aplicadas en la UI:**
- Todo el sistema de formas del tema (cards, botones, campos, chips, modales) → squircles.
- Estados vacíos (`VividEmptyState`) → squircle hero + **chispa de estrella** decorativa.
- Estados de carga (`VividLoadingState`) → spinner dentro de un **squircle hero que "respira"** (pulso).
- Barras de búsqueda (Search/Explore) → squircle (`SearchBar` token).

El resto de tokens (estrellas, ráfagas, pétalos, corazones, diamantes, festones) quedan
listos para usar en cualquier pantalla: p. ej. `Heart` para el "like", `Burst` para el botón
Crear, `Scallop` para un header de Reels, `Diamond` para un avatar destacado.

---

## 6. Referencia
- Release notes Compose Material 3 (alpha25 = última): https://developer.android.com/jetpack/androidx/releases/compose-material3
- Documentación Expressive en Compose: `@ExperimentalMaterial3ExpressiveApi` + `MotionScheme`, `LoadingIndicator`, `FilledTonalIconToggleButton`.
