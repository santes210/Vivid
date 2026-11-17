package com.vivid.app.presentation.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal data class FeedPreferences(
    val hiddenPosts: Set<String> = emptySet(),
    val mutedAuthors: Set<String> = emptySet()
) {
    fun allows(post: PostData): Boolean = post.id !in hiddenPosts && post.userId !in mutedAuthors
}

/** Local, account-scoped discovery choices. Never changes following or messaging permissions. */
internal class FeedPreferencesStore(context: Context, uid: String) {
    private val prefs = context.applicationContext.getSharedPreferences("discovery_$uid", Context.MODE_PRIVATE)
    fun read() = FeedPreferences(
        prefs.getStringSet("hiddenPosts", emptySet()).orEmpty().toSet(),
        prefs.getStringSet("mutedAuthors", emptySet()).orEmpty().toSet()
    )
    fun hide(id: String) = put("hiddenPosts", read().hiddenPosts + id)
    fun mute(id: String) = put("mutedAuthors", read().mutedAuthors + id)
    fun reset() { prefs.edit().remove("hiddenPosts").remove("mutedAuthors").apply() }
    private fun put(key: String, values: Set<String>) { prefs.edit().putStringSet(key, values).apply() }
    fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun stop(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
}

@Composable
internal fun rememberFeedPreferences(uid: String): Pair<FeedPreferences, FeedPreferencesStore> {
    val context = LocalContext.current
    val store = remember(uid) { FeedPreferencesStore(context, uid) }
    var state by remember(store) { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state = store.read() }
        store.listen(listener)
        state = store.read()
        onDispose { store.stop(listener) }
    }
    return state to store
}
