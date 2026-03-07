package dev.heckr.ptdl.ui.viewer

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import dev.heckr.ptdl.R

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var pagerAdapter: ImagePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val uriStrings = intent.getStringArrayExtra("imageUris") ?: return finish()
        val startIndex = intent.getIntExtra("startIndex", 0)
        val uris = uriStrings.map { Uri.parse(it) }

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val indicator = findViewById<android.widget.TextView>(R.id.page_indicator)
        val closeBtn = findViewById<ImageView>(R.id.btn_close)

        // Only keep current page in memory - load/unload on demand
        viewPager.offscreenPageLimit = 1
        pagerAdapter = ImagePagerAdapter(uris)
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(startIndex, false)

        if (uris.size > 1) {
            indicator.text = "${startIndex + 1} / ${uris.size}"
            indicator.isVisible = true
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    indicator.text = "${position + 1} / ${uris.size}"
                }
            })
        } else {
            indicator.isVisible = false
        }

        closeBtn.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::pagerAdapter.isInitialized) {
            pagerAdapter.releasePlayers()
        }
    }
}

private class ImagePagerAdapter(private val uris: List<Uri>) :
    RecyclerView.Adapter<ImagePagerAdapter.VH>() {

    private val activePlayers = mutableSetOf<ExoPlayer>()

    override fun getItemCount() = uris.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_image, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(uris[position])

    override fun onViewRecycled(holder: VH) {
        holder.recycle()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ZoomableImageView = view.findViewById(R.id.zoomable_image)
        private val playerView: PlayerView = view.findViewById(R.id.player_view)
        private var player: ExoPlayer? = null

        fun bind(uri: Uri) {
            val path = uri.toString().lowercase()
            val isVideo = listOf("mp4", "webm", "mkv", "mov", "3gp").any { ext ->
                path.endsWith(".$ext") || path.contains(".$ext?")
            }

            if (isVideo) {
                imageView.isVisible = false
                playerView.isVisible = true

                // Release existing player if any
                player?.let {
                    playerView.player = null
                    it.release()
                    activePlayers.remove(it)
                    player = null
                }

                val context = itemView.context
                val exo = ExoPlayer.Builder(context).build()
                player = exo
                activePlayers.add(exo)
                playerView.player = exo
                val mediaItem = MediaItem.fromUri(uri)
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = false
                playerView.useController = true
            } else {
                // Show image
                playerView.isVisible = false
                imageView.isVisible = true

                // Ensure any video player is released
                player?.let {
                    playerView.player = null
                    it.release()
                    activePlayers.remove(it)
                    player = null
                }

                imageView.load(uri) { crossfade(true) }
            }

        }

        fun recycle() {
            imageView.setImageDrawable(null)
            player?.let {
                playerView.player = null
                it.release()
                activePlayers.remove(it)
                player = null
            }
            playerView.isVisible = false
        }
    }

    fun releasePlayers() {
        for (p in activePlayers) {
            try {
                p.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        activePlayers.clear()
    }
}
