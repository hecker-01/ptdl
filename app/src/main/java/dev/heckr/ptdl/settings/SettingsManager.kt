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

    companion object {
        const val KEY_ROOT_URI = "pref_patreon_dl_root_uri"
    }
}
