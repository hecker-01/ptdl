package dev.heckr.ptdl.data

import android.net.Uri

data class CreatorInfo(
    val id: String,
    val folderName: String,
    val name: String,
    val creationName: String,
    val summary: String,
    val patronCount: Int,
    val postCount: Int,
    val folderUri: Uri,
    val avatarUrl: String?,
    val coverUrl: String?
)
