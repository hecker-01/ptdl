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
import dev.heckr.ptdl.R

class ImageViewerActivity : AppCompatActivity() {

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

        // Only keep current page in memory — load/unload on demand
        viewPager.offscreenPageLimit = 1
        viewPager.adapter = ImagePagerAdapter(uris)
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
}

private class ImagePagerAdapter(private val uris: List<Uri>) :
    RecyclerView.Adapter<ImagePagerAdapter.VH>() {

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

        fun bind(uri: Uri) {
            imageView.load(uri) { crossfade(true) }
        }

        fun recycle() {
            imageView.setImageDrawable(null)
        }
    }
}
