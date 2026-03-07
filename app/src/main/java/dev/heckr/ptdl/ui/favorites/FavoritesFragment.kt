package dev.heckr.ptdl.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.heckr.ptdl.R
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.FragmentFavoritesBinding
import dev.heckr.ptdl.ui.creator.CreatorListAdapter
import dev.heckr.ptdl.ui.creator.CreatorListItem
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private var listAdapter: CreatorListAdapter? = null
    private val allPosts = mutableListOf<PostInfo>()
    private var displayedCount = 0
    private var searchQuery = ""

    companion object {
        private const val PAGE_SIZE = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        listAdapter = CreatorListAdapter(
            onPostClick = { post ->
                findNavController().navigate(
                    R.id.postDetailFragment,
                    bundleOf("postFolderUri" to post.folderUri.toString())
                )
            },
            onCollectionClick = { /* no collections on favorites */ },
            onSearchChanged = { query ->
                searchQuery = query
                rebuildList()
            }
        )

        val layoutManager = LinearLayoutManager(requireContext())
        binding.favoritesRecycler.layoutManager = layoutManager
        binding.favoritesRecycler.adapter = listAdapter

        binding.favoritesRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val last = layoutManager.findLastVisibleItemPosition()
                val total = listAdapter?.itemCount ?: 0
                if (last >= total - 3 && displayedCount < filteredPosts().size) {
                    loadNextPage()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            PatreonRepository.favoritesFlow(requireContext()).collect { posts ->
                allPosts.clear()
                allPosts.addAll(posts)
                if (displayedCount < PAGE_SIZE) displayedCount = minOf(PAGE_SIZE, posts.size)
                rebuildList()
            }
        }
    }

    private fun filteredPosts(): List<PostInfo> {
        if (searchQuery.isBlank()) return allPosts
        val q = searchQuery.lowercase()
        return allPosts.filter { it.title.lowercase().contains(q) }
    }

    private fun rebuildList() {
        val filtered = filteredPosts()
        val shown = if (searchQuery.isBlank()) minOf(displayedCount, filtered.size) else filtered.size
        val postCount = filtered.size

        val items = mutableListOf<CreatorListItem>()
        items.add(CreatorListItem.Header(
            name = "Favorites",
            meta = if (postCount == 1) "1 post" else "$postCount posts",
            bio = if (allPosts.isEmpty()) "Click the heart on posts to add them to your favorites." else "Your saved posts, in one place",
            avatarUrl = null,
            coverUrl = null
        ))
        items.add(CreatorListItem.SearchBar)

        if (shown > 0) {
            items.add(CreatorListItem.SectionLabel("Posts ($postCount)"))
            for (i in 0 until shown) items.add(CreatorListItem.Post(filtered[i]))
            if (searchQuery.isBlank() && shown < filtered.size) {
                items.add(CreatorListItem.LoadMore)
            }
        } else {
            items.add(CreatorListItem.SectionLabel("Posts (0)"))
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
