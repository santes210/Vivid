package com.vivid.app.presentation.explore

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vivid.app.util.Hashtags
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puente entre el resto de la app y Explorar.
 *
 * Tocar un `#tag` en un caption (feed, detalle) tiene que abrir Explorar
 * filtrado por ese tema. Explorar vive en otro destino de navegación con
 * su propio ViewModel; el tag pendiente se deja aquí (singleton) y
 * [ExploreViewModel] lo consume al aparecer. Complementa `search?tag=`
 * porque `restoreState` a veces ignora el argumento nuevo.
 */
@Singleton
class ExploreSession @Inject constructor() {

    private val _pendingTag = MutableStateFlow<String?>(null)
    val pendingTag: StateFlow<String?> = _pendingTag.asStateFlow()

    fun openTag(tag: String) {
        val normalized = Hashtags.normalize(tag)
        if (normalized.isNotEmpty()) _pendingTag.value = normalized
    }

    fun consume(): String? {
        val value = _pendingTag.value
        _pendingTag.value = null
        return value
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExploreSessionEntryPoint {
    fun exploreSession(): ExploreSession
}

@Composable
fun rememberExploreSession(): ExploreSession {
    val context = LocalContext.current
    return remember(context) { exploreSessionFrom(context) }
}

internal fun exploreSessionFrom(context: Context): ExploreSession {
    return EntryPointAccessors.fromApplication(
        context.applicationContext,
        ExploreSessionEntryPoint::class.java
    ).exploreSession()
}
