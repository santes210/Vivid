package com.vivid.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.VividTheme

/**
 * Andamiaje de previews de Vivid.
 *
 * Por qué importa: sin `@Preview` la única forma de ver un cambio de UI es
 * compilar, instalar y navegar hasta la pantalla — con Firebase de por medio,
 * sesión iniciada y datos reales. Eso hace que nadie itere sobre el diseño y
 * que los estados raros (texto larguísimo, fuente al 150 %, tema oscuro,
 * pantalla estrecha) no se miren nunca hasta que un usuario los reporta.
 *
 * Reglas de la casa:
 *   - Los previews van sobre composables **sin ViewModel ni Firebase**: si un
 *     componente no se puede previsualizar, normalmente es que le sobra
 *     dependencia, y eso también es una señal de diseño.
 *   - Siempre dentro de [VividPreviewSurface], que aplica el tema de marca
 *     (`dynamicColor = false`: en el render no hay wallpaper del que tirar).
 *   - Usar las anotaciones múltiples de abajo en vez de copiar `@Preview`:
 *     un componente que solo se ve bien en claro y con fuente normal no está
 *     terminado.
 */

/** Claro + oscuro de una tacada. El mínimo aceptable para un componente. */
@Preview(name = "Claro", showBackground = true)
@Preview(
    name = "Oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class VividPreview

/** Claro, oscuro, fuente grande y pantalla estrecha: revisión de accesibilidad. */
@Preview(name = "Claro", showBackground = true)
@Preview(
    name = "Oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Fuente 150 %", showBackground = true, fontScale = 1.5f)
@Preview(name = "Pantalla estrecha", showBackground = true, widthDp = 320)
annotation class VividPreviewA11y

/** Envoltorio con el tema de marca, superficie y un poco de aire. */
@Composable
fun VividPreviewSurface(
    // Por defecto sigue al uiMode del @Preview: así una sola función cubre
    // claro y oscuro sin duplicar código.
    darkTheme: Boolean = isSystemInDarkTheme(),
    padding: Int = 16,
    content: @Composable () -> Unit
) {
    VividTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(padding.dp)) { content() }
        }
    }
}
