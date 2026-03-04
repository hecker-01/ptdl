package dev.heckr.ptdl.ui.post

import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import coil.transform.RoundedCornersTransformation
import dev.heckr.ptdl.data.LocalFileScanner
import dev.heckr.ptdl.databinding.FragmentPostDetailBinding
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
            binding.toolbar.title = ""   // title shown in card below images

            val dateText = formatDate(detail.publishedAt)
            binding.postDate.text = dateText

            // Rich content rendering
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

            // Populate images — add a small margin + rounded corners for polish
            binding.imagesContainer.removeAllViews()
            val cornerPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
            )
            val marginPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics
            ).toInt()
            val spacingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
            ).toInt()

            val imageUris = detail.imageUris
            for ((index, uri) in imageUris.withIndex()) {
                val imageView = ImageView(requireContext()).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { lp ->
                        lp.leftMargin = marginPx
                        lp.rightMargin = marginPx
                        lp.bottomMargin = if (index < imageUris.lastIndex) spacingPx else 0
                    }
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    clipToOutline = true
                }
                imageView.load(uri) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(cornerPx))
                }
                // Tap to open fullscreen viewer
                imageView.setOnClickListener {
                    val uriStrings = imageUris.map { it.toString() }.toTypedArray()
                    val intent = android.content.Intent(requireContext(), dev.heckr.ptdl.ui.viewer.ImageViewerActivity::class.java).apply {
                        putExtra("imageUris", uriStrings)
                        putExtra("startIndex", index)
                    }
                    startActivity(intent)
                }
                binding.imagesContainer.addView(imageView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
