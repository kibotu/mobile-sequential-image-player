package com.exozet.sequentialimage.player

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SeekBar
import androidx.annotation.IntRange
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.sequentialimageplayer_view.view.*
import java.io.IOException
import java.util.*
import kotlin.math.roundToInt


class SequentialImagePlayer @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val TAG by lazy { "${this::class.java.simpleName}:$uuid" }

    private val uuid: String by lazy { UUID.randomUUID().toString().take(8) }

    var debug = true

    private fun log(message: String) {
        if (debug)
            android.util.Log.d(TAG, message)
    }

    private var imageSwapper: ImageSwapper? = null

    var imageUris: Array<Uri> = arrayOf()
        set(value) {
            if (field contentEquals value)
                return
            field = value
        }

    var swipeSpeed: Float = 1f
        set(value) {
            field = value / 10f
        }

    var autoPlay: Boolean = false
        get() = autoplaySwitch.isChecked
        set(value) {
            field = value
            autoplaySwitch.isChecked = value
        }

    internal var max: Int = 0
        get() = imageUris.size - 1

    var showControls: Boolean = false
        set(value) {
            field = value
            seekBar.goneUnless(!value)
            playDirectionSwitch.goneUnless(!value)
            autoplaySwitch.goneUnless(!value)
            fpsSpinner.goneUnless(!value)
        }

    @IntRange(from = 1, to = 60)
    var fps: Int = 30
        set(value) {
            field = value
            with(fpsSpinner) {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, (1 until 61).map { "$it" }.toList())
                setSelection(value - 1)
            }
        }

    var zoomable: Boolean = true
        set(value) {
            field = value
            viewHolder.isZoomable = value
        }

    var translatable: Boolean = true
        set(value) {
            field = value
            viewHolder.isTranslatable = value
        }

    var playBackwards: Boolean = true
        set(value) {
            field = value
            playDirectionSwitch.isChecked = value
        }

    var blurLetterbox: Boolean = true

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.sequentialimageplayer_view, this, true)
    }

    private fun onCreate() {
        imageSwapper = ImageSwapper(this)

        initSeekBar()

        autoplaySwitch.setOnCheckedChangeListener { _, isChecked -> if (isChecked) startAutoPlay() else stopAutoPlay() }

        initFpsSpinner()

        addSwipeGesture()

        loadImage(imageUris.firstOrNull())

        cancelBusy()

    }

    private fun initFpsSpinner() {
        fpsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                fps = p2
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        busy()
        onCreate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onDestroy()
        cancelBusy()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) onResume() else onPause()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) onResume() else onPause()
    }

    private fun addSwipeGesture() {

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            private val SWIPE_MAX_OF_PATH_X = 100
            private val SWIPE_MAX_OF_PATH_Y = 100

            /** Rotation threshold for scroll (X axis direction)  */
            val THRESHOLD_SCROLL_X = 0.02
            /** Rotation threshold for scroll (Y axis direction)  */
            val THRESHOLD_SCROLL_Y = 0.02

            /** Rotation amount derivative parameter for scroll (X axis direction)  */
            val ON_SCROLL_DIVIDER_X = 400.0f
            /** Rotation amount derivative parameter for scroll (Y axis direction)  */
            val ON_SCROLL_DIVIDER_Y = 400.0f

            override fun onDown(e: MotionEvent?): Boolean = true

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean =
                    if (Math.abs(distanceX) > SWIPE_MAX_OF_PATH_X || Math.abs(distanceY) > SWIPE_MAX_OF_PATH_Y) {
                        false
                    } else {
                        var diffX = distanceX / ON_SCROLL_DIVIDER_X
                        var diffY = distanceY / ON_SCROLL_DIVIDER_Y

                        if (Math.abs(diffX) < THRESHOLD_SCROLL_X) {
                            diffX = 0.0f
                        }
                        if (Math.abs(diffY) < THRESHOLD_SCROLL_Y) {
                            diffY = 0.0f
                        }

                        onScroll(distanceX, -distanceY)

                        true
                    }

            private fun onScroll(dX: Float, dY: Float) {
                onHorizontalScroll(dX)
                onVerticalScroll(dY)
            }

            private fun onVerticalScroll(deltaY: Float) {
                // Log("onVerticalScroll deltaY=$deltaY")
            }

            private fun onHorizontalScroll(deltaX: Float) {
                val delta = deltaX * swipeSpeed
                // Log("onHorizontalScroll deltaX=$deltaX / speed=$swipeSpeed -> delta=$delta")
                imageSwapper?.swapImage((imageSwapper?.index ?: 0) + delta.roundToInt())
            }
        })

        viewHolder.setOnTouchListener { _, motionEvent ->

            viewHolder.isZoomable = zoomable
            viewHolder.isTranslatable = zoomable

            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    if (autoPlay) startAutoPlay()
                    super.onTouchEvent(motionEvent)
                }
                MotionEvent.ACTION_DOWN -> {
                    if (autoPlay) stopAutoPlay()
                    if (motionEvent.pointerCount <= 1) {
                        viewHolder.isZoomable = false
                        viewHolder.isTranslatable = false
                        gestureDetector.onTouchEvent(motionEvent)
                    } else
                        super.onTouchEvent(motionEvent)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (motionEvent.pointerCount <= 1) {
                        viewHolder.isZoomable = false
                        viewHolder.isTranslatable = false
                        gestureDetector.onTouchEvent(motionEvent)
                    } else
                        super.onTouchEvent(motionEvent)
                }
                else -> {
                    super.onTouchEvent(motionEvent)
                }
            }
        }
    }

    private fun initSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                imageSwapper?.swapImage(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (autoPlay) stopAutoPlay()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (autoPlay) startAutoPlay()
            }
        })
    }

    private fun onResume() {
        if (autoPlay) startAutoPlay()
    }


    private fun onPause() {
        if (autoPlay) stopAutoPlay()
    }

    private fun onDestroy() {
        stopAutoPlay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewHolder.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun startAutoPlay() {
        viewHolder.post(imageSwapper)
    }

    private fun stopAutoPlay() {
        viewHolder.removeCallbacks(imageSwapper)
    }

    internal fun loadImage(uri: Uri?) {
        if (uri == null)
            return

        val bitmap = loadBitmap(uri)
        viewHolder.setImageBitmap(bitmap)

        if (resources.configuration.orientation == ORIENTATION_PORTRAIT)
            if (bitmap?.width ?: 0 >= bitmap?.height ?: 0)
                viewHolderBackground.blur(bitmap)


        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE)
            if (bitmap?.height ?: 0 >= bitmap?.width ?: 0)
                viewHolderBackground.blur(bitmap)
    }

    private fun loadBitmap(uri: Uri?): Bitmap? {

        var bitmap: Bitmap? = null
        try {

            val istr = if (uri.toString().startsWith("file:///android_asset/"))
                context.assets.open(uri.toString().removePrefix("file:///android_asset/"))
            else
                context.contentResolver.openInputStream(uri)

            bitmap = BitmapFactory.decodeStream(istr)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bitmap
    }

    internal fun cancelBusy() {
        progress.visibility = View.GONE
    }

    internal fun busy() {
        progress.visibility = View.VISIBLE
    }

    companion object {

        internal const val FPS = "FPS"
        internal const val ZOOMABLE = "ZOOMABLE"
        internal const val TRANSLATABLE = "TRANSLATABLE"
        internal const val PLAY_BACKWARDS = "PLAY_BACKWARDS"
        internal const val AUTO_PLAY = "AUTO_PLAY"
        internal const val SHOW_CONTROLS = "SHOW_CONTROLS"
        internal const val SWIPE_SPEED = "SWIPE_SPEED"
        internal const val BLUR_LETTERBOX = "BLUR_LETTERBOX"

        internal fun loopRange(value: Int, min: Int = 0, max: Int): Int = when {
            value > max -> min
            value < min -> max
            else -> value
        }

        internal fun View.goneUnless(isGone: Boolean = true) {
            visibility = if (isGone) View.GONE else View.VISIBLE
        }
    }

    private val paint = Paint().apply { flags = Paint.FILTER_BITMAP_FLAG }

    private var blurryBitmap: Bitmap? = null

    private fun AppCompatImageView.blur(bitmap: Bitmap?, radius: Int = 10, scaleFactor: Float = 8f) {
        if (!blurLetterbox)
            return

        if (bitmap == null || measuredWidth <= 0 || measuredHeight <= 0)
            return

        val startMs = System.currentTimeMillis()

        if (blurryBitmap == null)
            blurryBitmap = Bitmap.createBitmap((measuredWidth / scaleFactor).toInt(), (measuredHeight / scaleFactor).toInt(), Bitmap.Config.RGB_565)

        val canvas = Canvas(blurryBitmap)
        canvas.translate(-left.toFloat() + -measuredWidth / 2f, -top.toFloat() / 2f)
//        canvas.scale(1 / scaleFactor, 1 / scaleFactor)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        blurryBitmap = FastBlur.doBlur(blurryBitmap, radius, true)

        setImageBitmap(blurryBitmap)

        log("view=[$measuredWidth:$measuredHeight]: bitmap=[${bitmap.width}:${bitmap.height}] overlay=[${blurryBitmap?.width}:${blurryBitmap?.height}] in ${System.currentTimeMillis() - startMs} ms")
    }
}