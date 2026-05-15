package dev.heckr.ptdl.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.heckr.ptdl.R
import dev.heckr.ptdl.ThemeManager
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.databinding.FragmentSettingsBinding
import dev.heckr.ptdl.settings.AppLockManager
import dev.heckr.ptdl.settings.AppUpdater
import dev.heckr.ptdl.settings.SettingsManager
import dev.heckr.ptdl.settings.UpdateChecker
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private lateinit var appUpdater: AppUpdater

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            settingsManager.putString(SettingsManager.KEY_ROOT_URI, uri.toString())
            PatreonRepository.invalidate()
            startIndexing(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdater = AppUpdater(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        // Updates
        appUpdater.onStateChanged = fun(state: AppUpdater.State, message: String) {
            if (_binding == null) return
            binding.updateSubtitle.text = message
            when (state) {
                AppUpdater.State.DOWNLOADING, AppUpdater.State.INSTALLING -> {
                    binding.updateProgress.isVisible = true
                    binding.updateCard.isClickable = false
                    binding.updateCard.isFocusable = false
                }
                else -> {
                    binding.updateProgress.isVisible = false
                    binding.updateCard.isClickable = true
                    binding.updateCard.isFocusable = true
                }
            }
        }
        appUpdater.onDownloadProgress = fun(progress: Int) {
            if (_binding == null) return
            if (progress < 0) {
                binding.updateProgress.isIndeterminate = true
            } else {
                binding.updateProgress.isIndeterminate = false
                binding.updateProgress.setProgressCompat(progress, true)
            }
        }
        appUpdater.syncFromChecker()
        binding.updateCard.setOnClickListener {
            if (appUpdater.onUpdateTapped(requireContext())) {
                showUpdateDialog()
            }
        }

        binding.btnSelectFolder.setOnClickListener { pickFolder.launch(null) }
        binding.btnClearFolder.setOnClickListener {
            settingsManager.remove(SettingsManager.KEY_ROOT_URI)
            PatreonRepository.invalidate()
            updateFolderDisplay()
        }

        // Theme toggle
        val currentMode = settingsManager.getString(ThemeManager.KEY_DARK_MODE, ThemeManager.MODE_SYSTEM)
        binding.themeToggleGroup.check(
            when (currentMode) {
                ThemeManager.MODE_LIGHT -> R.id.btn_theme_light
                ThemeManager.MODE_DARK -> R.id.btn_theme_dark
                else -> R.id.btn_theme_system
            }
        )
        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btn_theme_light -> ThemeManager.MODE_LIGHT
                R.id.btn_theme_dark -> ThemeManager.MODE_DARK
                else -> ThemeManager.MODE_SYSTEM
            }
            settingsManager.putString(ThemeManager.KEY_DARK_MODE, mode)
            ThemeManager.applyNightMode(settingsManager)
        }

        // App Lock
        setupLockSection()

        updateFolderDisplay()

        // Copyright footer
        val footerText = getString(R.string.copyright_footer)
        val domain = "heckr.dev"
        val linkStart = footerText.indexOf(domain)
        if (linkStart >= 0) {
            val spannable = SpannableString(footerText)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$domain")))
                }
            }, linkStart, linkStart + domain.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.copyrightFooter.text = spannable
            binding.copyrightFooter.movementMethod = LinkMovementMethod.getInstance()
        }

        // Build info
        val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        binding.aboutVersion.text = getString(R.string.version_format, info.versionName)
        binding.aboutBuild.text = getString(
            R.string.build_format, info.longVersionCode,
            if (dev.heckr.ptdl.BuildConfig.DEBUG) getString(R.string.build_debug)
            else getString(R.string.build_release)
        )
        binding.aboutPackage.text = info.packageName
    }

    // --- App Lock section ---

    private var applyingLockState = false

    private fun setupLockSection() {
        val lockEnabled = AppLockManager.isEnabled(requireContext())
        applyingLockState = true
        binding.switchAppLock.isChecked = lockEnabled
        applyingLockState = false
        setLockMethodRowVisible(lockEnabled)
        updateLockMethodLabel()

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (applyingLockState) return@setOnCheckedChangeListener
            if (isChecked) {
                // Revert until setup completes
                applyingLockState = true
                binding.switchAppLock.isChecked = false
                applyingLockState = false
                showEnableLockDialog()
            } else {
                AppLockManager.disable(requireContext())
                setLockMethodRowVisible(false)
            }
        }

        binding.lockMethodRow.setOnClickListener {
            showChangeLockMethodDialog()
        }
    }

    private fun setLockMethodRowVisible(visible: Boolean) {
        binding.lockMethodDivider.isVisible = visible
        binding.lockMethodRow.isVisible = visible
    }

    private fun updateLockMethodLabel() {
        val type = AppLockManager.getLockType(requireContext())
        binding.lockMethodValue.text = getString(
            if (type == AppLockManager.TYPE_PIN) R.string.lock_method_pin
            else R.string.lock_method_device
        )
    }

    private fun showEnableLockDialog() {
        val methods = arrayOf(
            getString(R.string.lock_method_device),
            getString(R.string.lock_method_pin)
        )
        var selectedIndex = 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_choose_method)
            .setSingleChoiceItems(methods, 0) { _, which -> selectedIndex = which }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selectedIndex == 0) enableDeviceLock()
                else showPinSetupDialog(isChanging = false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChangeLockMethodDialog() {
        val currentType = AppLockManager.getLockType(requireContext())
        val currentIndex = if (currentType == AppLockManager.TYPE_PIN) 1 else 0
        val methods = arrayOf(
            getString(R.string.lock_method_device),
            getString(R.string.lock_method_pin)
        )
        var selectedIndex = currentIndex

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_change_method_title)
            .setSingleChoiceItems(methods, currentIndex) { _, which -> selectedIndex = which }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                when {
                    selectedIndex == 0 && currentType != AppLockManager.TYPE_DEVICE -> enableDeviceLock()
                    selectedIndex == 1 -> showPinSetupDialog(isChanging = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun enableDeviceLock() {
        if (!AppLockManager.canUseDeviceLock(requireContext())) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.lock_no_screen_lock_title)
                .setMessage(R.string.lock_no_screen_lock_message)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        // Verify with biometric before enabling, so we confirm it works
        AppLockManager.authenticate(
            requireActivity(),
            onSuccess = {
                AppLockManager.enable(requireContext(), AppLockManager.TYPE_DEVICE)
                AppLockManager.markUnlocked()
                applyingLockState = true
                binding.switchAppLock.isChecked = true
                applyingLockState = false
                setLockMethodRowVisible(true)
                updateLockMethodLabel()
            }
        )
    }

    private fun showPinSetupDialog(isChanging: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_entry, null)
        val pinLayout = dialogView.findViewById<TextInputLayout>(R.id.pin_input_layout)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.pin_input)
        pinLayout.hint = getString(R.string.lock_setup_pin_hint)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_setup_pin_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ -> /* handled below via button intercept */ }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                // Override positive button so we can validate without closing on error
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val pin = pinInput.text?.toString() ?: ""
                    if (pin.length < 4) {
                        pinLayout.error = getString(R.string.lock_pin_too_short)
                        return@setOnClickListener
                    }
                    pinLayout.error = null
                    dialog.dismiss()
                    showPinConfirmDialog(pin, isChanging)
                }
            }
    }

    private fun showPinConfirmDialog(pin: String, isChanging: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_entry, null)
        val pinLayout = dialogView.findViewById<TextInputLayout>(R.id.pin_input_layout)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.pin_input)
        pinLayout.hint = getString(R.string.lock_confirm_pin_hint)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_setup_pin_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val confirm = pinInput.text?.toString() ?: ""
                    if (confirm != pin) {
                        pinLayout.error = getString(R.string.lock_pin_mismatch)
                        return@setOnClickListener
                    }
                    AppLockManager.setupPin(requireContext(), pin)
                    AppLockManager.enable(requireContext(), AppLockManager.TYPE_PIN)
                    AppLockManager.markUnlocked()
                    applyingLockState = true
                    binding.switchAppLock.isChecked = true
                    applyingLockState = false
                    setLockMethodRowVisible(true)
                    updateLockMethodLabel()
                    dialog.dismiss()
                }
            }
    }

    // --- Update dialog ---

    private fun showUpdateDialog() {
        val ctx = context ?: return
        val version = UpdateChecker.latestVersion ?: return
        val body = UpdateChecker.releaseBody
        val sizeBytes = UpdateChecker.apkSizeBytes

        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)
        val sizeText = dialogView.findViewById<android.widget.TextView>(R.id.update_size)
        val changelogText = dialogView.findViewById<android.widget.TextView>(R.id.update_changelog)

        sizeText.text = getString(R.string.update_dialog_size_format, Formatter.formatFileSize(ctx, sizeBytes))

        if (!body.isNullOrBlank()) {
            val markwon = Markwon.create(ctx)
            markwon.setMarkdown(changelogText, body)
        } else {
            changelogText.text = getString(R.string.no_changelog)
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.update_dialog_title_format, version))
            .setView(dialogView)
            .setPositiveButton(R.string.update_button) { dialog, _ ->
                dialog.dismiss()
                appUpdater.startDownload(ctx)
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    // --- Folder ---

    private fun startIndexing(uri: Uri) {
        val mainActivity = activity as? dev.heckr.ptdl.MainActivity ?: return
        mainActivity.showIndexingOverlay()
        binding.btnSelectFolder.isEnabled = false
        binding.btnClearFolder.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val isValid = withContext(Dispatchers.IO) {
                dev.heckr.ptdl.data.LocalFileScanner.isValidPatreonDlFolder(requireContext(), uri)
            }
            if (!isValid) {
                if (_binding == null) return@launch
                mainActivity.hideIndexingOverlay()
                binding.btnSelectFolder.isEnabled = true
                binding.btnClearFolder.isEnabled = true
                settingsManager.remove(SettingsManager.KEY_ROOT_URI)
                PatreonRepository.invalidate()
                updateFolderDisplay()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.invalid_folder_title)
                    .setMessage(R.string.invalid_folder_message)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                PatreonRepository.warmUpAwait(
                    requireContext(), uri,
                    onCreatorProgress = { done, total ->
                        withContext(Dispatchers.Main) {
                            if (_binding == null) return@withContext
                            mainActivity.updateIndexingCreatorProgress(done, total)
                        }
                    },
                    onPostProgress = { postsSoFar ->
                        withContext(Dispatchers.Main) {
                            if (_binding == null) return@withContext
                            mainActivity.updateIndexingPostProgress(postsSoFar)
                        }
                    }
                )
            }
            if (_binding != null) {
                mainActivity.hideIndexingOverlay()
                binding.btnSelectFolder.isEnabled = true
                binding.btnClearFolder.isEnabled = true
                updateFolderDisplay()
            }
        }
    }

    private fun updateFolderDisplay() {
        val uriString = settingsManager.getString(SettingsManager.KEY_ROOT_URI)
        if (uriString.isBlank()) {
            binding.folderPathText.text = getString(R.string.not_set)
            binding.btnClearFolder.isVisible = false
        } else {
            val uri = Uri.parse(uriString)
            val displayPath = try {
                uri.lastPathSegment
                    ?.substringAfter(":")
                    ?.replace("%2F", "/")
                    ?: uriString
            } catch (_: Exception) { uriString }
            binding.folderPathText.text = displayPath
            binding.btnClearFolder.isVisible = true
        }
    }

    override fun onDestroyView() {
        appUpdater.cleanup()
        super.onDestroyView()
        _binding = null
    }
}
