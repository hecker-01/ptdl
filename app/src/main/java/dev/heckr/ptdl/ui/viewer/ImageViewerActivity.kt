package dev.heckr.ptdl.ui.viewer

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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

        // Immersive full-screen
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

/**
 * Simple ViewPager2 adapter that shows one zoomable image per page.
 */
private class ImagePagerAdapter(private val uris: List<Uri>) :
    RecyclerView.Adapter<ImagePagerAdapter.VH>() {

    override fun getItemCount() = uris.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_image, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(uris[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.zoomable_image)

        private var scaleFactor = 1f
        private var translateX = 0f
        private var translateY = 0f
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var activePointerId = MotionEvent.INVALID_POINTER_ID

        private val scaleDetector = ScaleGestureDetector(
            view.context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor *= detector.scaleFactor
                    scaleFactor = scaleFactor.coerceIn(1f, 5f)
                    applyTransform()
                    return true
                }
            }
        )

        init {
            imageView.setOnTouchListener { v, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePointerId = event.getPointerId(0)
                        lastTouchX = event.x
                        lastTouchY = event.y
                        // Disable ViewPager swipe while zoomed
                        v.parent?.requestDisallowInterceptTouchEvent(scaleFactor > 1.05f)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (scaleFactor > 1.05f) {
                            val idx = event.findPointerIndex(activePointerId)
                            if (idx >= 0) {
                                translateX += event.getX(idx) - lastTouchX
                                translateY += event.getY(idx) - lastTouchY
                                lastTouchX = event.getX(idx)
                                lastTouchY = event.getY(idx)
                                applyTransform()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val pointerIndex = event.actionIndex
                        val pointerId = event.getPointerId(pointerIndex)
                        if (pointerId == activePointerId) {
                            val newIdx = if (pointerIndex == 0) 1 else 0
                            lastTouchX = event.getX(newIdx)
                            lastTouchY = event.getY(newIdx)
                            activePointerId = event.getPointerId(newIdx)
                        }
                    }
                }
                true
            }

            // Double-tap to reset
            imageView.setOnClickListener {
                if (scaleFactor > 1.05f) {
                    scaleFactor = 1f
                    translateX = 0f
                    translateY = 0f
                    applyTransform()
                }
            }
        }

        fun bind(uri: Uri) {
            scaleFactor = 1f
            translateX = 0f
            translateY = 0f
            applyTransform()
            imageView.load(uri) { crossfade(true) }
        }

        private fun applyTransform() {
            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            imageView.translationX = translateX
            imageView.translationY = translateY
        }
    }
}
