package dev.heckr.ptdl.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.heckr.ptdl.data.PostInfo
import dev.heckr.ptdl.R
import dev.heckr.ptdl.databinding.ItemFavoritesBinding

class FavoritesAdapter(
    private val onHeaderClick: () -> Unit,
    private val onPostClick: (PostInfo) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

    private var items: List<PostInfo> = emptyList()

    inner class ViewHolder(private val binding: ItemFavoritesBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { onHeaderClick() }
        }

        fun bind(list: List<PostInfo>) {
            binding.root.isVisible = true
            if (list.isEmpty()) {
                binding.headerIcon.setImageResource(R.drawable.icon_favorite_empty)
                binding.headerSubtitle.text = binding.root.context.getString(R.string.favorites_empty_hint)
            } else {
                binding.headerIcon.setImageResource(R.drawable.icon_favorite_filled)
                binding.headerSubtitle.text = binding.root.context.getString(R.string.favorites_header_subtitle)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoritesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items)

    override fun getItemCount() = 1

    fun submitList(list: List<PostInfo>) {
        items = list
        notifyItemChanged(0)
    }
}
