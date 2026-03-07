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
import dev.heckr.ptdl.data.PatreonRepository
import dev.heckr.ptdl.R

class PostsAdapter(
    private val onClick: (PostInfo) -> Unit
) : ListAdapter<PostInfo, PostsAdapter.ViewHolder>(Diff) {

    inner class ViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostInfo) {
            binding.postTitle.text = post.title

            binding.postDate.text = formatDate(post.publishedAt)

            binding.likeCount.text = binding.root.context.getString(R.string.like_count_format, post.likeCount)
            binding.commentCount.text = binding.root.context.getString(R.string.comment_count_format, post.commentCount)

            binding.lockLabel.isVisible = !post.canView

            if (post.thumbnailUri != null) {
                binding.postThumbnail.isVisible = true
                binding.postThumbnail.load(post.thumbnailUri) {
                    crossfade(true)
                }
            } else {
                binding.postThumbnail.isVisible = false
            }

            // Favorite toggle
            val isFav = PatreonRepository.isFavorite(binding.root.context, post.id)
            binding.favoriteButton.setImageResource(
                if (isFav) R.drawable.icon_favorite_filled else R.drawable.icon_favorite_empty
            )
            binding.favoriteButton.setOnClickListener {
                val nowFav = PatreonRepository.toggleFavorite(binding.root.context, post.id)
                binding.favoriteButton.setImageResource(
                    if (nowFav) R.drawable.icon_favorite_filled else R.drawable.icon_favorite_empty
                )
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
