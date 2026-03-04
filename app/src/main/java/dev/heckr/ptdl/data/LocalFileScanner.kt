package dev.heckr.ptdl.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object LocalFileScanner {

    // ─── Creator scanning ────────────────────────────────────────────────────

    fun scanCreators(context: Context, rootUri: Uri): List<CreatorInfo> {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        return rootDoc.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(".") == false }
            .mapNotNull { parseCreator(context, it) }
            .sortedBy { it.name.lowercase() }
    }

    fun parseCreatorFromUri(context: Context, folderUri: Uri): CreatorInfo? {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        return parseCreator(context, folder)
    }

    private fun parseCreator(context: Context, folder: DocumentFile): CreatorInfo? {
        val campaignInfoDir = folder.findFile("campaign_info") ?: return null
        val postsDir = folder.findFile("posts")
        val postCount = postsDir?.listFiles()?.count { it.isDirectory } ?: 0

        var id = ""
        var name = folder.name ?: "Unknown"
        var creationName = ""
        var summary = ""
        var patronCount = 0
        var avatarUrl: String? = null
        var coverUrl: String? = null

        val campaignApiFile = campaignInfoDir.findFile("campaign-api.json")
        if (campaignApiFile != null) {
            try {
                val json = readFile(context, campaignApiFile)
                val attrs = JSONObject(json)
                    .getJSONObject("data")
                    .getJSONObject("attributes")
                id = JSONObject(json).getJSONObject("data").optString("id", "")
                name = attrs.optString("name", name)
                creationName = attrs.optString("creation_name", "")
                summary = stripHtml(attrs.optString("summary", ""))
                patronCount = attrs.optInt("patron_count", 0)
                avatarUrl = attrs.optString("avatar_photo_url", "").ifBlank { null }
                coverUrl = attrs.optString("cover_photo_url", "").ifBlank { null }
            } catch (_: Exception) {}
        }

        return CreatorInfo(
            id = id,
            folderName = folder.name ?: "",
            name = name,
            creationName = creationName,
            summary = summary,
            patronCount = patronCount,
            postCount = postCount,
            folderUri = folder.uri,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl
        )
    }

    // ─── Post scanning ───────────────────────────────────────────────────────

    fun scanPosts(context: Context, creatorFolderUri: Uri): List<PostInfo> {
        val creatorDoc = DocumentFile.fromTreeUri(context, creatorFolderUri) ?: return emptyList()
        val postsDir = creatorDoc.findFile("posts") ?: return emptyList()
        return postsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(".") == false }
            .mapNotNull { parsePostShallow(context, it) }
            .sortedByDescending { it.publishedAt }
    }

    private fun parsePostShallow(context: Context, postFolder: DocumentFile): PostInfo? {
        val folderName = postFolder.name ?: return null
        val parts = folderName.split(" - ", limit = 2)
        val idFromFolder = parts[0].trim()
        val titleFromFolder = if (parts.size > 1) parts[1].trim() else folderName

        var title = titleFromFolder
        var publishedAt = ""
        var postType = "text_only"
        var canView = true
        var likeCount = 0
        var commentCount = 0
        var thumbnailUri: Uri? = null

        val postInfoDir = postFolder.findFile("post_info")
        if (postInfoDir != null) {
            val postApiFile = postInfoDir.findFile("post-api.json")
            if (postApiFile != null) {
                try {
                    val json = readFile(context, postApiFile)
                    val attrs = JSONObject(json)
                        .getJSONObject("data")
                        .getJSONObject("attributes")
                    title = attrs.optString("title", titleFromFolder).ifBlank { titleFromFolder }
                    publishedAt = attrs.optString("published_at", "")
                    postType = attrs.optString("post_type", "text_only")
                    canView = attrs.optBoolean("current_user_can_view", true)
                    likeCount = attrs.optInt("like_count", 0)
                    commentCount = attrs.optInt("comment_count", 0)
                } catch (_: Exception) {}
            }
            val thumb = postInfoDir.findFile("thumbnail.webp")
                ?: postInfoDir.findFile("cover-image.webp")
            if (thumb != null) thumbnailUri = thumb.uri
        }

        return PostInfo(
            id = idFromFolder,
            title = title,
            publishedAt = publishedAt,
            postType = postType,
            canView = canView,
            likeCount = likeCount,
            commentCount = commentCount,
            thumbnailUri = thumbnailUri,
            folderUri = postFolder.uri
        )
    }

    // ─── Post detail ─────────────────────────────────────────────────────────

    data class PostDetail(
        val title: String,
        val content: String,
        val publishedAt: String,
        val likeCount: Int,
        val commentCount: Int,
        val postType: String,
        val canView: Boolean,
        val imageUris: List<Uri>
    )

    fun loadPostDetail(context: Context, postFolderUri: Uri): PostDetail {
        val postDoc = DocumentFile.fromTreeUri(context, postFolderUri)
            ?: return PostDetail("", "", "", 0, 0, "text_only", false, emptyList())

        var title = ""
        var content = ""
        var publishedAt = ""
        var likeCount = 0
        var commentCount = 0
        var postType = "text_only"
        var canView = true

        val postInfoDir = postDoc.findFile("post_info")
        if (postInfoDir != null) {
            val postApiFile = postInfoDir.findFile("post-api.json")
            if (postApiFile != null) {
                try {
                    val json = readFile(context, postApiFile)
                    val data = JSONObject(json).getJSONObject("data")
                    val attrs = data.getJSONObject("attributes")
                    title = attrs.optString("title", "")
                    publishedAt = attrs.optString("published_at", "")
                    likeCount = attrs.optInt("like_count", 0)
                    commentCount = attrs.optInt("comment_count", 0)
                    postType = attrs.optString("post_type", "text_only")
                    canView = attrs.optBoolean("current_user_can_view", true)

                    val contentJson = attrs.optString("content_json_string", "")
                    content = if (contentJson.isNotBlank() && contentJson != "null") {
                        parseContentJson(contentJson)
                    } else {
                        stripHtml(attrs.optString("content", ""))
                    }
                } catch (_: Exception) {}
            }
        }

        val imageUris = scanPostImages(context, postFolderUri)
        return PostDetail(title, content, publishedAt, likeCount, commentCount, postType, canView, imageUris)
    }

    fun scanPostImages(context: Context, postFolderUri: Uri): List<Uri> {
        val postDoc = DocumentFile.fromTreeUri(context, postFolderUri) ?: return emptyList()
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

        fun DocumentFile.imageFiles() = listFiles()
            .filter { f -> f.isFile && f.name?.substringAfterLast('.', "")?.lowercase() in imageExtensions }
            .sortedBy { it.name }
            .map { it.uri }

        val images = postDoc.findFile("images")?.imageFiles() ?: emptyList()
        if (images.isNotEmpty()) return images

        return postDoc.findFile("attachments")?.imageFiles() ?: emptyList()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun readFile(context: Context, file: DocumentFile): String {
        val stream = context.contentResolver.openInputStream(file.uri) ?: return ""
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    private fun parseContentJson(jsonString: String): String {
        return try {
            extractText(JSONObject(jsonString)).trim()
        } catch (_: Exception) {
            jsonString
        }
    }

    private fun extractText(node: JSONObject): String {
        val text = node.optString("text", "")
        if (text.isNotEmpty()) return text
        val content = node.optJSONArray("content") ?: return ""
        val type = node.optString("type", "")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            try { sb.append(extractText(content.getJSONObject(i))) } catch (_: Exception) {}
        }
        if (type == "paragraph" || type == "heading") sb.append("\n")
        return sb.toString()
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<br\\s*/?>", setOf(RegexOption.IGNORE_CASE)), "\n")
            .replace(Regex("<[^>]+>"), "")
            .trim()
}
