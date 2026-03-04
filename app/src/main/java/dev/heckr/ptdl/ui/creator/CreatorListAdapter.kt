package dev.heckr.ptdl.ui.creator

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.heckr.ptdl.data.CollectionInfo
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.ItemCollectionBinding
import dev.heckr.ptdl.databinding.ItemCollectionsRowBinding
import dev.heckr.ptdl.databinding.ItemCreatorHeaderBinding
import dev.heckr.ptdl.databinding.ItemLoadMoreBinding
import dev.heckr.ptdl.databinding.ItemPostBinding
import dev.heckr.ptdl.databinding.ItemSearchBarBinding
import dev.heckr.ptdl.databinding.ItemSectionLabelBinding
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sealed hierarchy for all row types in the Creator screen's single RecyclerView.
 */
sealed class CreatorListItem {
    data class Header(
        val name: String,
        val meta: String,
        val bio: String,
        val avatarUrl: String?,
        val coverUrl: String?          // not used here; toolbar handles it
    ) : CreatorListItem()

    data object SearchBar : CreatorListItem()

    data class SectionLabel(val label: String) : CreatorListItem()

    data class CollectionsRow(val collections: List<CollectionInfo>) : CreatorListItem()

    data class Post(val post: PostInfo) : CreatorListItem()

    data object LoadMore : CreatorListItem()
}

class CreatorListAdapter(
    private val onPostClick: (PostInfo) -> Unit,
    private val onCollectionClick: (CollectionInfo) -> Unit,
    private val onSearchChanged: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SEARCH = 1
        private const val TYPE_SECTION = 2
        private const val TYPE_COLLECTIONS = 3
        private const val TYPE_POST = 4
        private const val TYPE_LOAD_MORE = 5
    }

    private var items: List<CreatorListItem> = emptyList()

    fun submitItems(newItems: List<CreatorListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is CreatorListItem.Header -> TYPE_HEADER
        is CreatorListItem.SearchBar -> TYPE_SEARCH
        is CreatorListItem.SectionLabel -> TYPE_SECTION
        is CreatorListItem.CollectionsRow -> TYPE_COLLECTIONS
        is CreatorListItem.Post -> TYPE_POST
        is CreatorListItem.LoadMore -> TYPE_LOAD_MORE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemCreatorHeaderBinding.inflate(inflater, parent, false))
            TYPE_SEARCH -> SearchVH(ItemSearchBarBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionVH(ItemSectionLabelBinding.inflate(inflater, parent, false))
            TYPE_COLLECTIONS -> CollectionsVH(ItemCollectionsRowBinding.inflate(inflater, parent, false))
            TYPE_POST -> PostVH(ItemPostBinding.inflate(inflater, parent, false))
            TYPE_LOAD_MORE -> LoadMoreVH(ItemLoadMoreBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CreatorListItem.Header -> (holder as HeaderVH).bind(item)
            is CreatorListItem.SearchBar -> { /* watcher set in onCreateViewHolder */ }
            is CreatorListItem.SectionLabel -> (holder as SectionVH).bind(item)
            is CreatorListItem.CollectionsRow -> (holder as CollectionsVH).bind(item)
            is CreatorListItem.Post -> (holder as PostVH).bind(item.post)
            is CreatorListItem.LoadMore -> { /* spinner auto-shows */ }
        }
    }

    // ── ViewHolders ────────────────────────────────────────────────────

    inner class HeaderVH(private val b: ItemCreatorHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(h: CreatorListItem.Header) {
            b.creatorName.text = h.name
            b.creatorMeta.text = h.meta
            if (h.bio.isNotBlank()) {
                b.creatorBio.text = h.bio
                b.creatorBio.isVisible = true
            } else {
                b.creatorBio.isVisible = false
            }
            h.avatarUrl?.let { b.avatarImage.load(it) { crossfade(true) } }
        }
    }

    inner class SearchVH(b: ItemSearchBarBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onSearchChanged(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    inner class SectionVH(private val b: ItemSectionLabelBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(item: CreatorListItem.SectionLabel) {
            b.sectionLabel.text = item.label
        }
    }

    inner class CollectionsVH(private val b: ItemCollectionsRowBinding) :
        RecyclerView.ViewHolder(b.root) {
        private val innerAdapter = CollectionsInnerAdapter { onCollectionClick(it) }
        init {
            b.collectionsRecycler.layoutManager =
                LinearLayoutManager(b.root.context, LinearLayoutManager.HORIZONTAL, false)
            b.collectionsRecycler.adapter = innerAdapter
        }
        fun bind(item: CreatorListItem.CollectionsRow) {
            innerAdapter.submitList(item.collections)
        }
    }

    inner class PostVH(private val b: ItemPostBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(post: PostInfo) {
            b.postTitle.text = post.title
            b.postDate.text = formatDate(post.publishedAt)
            b.likeCount.text = "♡ ${post.likeCount}"
            b.commentCount.text = "💬 ${post.commentCount}"
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

    inner class LoadMoreVH(b: ItemLoadMoreBinding) : RecyclerView.ViewHolder(b.root)
}

// ── Inner adapter for the horizontal collections row ──────────────────

private class CollectionsInnerAdapter(
    private val onClick: (CollectionInfo) -> Unit
) : RecyclerView.Adapter<CollectionsInnerAdapter.VH>() {

    private var list: List<CollectionInfo> = emptyList()
    fun submitList(newList: List<CollectionInfo>) { list = newList; notifyDataSetChanged() }

    override fun getItemCount() = list.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(list[position])

    inner class VH(private val b: ItemCollectionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: CollectionInfo) {
            b.collectionTitle.text = c.title
            b.collectionPostCount.text = "${c.postCount} posts"
            val thumb = c.thumbnailUri ?: c.thumbnailUrl
            if (thumb != null) {
                b.collectionThumbnail.load(thumb) { crossfade(true) }
            }
            b.root.setOnClickListener { onClick(c) }
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
