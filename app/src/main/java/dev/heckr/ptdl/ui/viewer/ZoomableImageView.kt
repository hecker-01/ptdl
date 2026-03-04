package dev.heckr.ptdl.ui.viewer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView with proper pinch-to-zoom, double-tap zoom, and bounded pan.
 * Uses Matrix transforms for smooth, correct behavior.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val imageMatrix2 = Matrix()
    private val savedMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var mode = NONE
    private val startPoint = PointF()
    private val midPoint = PointF()
    private var oldDist = 1f

    private var minScale = 1f
    private var maxScale = 5f
    private var isReady = false
    private var initialSetup = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = currentScale()
                var factor = detector.scaleFactor
                val newScale = scale * factor
                if (newScale > maxScale) factor = maxScale / scale
                if (newScale < minScale) factor = minScale / scale
                imageMatrix2.postScale(factor, factor, detector.focusX, detector.focusY)
                clampTranslation()
                imageMatrix = imageMatrix2
                return true
            }
        })

    private var zoomAnimator: ValueAnimator? = null

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val scale = currentScale()
                val targetMatrix = Matrix()
                if (scale > minScale * 1.1f) {
                    // Animate back to fit
                    val d = drawable ?: return true
                    val dw = d.intrinsicWidth.toFloat()
                    val dh = d.intrinsicHeight.toFloat()
                    val vw = width.toFloat()
                    val vh = height.toFloat()
                    val fitScale = minOf(vw / dw, vh / dh)
                    targetMatrix.postScale(fitScale, fitScale)
                    targetMatrix.postTranslate((vw - dw * fitScale) / 2f, (vh - dh * fitScale) / 2f)
                } else {
                    // Animate zoom to 2.5x at tap point
                    val target = 2.5f
                    val factor = target / scale
                    targetMatrix.set(imageMatrix2)
                    targetMatrix.postScale(factor, factor, e.x, e.y)
                    // Clamp the target
                    clampMatrix(targetMatrix)
                }
                animateMatrix(imageMatrix2, targetMatrix)
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            isReady = true
            if (drawable != null && !initialSetup) setupFitCenter()
        }
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        initialSetup = false
        if (isReady && drawable != null) setupFitCenter()
    }

    private fun setupFitCenter() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = minOf(vw / dw, vh / dh)
        minScale = scale
        imageMatrix2.reset()
        imageMatrix2.postScale(scale, scale)
        imageMatrix2.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
        imageMatrix = imageMatrix2
        initialSetup = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(imageMatrix2)
                startPoint.set(event.x, event.y)
                mode = DRAG
                parent?.requestDisallowInterceptTouchEvent(currentScale() > minScale * 1.05f)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(imageMatrix2)
                    midPoint(midPoint, event)
                    mode = ZOOM
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && !scaleDetector.isInProgress) {
                    if (currentScale() > minScale * 1.05f) {
                        val dx = event.x - startPoint.x
                        val dy = event.y - startPoint.y
                        imageMatrix2.set(savedMatrix)
                        imageMatrix2.postTranslate(dx, dy)
                        clampTranslation()
                        imageMatrix = imageMatrix2
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun currentScale(): Float {
        imageMatrix2.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun clampTranslation() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()

        imageMatrix2.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        var tx = matrixValues[Matrix.MTRANS_X]
        var ty = matrixValues[Matrix.MTRANS_Y]
        val scaledW = dw * scale
        val scaledH = dh * scale

        // If image smaller than view, center it; otherwise clamp to edges
        tx = if (scaledW <= vw) (vw - scaledW) / 2f
             else tx.coerceIn(vw - scaledW, 0f)
        ty = if (scaledH <= vh) (vh - scaledH) / 2f
             else ty.coerceIn(vh - scaledH, 0f)

        matrixValues[Matrix.MTRANS_X] = tx
        matrixValues[Matrix.MTRANS_Y] = ty
        imageMatrix2.setValues(matrixValues)
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        point.set((event.getX(0) + event.getX(1)) / 2f,
                  (event.getY(0) + event.getY(1)) / 2f)
    }

    private fun animateMatrix(from: Matrix, to: Matrix) {
        zoomAnimator?.cancel()
        val startValues = FloatArray(9)
        val endValues = FloatArray(9)
        from.getValues(startValues)
        to.getValues(endValues)

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            val animValues = FloatArray(9)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                for (i in 0..8) {
                    animValues[i] = startValues[i] + (endValues[i] - startValues[i]) * t
                }
                imageMatrix2.setValues(animValues)
                imageMatrix = imageMatrix2
            }
            start()
        }
    }

    private fun clampMatrix(m: Matrix) {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        val vals = FloatArray(9)
        m.getValues(vals)
        val scale = vals[Matrix.MSCALE_X]
        val scaledW = dw * scale
        val scaledH = dh * scale
        vals[Matrix.MTRANS_X] = if (scaledW <= vw) (vw - scaledW) / 2f
            else vals[Matrix.MTRANS_X].coerceIn(vw - scaledW, 0f)
        vals[Matrix.MTRANS_Y] = if (scaledH <= vh) (vh - scaledH) / 2f
            else vals[Matrix.MTRANS_Y].coerceIn(vh - scaledH, 0f)
        m.setValues(vals)
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
