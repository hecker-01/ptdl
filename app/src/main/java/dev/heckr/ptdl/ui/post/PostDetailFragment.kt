package dev.heckr.ptdl.ui.post

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import androidx.documentfile.provider.DocumentFile
import dev.heckr.ptdl.R
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.data.LocalFileScanner
import dev.heckr.ptdl.databinding.FragmentPostDetailBinding
import dev.heckr.ptdl.databinding.ItemAttachmentThumbBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val postUri = Uri.parse(arguments?.getString("postFolderUri") ?: return)
        loadPost(postUri)
    }

    private fun loadPost(postUri: Uri) {
        binding.progressBar.isVisible = true
        binding.scrollView.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                LocalFileScanner.loadPostDetail(requireContext(), postUri)
            }
            if (_binding == null) return@launch

            binding.progressBar.isVisible = false
            binding.scrollView.isVisible = true

            binding.postTitle.text = detail.title
            binding.toolbar.title = ""

            binding.postDate.text = formatDate(detail.publishedAt)

            // Setup favorite action in toolbar
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_post_detail)
            val folderDoc = DocumentFile.fromTreeUri(requireContext(), postUri)
            val folderName = folderDoc?.name ?: postUri.lastPathSegment ?: ""
            val postId = folderName.split(" - ", limit = 2)[0].trim()
            val favItem = binding.toolbar.menu.findItem(R.id.action_favorite)
            favItem?.setIcon(
                if (PatreonRepository.isFavorite(requireContext(), postId))
                    R.drawable.icon_favorite_filled
                else R.drawable.icon_favorite_empty
            )
            binding.toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_favorite) {
                    val now = PatreonRepository.toggleFavorite(requireContext(), postId)
                    item.setIcon(if (now) R.drawable.icon_favorite_filled else R.drawable.icon_favorite_empty)
                    true
                } else false
            }

            val richContent = when {
                detail.contentJsonString.isNotBlank() && detail.contentJsonString != "null" ->
                    LocalFileScanner.parseContentJsonRich(detail.contentJsonString)
                detail.contentHtml.isNotBlank() ->
                    LocalFileScanner.parseHtmlRich(detail.contentHtml)
                else -> detail.content.trim()
            }
            if (richContent.isNotBlank()) {
                binding.postContent.text = richContent
                binding.postContent.isVisible = true
            }

            binding.likeCount.text = getString(R.string.like_count_detail_format, detail.likeCount)
            binding.commentCount.text = getString(R.string.comment_count_detail_format, detail.commentCount)

            // Unified image + video grid
            val imageUris = detail.imageUris
            val allMedia = imageUris.map { MediaAttachment(it, false) } +
                detail.videoUris.map { MediaAttachment(it, true) }

            if (allMedia.isNotEmpty()) {
                binding.attachmentsLabel.text = resources.getQuantityString(
                    R.plurals.attachment_count, allMedia.size, allMedia.size
                )
                binding.attachmentsLabel.isVisible = true
                binding.attachmentsGrid.isVisible = true

                val spanCount = if (allMedia.size == 1) 2 else 3
                binding.attachmentsGrid.layoutManager = GridLayoutManager(requireContext(), spanCount)
                binding.attachmentsGrid.adapter = AttachmentGridAdapter(allMedia) { index ->
                    val item = allMedia[index]
                    if (item.isVideo) {
                        startActivity(
                            Intent(requireContext(), dev.heckr.ptdl.ui.viewer.VideoPlayerActivity::class.java)
                                .putExtra("videoUri", item.uri.toString())
                        )
                    } else {
                        val imageUriStrings = imageUris.map { it.toString() }.toTypedArray()
                        startActivity(
                            Intent(requireContext(), dev.heckr.ptdl.ui.viewer.ImageViewerActivity::class.java)
                                .putExtra("imageUris", imageUriStrings)
                                .putExtra("startIndex", index)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private data class MediaAttachment(val uri: Uri, val isVideo: Boolean)

private class AttachmentGridAdapter(
    private val items: List<MediaAttachment>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<AttachmentGridAdapter.VH>() {

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAttachmentThumbBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(position)

    override fun onViewRecycled(holder: VH) {
        holder.recycle()
    }

    inner class VH(private val b: ItemAttachmentThumbBinding) :
        RecyclerView.ViewHolder(b.root) {

        init {
            val spacing = (4 * b.root.resources.displayMetrics.density).toInt()
            (b.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.setMargins(spacing, spacing, spacing, spacing)
            } ?: run {
                b.root.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(spacing, spacing, spacing, spacing) }
            }
        }

        fun bind(position: Int) {
            val item = items[position]
            if (item.isVideo) {
                b.attachmentThumb.setImageDrawable(null)
                b.playIcon.isVisible = true
            } else {
                b.attachmentThumb.load(item.uri) {
                    crossfade(true)
                    size(300, 300)
                }
                b.playIcon.isVisible = false
            }
            b.root.setOnClickListener { onClick(position) }
        }

        fun recycle() {
            b.attachmentThumb.setImageDrawable(null)
            b.playIcon.isVisible = false
        }
    }
}

private fun formatDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val odt = OffsetDateTime.parse(isoDate)
        odt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    } catch (_: Exception) {
        isoDate.take(10)
    }
}
