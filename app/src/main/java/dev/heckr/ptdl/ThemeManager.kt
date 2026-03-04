package dev.heckr.ptdl

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import dev.heckr.ptdl.settings.SettingsManager

object ThemeManager {
    const val KEY_THEME_COLOR = "pref_theme_color"
    const val KEY_DARK_MODE = "pref_dark_mode"
    const val THEME_DYNAMIC = "dynamic"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    fun applyTheme(activity: Activity, settingsManager: SettingsManager) {
        DynamicColors.applyToActivityIfAvailable(activity)
        applyNightMode(settingsManager)
    }

    fun applyNightMode(settingsManager: SettingsManager) {
        val mode = settingsManager.getString(KEY_DARK_MODE, MODE_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
