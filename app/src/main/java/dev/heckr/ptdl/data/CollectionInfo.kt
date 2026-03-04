package dev.heckr.ptdl.data

import android.net.Uri

data class CollectionInfo(
    val id: String,
    val title: String,
    val description: String,
    val postCount: Int,
    val postIds: List<Long>,
    val thumbnailUri: Uri?,
    val thumbnailUrl: String?,
    val folderUri: Uri
)
