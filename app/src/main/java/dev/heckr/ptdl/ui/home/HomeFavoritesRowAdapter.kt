package dev.heckr.ptdl.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.databinding.ItemFavoriteThumbBinding

class HomeFavoritesRowAdapter(
    private val onClick: (PostInfo) -> Unit
) : RecyclerView.Adapter<HomeFavoritesRowAdapter.VH>() {

    private val items = mutableListOf<PostInfo>()

    inner class VH(private val b: ItemFavoriteThumbBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: PostInfo) {
            b.thumbImage.load(p.thumbnailUri)
            b.thumbTitle.text = p.title
            b.root.setOnClickListener { onClick(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFavoriteThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    fun submitList(list: List<PostInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
