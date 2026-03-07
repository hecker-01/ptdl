package dev.heckr.ptdl.data

import android.content.Context
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object LocalFileScanner {

    // --- Creator scanning ----------------------------------------------------

    suspend fun scanCreators(context: Context, rootUri: Uri): List<CreatorInfo> = coroutineScope {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@coroutineScope emptyList()
        rootDoc.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(".") == false }
            .map { folder -> async(Dispatchers.IO) { parseCreator(context, folder) } }
            .awaitAll()
            .filterNotNull()
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

    // --- Post scanning -------------------------------------------------------

    suspend fun scanPosts(context: Context, creatorFolderUri: Uri): List<PostInfo> = coroutineScope {
        val creatorDoc = DocumentFile.fromTreeUri(context, creatorFolderUri) ?: return@coroutineScope emptyList()
        val postsDir = creatorDoc.findFile("posts") ?: return@coroutineScope emptyList()
        val folders = postsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(".") == false }
        // Parse in parallel chunks of 8 for much faster SAF throughput
        folders.chunked(8).flatMap { chunk ->
            chunk.map { folder ->
                async(Dispatchers.IO) { parsePostShallow(context, folder) }
            }.awaitAll().filterNotNull()
        }.sortedByDescending { it.publishedAt }
    }

    /**
     * Streaming version - emits each [PostInfo] as soon as it's parsed,
     * using parallel workers for faster throughput.
     */
    fun scanPostsFlow(context: Context, creatorFolderUri: Uri): Flow<PostInfo> = flow {
        val creatorDoc = DocumentFile.fromTreeUri(context, creatorFolderUri) ?: return@flow
        val postsDir = creatorDoc.findFile("posts") ?: return@flow
        val folders = postsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(".") == false }
            .sortedByDescending { it.name }

        val channel = Channel<PostInfo>(Channel.BUFFERED)
        coroutineScope {
            // Launch parallel workers in chunks of 8
            launch(Dispatchers.IO) {
                coroutineScope {
                    folders.chunked(8).forEach { chunk ->
                        chunk.map { folder ->
                            async {
                                parsePostShallow(context, folder)?.let { channel.send(it) }
                            }
                        }.awaitAll()
                    }
                }
                channel.close()
            }
            for (post in channel) {
                emit(post)
            }
        }
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

    // --- Post detail ---------------------------------------------------------

    data class PostDetail(
        val title: String,
        val content: String,
        val contentJsonString: String,
        val contentHtml: String,
        val publishedAt: String,
        val likeCount: Int,
        val commentCount: Int,
        val postType: String,
        val canView: Boolean,
        val imageUris: List<Uri>,
        val attachmentUris: List<Uri>
    )

    fun loadPostDetail(context: Context, postFolderUri: Uri): PostDetail {
        val postDoc = DocumentFile.fromTreeUri(context, postFolderUri)
            ?: return PostDetail("", "", "", "", "", 0, 0, "text_only", false, emptyList(), emptyList())

        var title = ""
        var content = ""
        var contentJsonString = ""
        var contentHtml = ""
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

                    contentJsonString = attrs.optString("content_json_string", "")
                    contentHtml = attrs.optString("content", "")
                    content = if (contentJsonString.isNotBlank() && contentJsonString != "null") {
                        parseContentJson(contentJsonString)
                    } else {
                        stripHtml(contentHtml)
                    }
                } catch (_: Exception) {}
            }
        }

        val imageUris = scanPostImages(context, postFolderUri)
        val attachmentUris = scanPostAttachments(context, postFolderUri)
        return PostDetail(title, content, contentJsonString, contentHtml, publishedAt, likeCount, commentCount, postType, canView, imageUris, attachmentUris)
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

    fun scanPostAttachments(context: Context, postFolderUri: Uri): List<Uri> {
        val postDoc = DocumentFile.fromTreeUri(context, postFolderUri) ?: return emptyList()
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
        val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "3gp")
        val allowed = imageExtensions + videoExtensions

        fun DocumentFile.attachmentFiles() = listFiles()
            .filter { f -> f.isFile && f.name?.substringAfterLast('.', "")?.lowercase() in allowed }
            .sortedBy { it.name }
            .map { it.uri }

        val images = postDoc.findFile("images")?.listFiles()
            ?.filter { f -> f.isFile && f.name?.substringAfterLast('.', "")?.lowercase() in imageExtensions }
            ?.sortedBy { it.name }
            ?.map { it.uri } ?: emptyList()
        if (images.isNotEmpty()) return images

        return postDoc.findFile("attachments")?.attachmentFiles() ?: emptyList()
    }

    // --- Helpers -------------------------------------------------------------

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

    /**
     * Parses content_json_string into a styled [CharSequence] with bold, italic,
     * underline, and heading sizes preserved.
     */
    fun parseContentJsonRich(jsonString: String): CharSequence {
        if (jsonString.isBlank() || jsonString == "null") return ""
        return try {
            val sb = SpannableStringBuilder()
            extractRichText(JSONObject(jsonString), sb)
            sb
        } catch (_: Exception) {
            jsonString
        }
    }

    private fun extractRichText(node: JSONObject, sb: SpannableStringBuilder) {
        val type = node.optString("type", "")
        val text = node.optString("text", "")

        if (text.isNotEmpty()) {
            val start = sb.length
            sb.append(text)
            // Apply marks (bold, italic, underline)
            val marks = node.optJSONArray("marks")
            if (marks != null) {
                for (i in 0 until marks.length()) {
                    when (marks.getJSONObject(i).optString("type")) {
                        "bold" -> sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "italic" -> sb.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "underline" -> sb.setSpan(UnderlineSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            return
        }

        val content = node.optJSONArray("content") ?: return
        val startPos = sb.length
        for (i in 0 until content.length()) {
            try { extractRichText(content.getJSONObject(i), sb) } catch (_: Exception) {}
        }
        when (type) {
            "paragraph" -> sb.append("\n")
            "heading" -> {
                val level = node.optJSONObject("attrs")?.optInt("level", 2) ?: 2
                val scale = when (level) { 1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.0f }
                sb.setSpan(RelativeSizeSpan(scale), startPos, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), startPos, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append("\n")
            }
        }
    }

    /** Parses HTML content into a styled [CharSequence]. */
    fun parseHtmlRich(html: String): CharSequence {
        if (html.isBlank()) return ""
        return androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    // --- Collection scanning -------------------------------------------------

    suspend fun scanCollections(context: Context, creatorFolderUri: Uri): List<CollectionInfo> = coroutineScope {
        val creatorDoc = DocumentFile.fromTreeUri(context, creatorFolderUri) ?: return@coroutineScope emptyList()
        val collectionsDir = creatorDoc.findFile("collections") ?: return@coroutineScope emptyList()
        collectionsDir.listFiles()
            .filter { it.isDirectory && it.name?.startsWith(".") == false }
            .map { folder -> async(Dispatchers.IO) { parseCollection(context, folder) } }
            .awaitAll()
            .filterNotNull()
            .sortedBy { it.title.lowercase() }
    }

    private fun parseCollection(context: Context, folder: DocumentFile): CollectionInfo? {
        val folderName = folder.name ?: return null
        val parts = folderName.split(" - ", limit = 2)
        val idFromFolder = parts[0].trim()
        val titleFromFolder = if (parts.size > 1) parts[1].trim() else folderName

        var id = idFromFolder
        var title = titleFromFolder
        var description = ""
        var postCount = 0
        var postIds = emptyList<Long>()
        var thumbnailUrl: String? = null
        var thumbnailUri: Uri? = null

        // Look for local thumbnail image
        val thumbFile = folder.listFiles().firstOrNull {
            it.isFile && it.name?.startsWith("collection-") == true &&
                it.name?.substringAfterLast('.', "")?.lowercase() in setOf("png", "jpg", "jpeg", "webp")
        }
        if (thumbFile != null) thumbnailUri = thumbFile.uri

        val apiFile = folder.findFile("collection-api.json")
        if (apiFile != null) {
            try {
                val json = readFile(context, apiFile)
                val root = JSONObject(json)
                id = root.optString("id", idFromFolder)
                val attrs = root.getJSONObject("attributes")
                title = attrs.optString("title", titleFromFolder).ifBlank { titleFromFolder }
                description = attrs.optString("description", "")
                postCount = attrs.optInt("num_posts", 0)
                val idsArr = attrs.optJSONArray("post_ids")
                if (idsArr != null) {
                    postIds = (0 until idsArr.length()).map { idsArr.getLong(it) }
                }
                val thumb = attrs.optJSONObject("thumbnail")
                if (thumb != null) {
                    thumbnailUrl = thumb.optString("default", "").ifBlank { null }
                }
            } catch (_: Exception) {}
        }

        return CollectionInfo(
            id = id,
            title = title,
            description = description,
            postCount = postCount,
            postIds = postIds,
            thumbnailUri = thumbnailUri,
            thumbnailUrl = thumbnailUrl,
            folderUri = folder.uri
        )
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
