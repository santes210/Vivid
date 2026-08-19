package com.vivid.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Fuente única de verdad para idioma y tamaño de fuente in-app.
 *
 * Por qué un manager centralizado en lugar de AppCompatDelegate:
 *   - AppCompatDelegate.setApplicationLocales arrastra la dependencia
 *     androidx.appcompat, que en este proyecto (Compose puro) introduciría
 *     un conflicto de temas con Theme.Vivid. localeConfig de Android 13
 *     requiere un XML de recursos, lo que agrega fricción para dos idiomas.
 *   - Esta app solo necesita 2 idiomas (es/en) y un override ligero de
 *     escala tipográfica, no el sistema completo de per-app locales de A13.
 *   - Usar Locale.setDefault + Configuration.setLocale es estable, sin
 *     dependencias extra, y funciona del API 26 al 35.
 *
 * Para agregar un tercer idioma: añadir el strings.xml correspondiente y
 * extender SUPPORTED_LANGS.
 */
object LocaleManager {
    private const val PREFS_NAME = "vivid_locale"
    private const val KEY_LANG = "selected_lang"
    private const val KEY_FONT_SCALE = "font_scale"

    /** Códigos BCP-47 soportados. El primero es el default. */
    val SUPPORTED_LANGS = listOf("es", "en")
    const val DEFAULT_LANG = "es"
    const val SYSTEM_LANG = "" // cadena vacía = usar el locale del sistema

    /** Escala tipográfica que se aplica a la CompositionLocal de tipografía. */
    val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.30f)
    const val DEFAULT_FONT_SCALE = 1.0f

    var selectedLang: String by mutableStateOf(DEFAULT_LANG)
        private set
    var fontScale: Float by mutableStateOf(DEFAULT_FONT_SCALE)
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedLang = prefs.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
        fontScale = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
            .coerceIn(FONT_SCALES.min(), FONT_SCALES.max())
    }

    fun setLanguage(context: Context, lang: String) {
        val normalized = if (lang == SYSTEM_LANG) DEFAULT_LANG else lang
        selectedLang = normalized
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, normalized)
            .apply()
    }

    fun setFontScale(context: Context, scale: Float) {
        val clamped = scale.coerceIn(FONT_SCALES.min(), FONT_SCALES.max())
        fontScale = clamped
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE, clamped)
            .apply()
    }

    /**
     * Devuelve el [Locale] efectivo que debe usar la app. Si selectedLang
     * está vacío, devuelve el del sistema. Aplicar este locale a la
     * Configuration de cada Activity (ver [applyToActivity]).
     */
    fun currentLocale(): Locale = when (selectedLang) {
        SYSTEM_LANG, "" -> Locale.getDefault()
        else -> Locale(selectedLang)
    }

    /**
     * Hook a llamar en Activity.attachBaseContext() para que los recursos
     * se sirvan en el idioma seleccionado antes de que se cree la vista.
     */
    fun applyToActivity(base: Context): Context {
        val locale = currentLocale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.fontScale = fontScale
        return base.createConfigurationContext(config)
    }
}
