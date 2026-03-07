package dev.heckr.ptdl.ui.post

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
import coil.transform.RoundedCornersTransformation
import dev.heckr.ptdl.R
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

            binding.likeCount.text = "♡  ${detail.likeCount}"
            binding.commentCount.text = "💬  ${detail.commentCount}"

            // Thumbnail grid for attachments (images and videos)
            val attachments = detail.attachmentUris
            if (attachments.isNotEmpty()) {
                binding.attachmentsLabel.text = "${attachments.size} attachment${if (attachments.size != 1) "s" else ""}"
                binding.attachmentsLabel.isVisible = true
                binding.attachmentsGrid.isVisible = true

                val spanCount = if (attachments.size == 1) 2 else 3
                binding.attachmentsGrid.layoutManager = GridLayoutManager(requireContext(), spanCount)
                binding.attachmentsGrid.adapter = AttachmentGridAdapter(attachments) { index ->
                    val uriStrings = attachments.map { it.toString() }.toTypedArray()
                    val intent = android.content.Intent(
                        requireContext(),
                        dev.heckr.ptdl.ui.viewer.ImageViewerActivity::class.java
                    ).apply {
                        putExtra("imageUris", uriStrings)
                        putExtra("startIndex", index)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class AttachmentGridAdapter(
    private val uris: List<Uri>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<AttachmentGridAdapter.VH>() {

    override fun getItemCount() = uris.size

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
            // Add spacing via layout params
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
            val uri = uris[position]
            b.attachmentThumb.load(uri) {
                crossfade(true)
                size(300, 300)
            }

            val lastSeg = uri.lastPathSegment ?: uri.toString()
            val lower = lastSeg.lowercase(Locale.getDefault())
            val isVideo = lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".3gp")
            b.attachmentPlayIcon.isVisible = isVideo

            b.root.setOnClickListener { onClick(position) }
        }

        fun recycle() {
            b.attachmentThumb.setImageDrawable(null)
            b.attachmentPlayIcon.setImageDrawable(null)
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
