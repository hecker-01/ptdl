package dev.heckr.ptdl.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.heckr.ptdl.R
import dev.heckr.ptdl.ThemeManager
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.databinding.FragmentSettingsBinding
import dev.heckr.ptdl.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        binding.btnSelectFolder.setOnClickListener {
            pickFolder.launch(null)
        }

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

        updateFolderDisplay()

        // Build info
        val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        binding.aboutVersion.text = "Version ${info.versionName}"
        binding.aboutBuild.text = "Build ${info.longVersionCode} · ${if (dev.heckr.ptdl.BuildConfig.DEBUG) "Debug" else "Release"}"
        binding.aboutPackage.text = info.packageName
    }

    private fun startIndexing(uri: Uri) {
        binding.indexingOverlay.isVisible = true
        binding.indexingProgress.isIndeterminate = true
        binding.indexingStatus.text = "Scanning folder\u2026"
        binding.indexingPostStatus.isVisible = false
        binding.indexingPostProgress.isVisible = false
        binding.btnSelectFolder.isEnabled = false
        binding.btnClearFolder.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val postCount = withContext(Dispatchers.IO) {
                PatreonRepository.warmUpAwait(
                    requireContext(), uri,
                    onCreatorProgress = { done, total ->
                        withContext(Dispatchers.Main) {
                            if (_binding == null) return@withContext
                            if (done == 0 && total > 0) {
                                binding.indexingProgress.isIndeterminate = false
                                binding.indexingProgress.max = total
                                binding.indexingProgress.progress = 0
                                binding.indexingStatus.text = "Found $total creators"
                                binding.indexingPostStatus.isVisible = true
                                binding.indexingPostProgress.isVisible = true
                                binding.indexingPostProgress.isIndeterminate = true
                                binding.indexingPostStatus.text = "Scanning posts\u2026"
                            } else if (total > 0) {
                                binding.indexingProgress.progress = done
                                binding.indexingStatus.text = "Creators $done / $total"
                            }
                        }
                    },
                    onPostProgress = { postsSoFar ->
                        withContext(Dispatchers.Main) {
                            if (_binding == null) return@withContext
                            binding.indexingPostStatus.text = "$postsSoFar posts found"
                        }
                    }
                )
            }
            if (_binding != null) {
                binding.indexingOverlay.isVisible = false
                binding.btnSelectFolder.isEnabled = true
                binding.btnClearFolder.isEnabled = true
                updateFolderDisplay()
            }
        }
    }

    private fun updateFolderDisplay() {
        val uriString = settingsManager.getString(SettingsManager.KEY_ROOT_URI)
        if (uriString.isBlank()) {
            binding.folderPathText.text = "Not set"
            binding.btnClearFolder.isVisible = false
        } else {
            // Show a human-readable path from the URI
            val uri = Uri.parse(uriString)
            val displayPath = try {
                uri.lastPathSegment
                    ?.substringAfter(":")   // strip "primary:" prefix
                    ?.replace("%2F", "/")   // decode slashes
                    ?: uriString
            } catch (_: Exception) {
                uriString
            }
            binding.folderPathText.text = displayPath
            binding.btnClearFolder.isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

