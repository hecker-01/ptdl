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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.heckr.ptdl.settings.AppLockManager
import dev.heckr.ptdl.settings.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    // Indexing overlay views
    private lateinit var indexingOverlay: FrameLayout
    private lateinit var indexingStatus: TextView
    private lateinit var indexingProgress: LinearProgressIndicator
    private lateinit var indexingPostStatus: TextView
    private lateinit var indexingPostProgress: LinearProgressIndicator

    // Lock overlay views
    private lateinit var lockOverlay: FrameLayout
    private lateinit var btnUnlock: MaterialButton

    private var pendingLock = false

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

        // Lock overlay views
        lockOverlay = findViewById(R.id.lock_overlay)
        btnUnlock = findViewById(R.id.btn_unlock)
        btnUnlock.setOnClickListener { triggerAuth() }

        // Check for updates on boot
        dev.heckr.ptdl.settings.UpdateChecker.check(this)

        // Listen for update check result and update badge
        val updateBadge: () -> Unit = {
            if (dev.heckr.ptdl.settings.UpdateChecker.updateAvailable) {
                val badge = bottomNav.getOrCreateBadge(R.id.navigation_settings)
                badge.isVisible = true
                badge.clearNumber()
            } else {
                bottomNav.removeBadge(R.id.navigation_settings)
            }
        }
        dev.heckr.ptdl.settings.UpdateChecker.addListener(updateBadge)
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

    override fun onStart() {
        super.onStart()
        if (AppLockManager.isEnabled(this) && !AppLockManager.isSessionUnlocked) {
            pendingLock = true
            lockOverlay.isVisible = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingLock) {
            pendingLock = false
            triggerAuth()
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppLockManager.shouldLockOnBackground(this)) {
            AppLockManager.lockSession()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (lockOverlay.isVisible) {
            moveTaskToBack(true)
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun triggerAuth() {
        if (!lockOverlay.isVisible) return
        when (AppLockManager.getLockType(this)) {
            AppLockManager.TYPE_PIN -> showPinUnlockDialog()
            else -> AppLockManager.authenticate(this, onSuccess = { lockOverlay.isVisible = false })
        }
    }

    private fun showPinUnlockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_entry, null)
        val pinLayout = dialogView.findViewById<TextInputLayout>(R.id.pin_input_layout)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.pin_input)
        pinLayout.hint = getString(R.string.lock_enter_pin_hint)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lock_prompt_title)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.lock_unlock_button) { dialog, _ ->
                val pin = pinInput.text?.toString() ?: ""
                if (AppLockManager.verifyPin(this, pin)) {
                    AppLockManager.markUnlocked()
                    lockOverlay.isVisible = false
                    dialog.dismiss()
                } else {
                    pinLayout.error = getString(R.string.lock_wrong_pin)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> moveTaskToBack(true) }
            .show()
    }

    // --- Indexing overlay ---

    fun showIndexingOverlay() {
        indexingOverlay.isVisible = true
        indexingProgress.isIndeterminate = true
        indexingStatus.text = getString(R.string.scanning_folder)
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
            indexingStatus.text = resources.getQuantityString(R.plurals.found_creators, total, total)
            indexingPostStatus.isVisible = true
            indexingPostProgress.isVisible = true
            indexingPostProgress.isIndeterminate = true
            indexingPostStatus.text = getString(R.string.scanning_posts)
        } else if (total > 0) {
            indexingProgress.progress = done
            indexingStatus.text = getString(R.string.creators_progress_format, done, total)
        }
    }

    fun updateIndexingPostProgress(postsSoFar: Int) {
        indexingPostStatus.text = resources.getQuantityString(R.plurals.posts_found, postsSoFar, postsSoFar)
    }
}
