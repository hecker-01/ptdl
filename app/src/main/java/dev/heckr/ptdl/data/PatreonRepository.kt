package dev.heckr.ptdl.data

import android.content.Context
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object PatreonRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cachedRootUri: String? = null
    @Volatile private var cachedCreators: List<CreatorInfo> = emptyList()
    private val postsCache = ConcurrentHashMap<String, List<PostInfo>>()

    // --- Public API ----------------------------------------------------------

    /** Call once on app start (or folder change) to pre-fill all caches. */
    fun warmUp(context: Context, rootUri: Uri) {
        val appContext = context.applicationContext
        scope.launch {
            val newUriStr = rootUri.toString()
            val creators = LocalFileScanner.scanCreators(appContext, rootUri)
            cachedRootUri = newUriStr
            cachedCreators = creators

            // Pre-scan every creator's posts concurrently
            creators.forEach { creator ->
                launch {
                    val key = creator.folderUri.toString()
                    if (!postsCache.containsKey(key)) {
                        val posts = LocalFileScanner.scanPosts(appContext, creator.folderUri)
                        postsCache[key] = posts
                        preloadThumbnails(appContext, posts)
                    }
                }
            }
        }
    }

    /**
     * Suspending version of warmUp - awaits the full index (creators + all posts)
     * and returns the total number of posts found.
     * [onCreatorProgress] fires after discovery (done=0) and after each creator scan.
     * [onPostProgress] fires with the running total of posts found so far.
     */
    suspend fun warmUpAwait(
        context: Context,
        rootUri: Uri,
        onCreatorProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
        onPostProgress: (suspend (postsSoFar: Int) -> Unit)? = null
    ): Int = coroutineScope {
        val appContext = context.applicationContext
        val newUriStr = rootUri.toString()
        val creators = LocalFileScanner.scanCreators(appContext, rootUri)
        cachedRootUri = newUriStr
        cachedCreators = creators
        val total = creators.size
        onCreatorProgress?.invoke(0, total)

        val creatorsDone = AtomicInteger(0)
        val totalPosts = AtomicInteger(0)
        val jobs = creators.map { creator ->
            async {
                val key = creator.folderUri.toString()
                val posts = mutableListOf<PostInfo>()
                // Stream posts so the counter updates live per-post
                LocalFileScanner.scanPostsFlow(appContext, creator.folderUri)
                    .collect { post ->
                        posts.add(post)
                        val running = totalPosts.incrementAndGet()
                        onPostProgress?.invoke(running)
                    }
                val sorted = posts.sortedByDescending { it.publishedAt }
                postsCache[key] = sorted
                preloadThumbnails(appContext, sorted)
                onCreatorProgress?.invoke(creatorsDone.incrementAndGet(), total)
                posts.size
            }
        }
        jobs.awaitAll().sum()
    }

    /** Returns cached creators immediately (empty list if not yet loaded). */
    fun getCachedCreators(): List<CreatorInfo> = cachedCreators

    /**
     * Loads creators - returns from cache instantly if available,
     * otherwise does the scan and caches the result.
     */
    suspend fun loadCreators(context: Context, rootUri: Uri): List<CreatorInfo> {
        val uriStr = rootUri.toString()
        if (uriStr == cachedRootUri && cachedCreators.isNotEmpty()) {
            return cachedCreators
        }
        val creators = LocalFileScanner.scanCreators(context, rootUri)
        cachedRootUri = uriStr
        cachedCreators = creators
        return creators
    }

    /**
     * Loads posts - returns from cache instantly if available,
     * otherwise scans and caches.
     */
    suspend fun loadPosts(context: Context, creatorUri: Uri): List<PostInfo> {
        val key = creatorUri.toString()
        postsCache[key]?.let { return it }
        val posts = LocalFileScanner.scanPosts(context, creatorUri)
        postsCache[key] = posts
        return posts
    }

    /**
     * Returns a Flow that streams posts one-by-one using SAF.
     * The first N posts arrive fast while the rest continue scanning.
     * If cached, emits from cache instead.
     */
    fun loadPostsFlow(context: Context, creatorUri: Uri): Flow<PostInfo> {
        val key = creatorUri.toString()
        val cached = postsCache[key]
        return if (cached != null) {
            kotlinx.coroutines.flow.flow {
                cached.forEach { emit(it) }
            }
        } else {
            LocalFileScanner.scanPostsFlow(context, creatorUri)
        }
    }

    /** Pre-warms the post list for a creator (fire-and-forget). */
    fun prefetchPosts(context: Context, creatorUri: Uri) {
        val key = creatorUri.toString()
        if (postsCache.containsKey(key)) return
        val appContext = context.applicationContext
        scope.launch {
            val posts = LocalFileScanner.scanPosts(appContext, creatorUri)
            postsCache[key] = posts
            preloadThumbnails(appContext, posts)
        }
    }

    /** Returns the cached post list for a creator URI key, or null if not cached. */
    fun getCachedPosts(key: String): List<PostInfo>? = postsCache[key]

    /** Stores a fully-scanned post list in the cache. */
    fun cachePosts(key: String, posts: List<PostInfo>) {
        postsCache[key] = posts
    }

    /** Call when the user changes the root folder. */
    fun invalidate() {
        cachedRootUri = null
        cachedCreators = emptyList()
        postsCache.clear()
    }

    // --- Private helpers -----------------------------------------------------

    private fun preloadThumbnails(context: Context, posts: List<PostInfo>) {
        posts.forEach { post ->
            post.thumbnailUri?.let { uri ->
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }
}
