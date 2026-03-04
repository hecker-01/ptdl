package dev.heckr.ptdl.ui.creator

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
import dev.heckr.ptdl.data.CollectionInfo
import dev.heckr.ptdl.data.LocalFileScanner
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.FragmentCreatorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreatorFragment : Fragment() {

    private var _binding: FragmentCreatorBinding? = null
    private val binding get() = _binding!!

    private var listAdapter: CreatorListAdapter? = null

    private val allPosts = mutableListOf<PostInfo>()
    private var collections: List<CollectionInfo> = emptyList()
    private var headerItem: CreatorListItem.Header? = null
    private var displayedCount = 0
    private var doneCollecting = false
    private var searchQuery = ""

    companion object {
        private const val INITIAL_PAGE_SIZE = 5
        private const val PAGE_SIZE = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        listAdapter = CreatorListAdapter(
            onPostClick = { post ->
                findNavController().navigate(
                    R.id.action_creator_to_post,
                    bundleOf("postFolderUri" to post.folderUri.toString())
                )
            },
            onCollectionClick = { collection ->
                val creatorUri = arguments?.getString("creatorFolderUri") ?: return@CreatorListAdapter
                val thumb = (collection.thumbnailUri?.toString() ?: collection.thumbnailUrl)
                val postIds = collection.postIds.joinToString(",")
                findNavController().navigate(
                    R.id.action_creator_to_collection,
                    bundleOf(
                        "collectionTitle" to collection.title,
                        "collectionThumbnail" to thumb,
                        "creatorFolderUri" to creatorUri,
                        "collectionPostIds" to postIds
                    )
                )
            },
            onSearchChanged = { query ->
                searchQuery = query
                rebuildList()
            }
        )

        val layoutManager = LinearLayoutManager(requireContext())
        binding.creatorRecycler.layoutManager = layoutManager
        binding.creatorRecycler.adapter = listAdapter

        // Pagination: load more when nearing the end
        binding.creatorRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val last = layoutManager.findLastVisibleItemPosition()
                val total = listAdapter?.itemCount ?: 0
                if (last >= total - 3 && displayedCount < filteredPosts().size) {
                    loadNextPage()
                }
            }
        })

        val creatorUri = Uri.parse(arguments?.getString("creatorFolderUri") ?: return)
        loadCreator(creatorUri)
    }

    private fun loadCreator(creatorUri: Uri) {
        binding.progressBar.isVisible = true

        // Reset state in case the fragment is reused
        allPosts.clear()
        collections = emptyList()
        headerItem = null
        displayedCount = 0
        doneCollecting = false
        searchQuery = ""

        // Load creator info + collections FIRST, then stream posts
        viewLifecycleOwner.lifecycleScope.launch {
            val (creator, cols) = withContext(Dispatchers.IO) {
                val c = LocalFileScanner.parseCreatorFromUri(requireContext(), creatorUri)
                val cols = LocalFileScanner.scanCollections(requireContext(), creatorUri)
                c to cols
            }
            if (_binding == null) return@launch
            collections = cols

            if (creator != null) {
                val meta = buildString {
                    if (creator.patronCount > 0) append("${creator.patronCount} patrons")
                    if (creator.postCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${creator.postCount} posts")
                    }
                }
                headerItem = CreatorListItem.Header(
                    name = creator.name,
                    meta = meta,
                    bio = creator.summary,
                    avatarUrl = creator.avatarUrl,
                    coverUrl = creator.coverUrl
                )
                binding.collapsingToolbar.title = creator.name
                creator.coverUrl?.let { url ->
                    binding.coverImage.load(url) { crossfade(true) }
                }
            }

            // Show header + collections immediately — no waiting for posts
            binding.progressBar.isVisible = false
            rebuildList()

            // Stream posts in the background
            streamPosts(creatorUri)
        }
    }

    private suspend fun streamPosts(creatorUri: Uri) {
        val flow = PatreonRepository.loadPostsFlow(requireContext(), creatorUri)
        var firstPageShown = false

        withContext(Dispatchers.IO) {
            flow.collect { post ->
                allPosts.add(post)
                // Show first small batch fast so the page feels instant
                if (!firstPageShown && allPosts.size >= INITIAL_PAGE_SIZE) {
                    firstPageShown = true
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        displayedCount = INITIAL_PAGE_SIZE
                        rebuildList()
                    }
                }
            }
        }

        if (_binding == null) return
        doneCollecting = true
        if (!firstPageShown) {
            displayedCount = allPosts.size
        }
        rebuildList()

        // Cache full list
        val key = creatorUri.toString()
        PatreonRepository.cachePosts(key, allPosts.sortedByDescending { it.publishedAt })
    }

    private fun filteredPosts(): List<PostInfo> {
        if (searchQuery.isBlank()) return allPosts
        val q = searchQuery.lowercase()
        return allPosts.filter { it.title.lowercase().contains(q) }
    }

    private fun rebuildList() {
        val filtered = filteredPosts()
        // Clamp displayedCount to filtered size
        val shown = if (searchQuery.isBlank()) {
            minOf(displayedCount, filtered.size)
        } else {
            filtered.size // show all when searching
        }

        val items = mutableListOf<CreatorListItem>()
        headerItem?.let { items.add(it) }
        items.add(CreatorListItem.SearchBar)

        if (collections.isNotEmpty()) {
            items.add(CreatorListItem.SectionLabel("Collections (${collections.size})"))
            items.add(CreatorListItem.CollectionsRow(collections))
        }

        items.add(CreatorListItem.SectionLabel("Posts (${filtered.size})"))
        for (i in 0 until shown) {
            items.add(CreatorListItem.Post(filtered[i]))
        }
        if (!doneCollecting || (searchQuery.isBlank() && shown < filtered.size)) {
            items.add(CreatorListItem.LoadMore)
        }

        listAdapter?.submitItems(items)
    }

    private fun loadNextPage() {
        val filtered = filteredPosts()
        if (displayedCount >= filtered.size) return
        displayedCount = minOf(displayedCount + PAGE_SIZE, filtered.size)
        rebuildList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

