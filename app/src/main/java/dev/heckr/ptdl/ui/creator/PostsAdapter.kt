package dev.heckr.ptdl.ui.creator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.ItemPostBinding
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PostsAdapter(
    private val onClick: (PostInfo) -> Unit
) : ListAdapter<PostInfo, PostsAdapter.ViewHolder>(Diff) {

    inner class ViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostInfo) {
            binding.postTitle.text = post.title

            binding.postDate.text = formatDate(post.publishedAt)

            binding.likeCount.text = "♡ ${post.likeCount}"
            binding.commentCount.text = "💬 ${post.commentCount}"

            binding.lockLabel.isVisible = !post.canView

            if (post.thumbnailUri != null) {
                binding.postThumbnail.isVisible = true
                binding.postThumbnail.load(post.thumbnailUri) {
                    crossfade(true)
                }
            } else {
                binding.postThumbnail.isVisible = false
            }

            binding.root.setOnClickListener { onClick(post) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object Diff : DiffUtil.ItemCallback<PostInfo>() {
        override fun areItemsTheSame(a: PostInfo, b: PostInfo) = a.folderUri == b.folderUri
        override fun areContentsTheSame(a: PostInfo, b: PostInfo) = a == b
    }
}

private fun formatDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val odt = OffsetDateTime.parse(isoDate)
        odt.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    } catch (_: Exception) {
        isoDate.take(10)
    }
}
