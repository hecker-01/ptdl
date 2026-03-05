package dev.heckr.ptdl.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.heckr.ptdl.databinding.ItemFavoritesBinding

class FavoritesAdapter(
    private val onClick: () -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

    inner class ViewHolder(binding: ItemFavoritesBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { onClick() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoritesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit

    override fun getItemCount() = 1
}
