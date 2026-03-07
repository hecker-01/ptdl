package dev.heckr.ptdl.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import dev.heckr.ptdl.R
import dev.heckr.ptdl.data.CreatorInfo
import dev.heckr.ptdl.databinding.ItemCreatorBinding

class CreatorsAdapter(
    private val onClick: (CreatorInfo) -> Unit,
    private val onVisible: (CreatorInfo) -> Unit = {}
) : ListAdapter<CreatorInfo, CreatorsAdapter.ViewHolder>(Diff) {

    inner class ViewHolder(private val binding: ItemCreatorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(creator: CreatorInfo) {
            binding.creatorName.text = creator.name
            binding.creatorSubtitle.text = creator.creationName.ifBlank { creator.folderName }
            binding.postCount.text = binding.root.context.resources.getQuantityString(R.plurals.post_count, creator.postCount, creator.postCount)

            if (creator.coverUrl != null) {
                binding.coverImage.load(creator.coverUrl) {
                    crossfade(true)
                }
            } else {
                binding.coverImage.setImageDrawable(null)
            }

            if (creator.avatarUrl != null) {
                binding.avatarImage.load(creator.avatarUrl) {
                    crossfade(true)
                }
            } else {
                binding.avatarImage.setImageDrawable(null)
            }

            binding.root.setOnClickListener { onClick(creator) }
            onVisible(creator)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCreatorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object Diff : DiffUtil.ItemCallback<CreatorInfo>() {
        override fun areItemsTheSame(a: CreatorInfo, b: CreatorInfo) = a.folderUri == b.folderUri
        override fun areContentsTheSame(a: CreatorInfo, b: CreatorInfo) = a == b
    }
}
