package dev.heckr.ptdl.ui.collection

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
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.heckr.ptdl.R
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.FragmentCollectionBinding
import dev.heckr.ptdl.ui.creator.CollectionPostsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    private var allPosts: List<PostInfo> = emptyList()
    private var displayedCount = 0
    private var adapter: CollectionPostsAdapter? = null

    companion object {
        private const val PAGE_SIZE = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val title = arguments?.getString("collectionTitle") ?: "Collection"
        val thumbnailUrl = arguments?.getString("collectionThumbnail")
        val creatorUri = arguments?.getString("creatorFolderUri") ?: return
        val postIdsRaw = arguments?.getString("collectionPostIds") ?: return

        binding.collapsingToolbar.title = title
        thumbnailUrl?.let { binding.coverImage.load(it) { crossfade(true) } }

        val postIds = postIdsRaw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()

        adapter = CollectionPostsAdapter { post ->
            findNavController().navigate(
                R.id.action_collection_to_post,
                bundleOf("postFolderUri" to post.folderUri.toString())
            )
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.postsRecycler.layoutManager = layoutManager
        binding.postsRecycler.adapter = adapter

        // Pagination scroll listener
        binding.postsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val last = layoutManager.findLastVisibleItemPosition()
                val total = adapter?.itemCount ?: 0
                if (last >= total - 3 && displayedCount < allPosts.size) {
                    loadNextPage()
                }
            }
        })

        binding.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val posts = withContext(Dispatchers.IO) {
                val cached = PatreonRepository.getCachedPosts(creatorUri)
                cached?.filter { it.id.toLongOrNull() in postIds } ?: emptyList()
            }
            if (_binding == null) return@launch
            binding.progressBar.isVisible = false
            allPosts = posts
            if (posts.isEmpty()) {
                binding.emptyLabel.isVisible = true
            } else {
                displayedCount = minOf(PAGE_SIZE, posts.size)
                adapter?.submitList(posts.take(displayedCount))
            }
        }
    }

    private fun loadNextPage() {
        if (displayedCount >= allPosts.size) return
        displayedCount = minOf(displayedCount + PAGE_SIZE, allPosts.size)
        adapter?.submitList(allPosts.take(displayedCount))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
