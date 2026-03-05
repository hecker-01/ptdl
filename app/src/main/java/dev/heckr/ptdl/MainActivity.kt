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
import dev.heckr.ptdl.settings.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager(this)
        ThemeManager.applyTheme(this, settingsManager)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Check for updates on boot
        dev.heckr.ptdl.settings.UpdateChecker.check(this)

        // Listen for update check result and update badge
        val updateBadge: () -> Unit = {
            if (dev.heckr.ptdl.settings.UpdateChecker.updateAvailable) {
                val badge = bottomNav.getOrCreateBadge(R.id.navigation_settings)
                badge.isVisible = true
                badge.clearNumber() // Show dot only
            } else {
                bottomNav.removeBadge(R.id.navigation_settings)
            }
        }
        dev.heckr.ptdl.settings.UpdateChecker.addListener(updateBadge)
        // Set initial badge state
        updateBadge()

        // Hide the bottom bar while inside creator/post detail screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.creatorFragment, R.id.postDetailFragment, R.id.collectionFragment, R.id.favoritesFragment ->
                    bottomNav.visibility = View.GONE
                else ->
                    bottomNav.visibility = View.VISIBLE
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }
}

