package dev.heckr.ptdl.ui.home

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.heckr.ptdl.R
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.databinding.FragmentHomeBinding
import dev.heckr.ptdl.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private var adapter: CreatorsAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        adapter = CreatorsAdapter(
            onClick = { creator ->
                findNavController().navigate(
                    R.id.action_home_to_creator,
                    bundleOf("creatorFolderUri" to creator.folderUri.toString())
                )
            },
            onVisible = { creator ->
                // Pre-fetch posts for the creator entering the viewport
                PatreonRepository.prefetchPosts(requireContext(), creator.folderUri)
            }
        )

        binding.creatorsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.creatorsRecycler.adapter = adapter

        binding.btnSelectFolder.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
                .selectedItemId = R.id.navigation_profile
        }
    }

    override fun onResume() {
        super.onResume()
        loadCreators()
    }

    private fun loadCreators() {
        val uriString = settingsManager.getString(SettingsManager.KEY_ROOT_URI)
        if (uriString.isBlank()) {
            showEmptyState()
            return
        }

        val rootUri = Uri.parse(uriString)

        // Show cached data immediately - no spinner needed if we already have results
        val cached = PatreonRepository.getCachedCreators()
        if (cached.isNotEmpty()) {
            binding.progressBar.isVisible = false
            binding.emptyState.isVisible = false
            binding.creatorsRecycler.isVisible = true
            adapter?.submitList(cached)
            // Silently refresh in background
            viewLifecycleOwner.lifecycleScope.launch {
                val fresh = withContext(Dispatchers.IO) {
                    PatreonRepository.loadCreators(requireContext(), rootUri)
                }
                if (_binding == null) return@launch
                adapter?.submitList(fresh)
            }
            return
        }

        // No cache yet - show spinner
        binding.progressBar.isVisible = true
        binding.emptyState.isVisible = false
        binding.creatorsRecycler.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val creators = withContext(Dispatchers.IO) {
                PatreonRepository.loadCreators(requireContext(), rootUri)
            }
            if (_binding == null) return@launch
            binding.progressBar.isVisible = false
            if (creators.isEmpty()) {
                showEmptyState()
            } else {
                binding.creatorsRecycler.isVisible = true
                adapter?.submitList(creators)
            }
        }
    }

    private fun showEmptyState() {
        binding.progressBar.isVisible = false
        binding.emptyState.isVisible = true
        binding.creatorsRecycler.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
