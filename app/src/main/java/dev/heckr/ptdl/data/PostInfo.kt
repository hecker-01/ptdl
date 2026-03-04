package dev.heckr.ptdl.data

import android.net.Uri

data class PostInfo(
    val id: String,
    val title: String,
    val publishedAt: String,
    val postType: String,
    val canView: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val thumbnailUri: Uri?,
    val folderUri: Uri
)
