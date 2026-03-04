package dev.heckr.ptdl

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.heckr.ptdl.data.DiscordRepository
import dev.heckr.ptdl.settings.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private var currentThemeSelection: String = ThemeManager.THEME_DYNAMIC
    private var currentDarkMode: Boolean = false

    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                ThemeManager.KEY_DARK_MODE,
                ThemeManager.KEY_THEME_COLOR -> {
                    val newTheme = settingsManager.getString(
                        ThemeManager.KEY_THEME_COLOR,
                        ThemeManager.THEME_DYNAMIC
                    )
                    val newDarkMode = settingsManager.getBoolean(ThemeManager.KEY_DARK_MODE, false)
                    if (newTheme != currentThemeSelection || newDarkMode != currentDarkMode) {
                        currentThemeSelection = newTheme
                        currentDarkMode = newDarkMode
                        recreate()
                    }
                }
                "pref_notifications_enabled" -> {
                    val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
                    updateNotificationsVisibility(bottomNav)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager(this)
        ThemeManager.applyTheme(this, settingsManager)
        currentThemeSelection = settingsManager.getString(
            ThemeManager.KEY_THEME_COLOR,
            ThemeManager.THEME_DYNAMIC
        )
        currentDarkMode = settingsManager.getBoolean(ThemeManager.KEY_DARK_MODE, false)

        super.onCreate(savedInstanceState)

        DiscordRepository.initialize(applicationContext)

        val storedToken = settingsManager.getString(DiscordRepository.PREF_TOKEN, "")
        val effectiveToken = if (storedToken.isNotBlank()) {
            storedToken
        } else {
            BuildConfig.DISCORD_BOT_TOKEN
        }
        DiscordRepository.configureToken(effectiveToken)
        if (DiscordRepository.isConfigured()) {
            DiscordRepository.startGateway()
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set up bottom navigation with NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
        updateNotificationsVisibility(bottomNav)

        // Handle reselection of profile tab to navigate to settings
        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.navigation_profile) {
                if (navController.currentDestination?.id == R.id.navigation_profile) {
                    navController.navigate(R.id.action_profile_to_settings)
                }
            }
        }

        // Hide bottom navigation on settings page
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.settingsFragment) {
                bottomNav.visibility = View.GONE
            } else {
                bottomNav.visibility = View.VISIBLE
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // Apply bottom padding to bottom navigation
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)

            insets
        }

        settingsManager.registerChangeListener(preferenceListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        DiscordRepository.stopGateway()
        settingsManager.unregisterChangeListener(preferenceListener)
    }

    private fun updateNotificationsVisibility(bottomNav: BottomNavigationView) {
        val enabled = settingsManager.getBoolean("pref_notifications_enabled", true)
        bottomNav.menu.findItem(R.id.navigation_notifications)?.isVisible = enabled
    }
}
