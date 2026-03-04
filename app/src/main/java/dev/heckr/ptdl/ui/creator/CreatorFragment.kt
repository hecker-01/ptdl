package dev.heckr.ptdl.ui.creator

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import dev.heckr.ptdl.R
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

    private var adapter: PostsAdapter? = null

    private var allPosts: List<PostInfo> = emptyList()
    private var displayedCount = 0

    companion object {
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

        adapter = PostsAdapter { post ->
            findNavController().navigate(
                R.id.action_creator_to_post,
                bundleOf("postFolderUri" to post.folderUri.toString())
            )
        }
        binding.postsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.postsRecycler.adapter = adapter

        binding.nestedScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
                val child = v.getChildAt(0) ?: return@OnScrollChangeListener
                val remaining = child.height - v.height - scrollY
                if (remaining < 600 && displayedCount < allPosts.size) {
                    loadNextPage()
                }
            }
        )

        val creatorUri = Uri.parse(arguments?.getString("creatorFolderUri") ?: return)
        loadCreator(creatorUri)
    }

    private fun loadCreator(creatorUri: Uri) {
        binding.progressBar.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val (creator, posts) = withContext(Dispatchers.IO) {
                val creator = LocalFileScanner.parseCreatorFromUri(requireContext(), creatorUri)
                val postList = PatreonRepository.loadPosts(requireContext(), creatorUri)
                Pair(creator, postList)
            }

            if (_binding == null) return@launch
            binding.progressBar.isVisible = false

            if (creator != null) {
                binding.creatorName.text = creator.name
                binding.creatorMeta.text = buildString {
                    if (creator.patronCount > 0) append("${creator.patronCount} patrons")
                    if (creator.postCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${creator.postCount} posts")
                    }
                }
                if (creator.summary.isNotBlank()) {
                    binding.creatorBio.text = creator.summary
                    binding.creatorBio.isVisible = true
                }
                binding.collapsingToolbar.title = creator.name

                creator.coverUrl?.let { url ->
                    binding.coverImage.load(url) { crossfade(true) }
                }
                creator.avatarUrl?.let { url ->
                    binding.avatarImage.load(url) { crossfade(true) }
                }
            }

            allPosts = posts
            displayedCount = minOf(PAGE_SIZE, posts.size)
            adapter?.submitList(allPosts.take(displayedCount))
            binding.postsLabel.text = "Posts (${posts.size})"
            updateLoadMore()
        }
    }

    private fun loadNextPage() {
        if (displayedCount >= allPosts.size) return
        displayedCount = minOf(displayedCount + PAGE_SIZE, allPosts.size)
        adapter?.submitList(allPosts.take(displayedCount))
        updateLoadMore()
    }

    private fun updateLoadMore() {
        binding.loadMoreContainer.isVisible = displayedCount < allPosts.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

