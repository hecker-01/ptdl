package dev.heckr.ptdl

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.heckr.ptdl.settings.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    // Indexing overlay views
    private lateinit var indexingOverlay: FrameLayout
    private lateinit var indexingStatus: TextView
    private lateinit var indexingProgress: LinearProgressIndicator
    private lateinit var indexingPostStatus: TextView
    private lateinit var indexingPostProgress: LinearProgressIndicator

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

        // Indexing overlay views
        indexingOverlay = findViewById(R.id.indexing_overlay)
        indexingStatus = findViewById(R.id.indexing_status)
        indexingProgress = findViewById(R.id.indexing_progress)
        indexingPostStatus = findViewById(R.id.indexing_post_status)
        indexingPostProgress = findViewById(R.id.indexing_post_progress)

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

    fun showIndexingOverlay() {
        indexingOverlay.isVisible = true
        indexingProgress.isIndeterminate = true
        indexingStatus.text = "Scanning folder\u2026"
        indexingPostStatus.isVisible = false
        indexingPostProgress.isVisible = false
    }

    fun hideIndexingOverlay() {
        indexingOverlay.isVisible = false
    }

    fun updateIndexingCreatorProgress(done: Int, total: Int) {
        if (done == 0 && total > 0) {
            indexingProgress.isIndeterminate = false
            indexingProgress.max = total
            indexingProgress.progress = 0
            indexingStatus.text = "Found $total creators"
            indexingPostStatus.isVisible = true
            indexingPostProgress.isVisible = true
            indexingPostProgress.isIndeterminate = true
            indexingPostStatus.text = "Scanning posts\u2026"
        } else if (total > 0) {
            indexingProgress.progress = done
            indexingStatus.text = "Creators $done / $total"
        }
    }

    fun updateIndexingPostProgress(postsSoFar: Int) {
        indexingPostStatus.text = "$postsSoFar posts found"
    }
}

