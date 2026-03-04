package dev.heckr.ptdl.ui.creator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.ItemPostBinding
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CollectionPostsAdapter(
    private val onPostClick: (PostInfo) -> Unit
) : RecyclerView.Adapter<CollectionPostsAdapter.VH>() {

    private var list: List<PostInfo> = emptyList()

    fun submitList(newList: List<PostInfo>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(list[position])

    inner class VH(private val b: ItemPostBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(post: PostInfo) {
            b.postTitle.text = post.title
            b.postDate.text = formatDate(post.publishedAt)
            b.likeCount.text = "♡ ${post.likeCount}"
            b.commentCount.text = "\uD83D\uDCAC ${post.commentCount}"
            b.lockLabel.isVisible = !post.canView
            if (post.thumbnailUri != null) {
                b.postThumbnail.isVisible = true
                b.postThumbnail.load(post.thumbnailUri) { crossfade(true) }
            } else {
                b.postThumbnail.isVisible = false
            }
            b.root.setOnClickListener { onPostClick(post) }
        }
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
