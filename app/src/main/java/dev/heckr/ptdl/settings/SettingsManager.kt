package dev.heckr.ptdl.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ptdl_prefs", Context.MODE_PRIVATE)

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    fun remove(key: String) =
        prefs.edit().remove(key).apply()

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(id: String): Boolean = getFavorites().contains(id)

    fun addFavorite(id: String) {
        val set = getFavorites().toMutableSet()
        if (set.add(id)) prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
    }

    fun removeFavorite(id: String) {
        val set = getFavorites().toMutableSet()
        if (set.remove(id)) prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
    }

    /** Toggle favorite state. Returns true if now favorited. */
    fun toggleFavorite(id: String): Boolean {
        val set = getFavorites().toMutableSet()
        val nowFavorite = if (set.contains(id)) {
            set.remove(id)
            false
        } else {
            set.add(id)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
        return nowFavorite
    }

    companion object {
        const val KEY_ROOT_URI = "pref_patreon_dl_root_uri"
        const val KEY_FAVORITES = "pref_favorites_set"
    }
}
