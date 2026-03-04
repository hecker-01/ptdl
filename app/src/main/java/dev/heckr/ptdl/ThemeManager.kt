package dev.heckr.ptdl

import android.app.Activity
import com.google.android.material.color.DynamicColors
import dev.heckr.ptdl.settings.SettingsManager

object ThemeManager {
    const val KEY_THEME_COLOR = "pref_theme_color"
    const val KEY_DARK_MODE = "pref_dark_mode"
    const val THEME_DYNAMIC = "dynamic"

    fun applyTheme(activity: Activity, settingsManager: SettingsManager) {
        DynamicColors.applyToActivityIfAvailable(activity)
    }
}
