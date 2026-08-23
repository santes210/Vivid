package com.vivid.app.util

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object SettingsManager {
    private const val TAG = "SettingsManager"
    private const val PREFS_NAME = "vivid_settings"

    // ── Tema: claves canónicas ──
    // El tema se persiste con una clave estable e independiente del idioma
    // ("system"/"dark"/"light"), NUNCA con la etiqueta visible ("Oscuro",
    // "Dark"…). Antes se guardaba el literal en español y al cambiar el
    // idioma del sistema el modo oscuro dejaba de aplicarse. La etiqueta
    // localizada se resuelve en UI con [themeOptionLabelRes].
    const val THEME_SYSTEM = "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    val themeOptions = listOf(THEME_SYSTEM, THEME_DARK, THEME_LIGHT)

    /** Id de la semilla de marca por defecto (ver VividSeedPalette.SUNSET). */
    const val SEED_PALETTE_DEFAULT = "sunset"

    /** Normaliza cualquier valor legado (español/inglés) a su clave canónica. */
    fun normalizeThemeOption(raw: String?): String = when (raw) {
        THEME_DARK, "Oscuro", "Dark" -> THEME_DARK
        THEME_LIGHT, "Claro", "Light" -> THEME_LIGHT
        else -> THEME_SYSTEM
    }

    /** Recurso de la etiqueta localizada de una clave de tema. */
    @androidx.annotation.StringRes
    fun themeOptionLabelRes(option: String): Int = when (normalizeThemeOption(option)) {
        THEME_DARK -> com.vivid.app.R.string.theme_dark
        THEME_LIGHT -> com.vivid.app.R.string.theme_light
        else -> com.vivid.app.R.string.theme_system
    }

    // Keys
    private const val KEY_THEME = "selected_theme"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_SEED_PALETTE = "seed_palette"
    private const val KEY_AMOLED = "amoled_black"
    private const val KEY_SMOOTH_ANIMATIONS = "smooth_animations"
    private const val KEY_HAPTICS = "haptic_feedback"
    private const val KEY_AUTOPLAY_REELS = "autoplay_reels"
    private const val KEY_SHOW_REELS_IN_FEED = "show_reels_in_feed"
    private const val KEY_HD_UPLOADS = "hd_uploads"
    private const val KEY_DATA_SAVER = "data_saver"
    private const val KEY_OFFENSIVE_WORDS = "offensive_words"
    private const val KEY_HIDE_LIKES = "hide_likes"
    private const val KEY_NOTIFY_LIKES_COMMENTS = "notify_likes_comments"
    private const val KEY_NOTIFY_FOLLOWERS = "notify_followers"
    private const val KEY_NOTIFY_DM = "notify_dm"
    private const val KEY_NOTIFY_STORY_REMINDERS = "notify_story_reminders"
    private const val KEY_CREATOR_DASHBOARD = "creator_dashboard"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_ACTIVITY_STATUS = "activity_status"
    private const val KEY_2FA = "two_factor_auth"
    private const val KEY_CACHE_SIZE = "cache_size_mb"
    private const val KEY_LAST_CACHE_CLEAR = "last_cache_clear"
    private const val KEY_PERM_ONBOARDING_DONE = "perm_onboarding_done"

    // Observable/reactive compose states
    var selectedThemeOption by mutableStateOf(THEME_SYSTEM)
        private set
    var dynamicColorEnabled by mutableStateOf(true)
        private set
    /**
     * Semilla de marca cuando NO hay color dinámico (id de
     * [com.vivid.app.theme.VividSeedPalette]). Se persiste el id estable
     * ("sunset", "ocean"…), nunca la etiqueta visible: igual que con el tema,
     * guardar el literal traducido rompe la preferencia al cambiar de idioma.
     */
    var seedPaletteId by mutableStateOf(SEED_PALETTE_DEFAULT)
        private set
    /**
     * Negro puro (#000000) en modo oscuro. En paneles OLED el píxel negro está
     * apagado: negro real, menos batería y ese look "AMOLED" que la gente pide
     * explícitamente. No hace nada en tema claro.
     */
    var amoledBlackEnabled by mutableStateOf(false)
        private set
    var smoothAnimationsEnabled by mutableStateOf(true)
        private set
    /**
     * Respuesta háptica en gestos y acciones (like, seguir, enviar, cambiar de
     * pestaña). Activada por defecto: en Android el háptico es parte del
     * lenguaje de interacción, no un adorno. Se puede apagar en
     * Ajustes → Apariencia para quien lo encuentre molesto o le consuma batería.
     */
    var hapticFeedbackEnabled by mutableStateOf(true)
        private set
    var autoplayReels by mutableStateOf(true)
        private set
    var showReelsInFeed by mutableStateOf(true)
        private set
    var hdUploadsEnabled by mutableStateOf(false)
        private set
    var dataSaverMode by mutableStateOf(false)
        private set
    var offensiveWordsFilter by mutableStateOf(true)
        private set
    var hideLikesCount by mutableStateOf(false)
        private set
    var notifyLikesComments by mutableStateOf(true)
        private set
    var notifyNewFollowers by mutableStateOf(true)
        private set
    var notifyDirectMessages by mutableStateOf(true)
        private set
    var notifyStoryReminders by mutableStateOf(true)
        private set
    var creatorDashboardEnabled by mutableStateOf(false)
        private set
    var downloadQualityOption by mutableStateOf("Alta (HD)")
        private set
    var activityStatusEnabled by mutableStateOf(true)
        private set
    var twoFactorAuthEnabled by mutableStateOf(false)
        private set
    var simulatedCacheSizeMB by mutableStateOf(0f)
        private set
    var lastCacheClearAt by mutableStateOf(0L)
        private set
    /**
     * Marca de si el usuario ya pasó por la pantalla de onboarding de
     * permisos. Se usa una sola vez por instalación / cuenta; se persiste
     * en SharedPreferences para sobrevivir reinicios y al cambio de cuenta.
     */
    var permissionsOnboardingCompleted by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Migración: builds anteriores guardaban la etiqueta en español
        // ("Sistema"/"Oscuro"/"Claro"). Se normaliza a la clave canónica y,
        // si cambió, se re-persiste para no volver a migrar.
        val rawTheme = prefs.getString(KEY_THEME, THEME_SYSTEM)
        selectedThemeOption = normalizeThemeOption(rawTheme)
        if (rawTheme != selectedThemeOption) {
            prefs.edit().putString(KEY_THEME, selectedThemeOption).apply()
        }
        dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        seedPaletteId = prefs.getString(KEY_SEED_PALETTE, SEED_PALETTE_DEFAULT)
            ?: SEED_PALETTE_DEFAULT
        amoledBlackEnabled = prefs.getBoolean(KEY_AMOLED, false)
        smoothAnimationsEnabled = prefs.getBoolean(KEY_SMOOTH_ANIMATIONS, true)
        hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTICS, true)
        autoplayReels = prefs.getBoolean(KEY_AUTOPLAY_REELS, true)
        showReelsInFeed = prefs.getBoolean(KEY_SHOW_REELS_IN_FEED, true)
        hdUploadsEnabled = prefs.getBoolean(KEY_HD_UPLOADS, false)
        dataSaverMode = prefs.getBoolean(KEY_DATA_SAVER, false)
        offensiveWordsFilter = prefs.getBoolean(KEY_OFFENSIVE_WORDS, true)
        hideLikesCount = prefs.getBoolean(KEY_HIDE_LIKES, false)
        notifyLikesComments = prefs.getBoolean(KEY_NOTIFY_LIKES_COMMENTS, true)
        notifyNewFollowers = prefs.getBoolean(KEY_NOTIFY_FOLLOWERS, true)
        notifyDirectMessages = prefs.getBoolean(KEY_NOTIFY_DM, true)
        notifyStoryReminders = prefs.getBoolean(KEY_NOTIFY_STORY_REMINDERS, true)
        creatorDashboardEnabled = prefs.getBoolean(KEY_CREATOR_DASHBOARD, false)
        downloadQualityOption = prefs.getString(KEY_DOWNLOAD_QUALITY, "Alta (HD)") ?: "Alta (HD)"
        activityStatusEnabled = prefs.getBoolean(KEY_ACTIVITY_STATUS, true)
        twoFactorAuthEnabled = prefs.getBoolean(KEY_2FA, false)
        simulatedCacheSizeMB = prefs.getFloat(KEY_CACHE_SIZE, 0f)
        lastCacheClearAt = prefs.getLong(KEY_LAST_CACHE_CLEAR, 0L)
        permissionsOnboardingCompleted = prefs.getBoolean(KEY_PERM_ONBOARDING_DONE, false)
    }

    /** Sincroniza las preferencias de notificación a Firestore para que la Cloud Function las lea */
    private fun syncNotificationPrefsToFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val data = mapOf(
            "notifyLikesComments" to notifyLikesComments,
            "notifyNewFollowers" to notifyNewFollowers,
            "notifyDirectMessages" to notifyDirectMessages,
            "notifyStoryReminders" to notifyStoryReminders
        )
        db.collection("users").document(uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e(TAG, "Error syncing notification prefs to Firestore", e)
            }
    }

    // Setters
    fun setThemeOption(context: Context, value: String) {
        val canonical = normalizeThemeOption(value)
        selectedThemeOption = canonical
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, canonical).apply()
    }

    fun setDynamicColor(context: Context, value: Boolean) {
        dynamicColorEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
    }

    fun setSeedPalette(context: Context, paletteId: String) {
        seedPaletteId = paletteId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEED_PALETTE, paletteId).apply()
    }

    fun setAmoledBlack(context: Context, value: Boolean) {
        amoledBlackEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AMOLED, value).apply()
    }

    fun setSmoothAnimations(context: Context, value: Boolean) {
        smoothAnimationsEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SMOOTH_ANIMATIONS, value).apply()
    }

    fun setHapticFeedback(context: Context, value: Boolean) {
        hapticFeedbackEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTICS, value).apply()
    }

    fun setAutoplayReels(context: Context, value: Boolean) {
        autoplayReels = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTOPLAY_REELS, value).apply()
    }

    fun setShowReelsInFeed(context: Context, value: Boolean) {
        showReelsInFeed = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_REELS_IN_FEED, value).apply()
    }

    fun setHdUploads(context: Context, value: Boolean) {
        hdUploadsEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HD_UPLOADS, value).apply()
    }

    fun setDataSaver(context: Context, value: Boolean) {
        dataSaverMode = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DATA_SAVER, value).apply()
    }

    fun setOffensiveWords(context: Context, value: Boolean) {
        offensiveWordsFilter = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OFFENSIVE_WORDS, value).apply()
    }

    fun setHideLikes(context: Context, value: Boolean) {
        hideLikesCount = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_LIKES, value).apply()
    }

    fun setNotifyLikesComments(context: Context, value: Boolean) {
        notifyLikesComments = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFY_LIKES_COMMENTS, value).apply()
        syncNotificationPrefsToFirestore()
    }

    fun setNotifyFollowers(context: Context, value: Boolean) {
        notifyNewFollowers = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFY_FOLLOWERS, value).apply()
        syncNotificationPrefsToFirestore()
    }

    fun setNotifyDm(context: Context, value: Boolean) {
        notifyDirectMessages = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFY_DM, value).apply()
        syncNotificationPrefsToFirestore()
    }

    fun setNotifyStoryReminders(context: Context, value: Boolean) {
        notifyStoryReminders = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFY_STORY_REMINDERS, value).apply()
        syncNotificationPrefsToFirestore()
    }

    fun setCreatorDashboard(context: Context, value: Boolean) {
        creatorDashboardEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CREATOR_DASHBOARD, value).apply()
    }

    fun setDownloadQuality(context: Context, value: String) {
        downloadQualityOption = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DOWNLOAD_QUALITY, value).apply()
    }

    fun setActivityStatus(context: Context, value: Boolean) {
        activityStatusEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVITY_STATUS, value).apply()
    }

    fun set2FA(context: Context, value: Boolean) {
        twoFactorAuthEnabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_2FA, value).apply()
    }

    fun setCacheSize(context: Context, value: Float) {
        simulatedCacheSizeMB = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_CACHE_SIZE, value).apply()
    }

    /** Registra cuándo se limpió el caché por última vez (para forzar regeneración). */
    fun recordCacheClear(context: Context) {
        lastCacheClearAt = System.currentTimeMillis()
        setCacheSize(context, 0f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CACHE_CLEAR, lastCacheClearAt).apply()
    }

    /**
     * Marca el onboarding de permisos como completado. Se llama desde
     * PermissionsOnboardingScreen al pulsar "Continuar" o "Ahora no", o
     * desde Ajustes si el usuario quiere saltarlo manualmente.
     */
    fun markPermissionsOnboardingCompleted(context: Context? = null) {
        permissionsOnboardingCompleted = true
        // Si tenemos contexto, persistimos inmediatamente. Si no, se hará
        // en el próximo init() (cambio de proceso, reinicio, etc.).
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PERM_ONBOARDING_DONE, true).apply()
        }
    }

    fun filterOffensiveWords(text: String): String {
        if (!offensiveWordsFilter) return text
        val badWords = listOf(
            "puto", "puta", "mierda", "pendejo", "pendeja", "cabron", "cabrón", "culero", "culera", "marica", "maricón", "putita", "putito", "follar", "joder",
            "fuck", "bitch", "asshole", "shit", "bastard", "cunt", "dick"
        )
        var filteredText = text
        for (word in badWords) {
            val pattern = "(?i)\\b$word\\b".toRegex()
            filteredText = filteredText.replace(pattern) { matchResult ->
                "*".repeat(matchResult.value.length)
            }
        }
        return filteredText
    }
}
