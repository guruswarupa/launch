package com.guruswarupa.launch.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter

class FastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".map { it.toString() }
    private var recyclerView: RecyclerView? = null

    private val density = resources.displayMetrics.density
    private val trackStroke = 2f * density
    private val maxWaveDepth = 80f * density
    private val maxWaveHeight = 180f * density
    private val previewRadius = 74f * density
    private val dotRadius = 3f * density
    private val extraVerticalPadding = 12f * density
    private var currentColor = Color.WHITE

    private var currentAlpha = 0f
    private var waveProgress = 0f
    private var alphaAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null

    private var trackTop = 0f
    private var trackBottom = 0f
    private var trackX = 0f
    private var letterSpacing = 0f
    private var selectedIndex = -1
    private var isSliding = false

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(currentColor, 230)
        strokeWidth = trackStroke
        strokeCap = Paint.Cap.ROUND
    }

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
    }

    private val selectedLetterPaint = Paint(letterPaint).apply {
        textSize = 22f * density
        isFakeBoldText = true
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(currentColor, 80)
        style = Paint.Style.STROKE
        strokeWidth = trackStroke * 5
        maskFilter = BlurMaskFilter(16f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(currentColor, 60)
        style = Paint.Style.FILL
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        alpha = 30
        style = Paint.Style.FILL
    }

    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        textAlign = Paint.Align.CENTER
        textSize = 72f * density
        isFakeBoldText = true
    }

    private val wavePath = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setRecyclerView(rv: RecyclerView) {
        recyclerView = rv
    }

    fun setTextColor(color: Int) {
        currentColor = color
        trackPaint.color = ColorUtils.setAlphaComponent(color, 230)
        letterPaint.color = color
        selectedLetterPaint.color = color
        dotPaint.color = color
        glowPaint.color = ColorUtils.setAlphaComponent(color, 80)
        haloPaint.color = ColorUtils.setAlphaComponent(color, 60)
        previewPaint.color = color
        previewPaint.alpha = 30
        previewTextPaint.color = color
        updateWaveShader()
        invalidate()
    }

    private fun animateAlpha(to: Float) {
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofFloat(currentAlpha, to).apply {
            duration = 220
            addUpdateListener {
                currentAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateWaveProgress(to: Float) {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(waveProgress, to).apply {
            duration = 250
            addUpdateListener {
                waveProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackX = width - paddingEnd - trackStroke
        trackTop = paddingTop + extraVerticalPadding
        trackBottom = height - paddingBottom - extraVerticalPadding
        if (trackBottom <= trackTop) {
            letterSpacing = 0f
            return
        }
        letterSpacing = (trackBottom - trackTop) / (alphabet.size - 1).coerceAtLeast(1)
        updateWaveShader()
    }

    private fun updateWaveShader() {
        val startX = (trackX - maxWaveDepth).coerceAtLeast(0f)
        val startColor = ColorUtils.setAlphaComponent(letterPaint.color, 220)
        val endColor = ColorUtils.setAlphaComponent(letterPaint.color, 0)
        val shader = LinearGradient(
            startX,
            0f,
            trackX,
            0f,
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
        wavePaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackBottom <= trackTop || alphabet.isEmpty()) return

        val fadeAlpha = (currentAlpha * 255).toInt().coerceIn(0, 255)
        glowPaint.alpha = (fadeAlpha * 0.45f).toInt().coerceIn(0, 255)
        canvas.drawLine(trackX, trackTop, trackX, trackBottom, glowPaint)
        trackPaint.alpha = (fadeAlpha * 0.75f).toInt()
        canvas.drawLine(trackX, trackTop, trackX, trackBottom, trackPaint)

        dotPaint.alpha = (fadeAlpha * 0.45f).toInt()
        val baseOffset = (letterPaint.descent() + letterPaint.ascent()) / 2
        for (i in alphabet.indices) {
            val y = trackTop + letterSpacing * i
            canvas.drawCircle(trackX + trackStroke * 4, y, dotRadius, dotPaint)
            if (i == selectedIndex && isSliding) {
                haloPaint.alpha = (fadeAlpha * 0.4f).toInt().coerceIn(0, 255)
                val haloRadius = 26f * density + (waveProgress * 12f * density)
                canvas.drawCircle(trackX - trackStroke * 4, y, haloRadius, haloPaint)
            }
            val paintToUse = if (i == selectedIndex && isSliding) selectedLetterPaint else letterPaint
            val letterAlphaFactor = if (i == selectedIndex && isSliding) 1f else 0.65f
            paintToUse.alpha = (fadeAlpha * letterAlphaFactor).toInt().coerceIn(0, 255)
            val letterY = y - baseOffset
            canvas.drawText(alphabet[i], trackX - trackStroke * 4, letterY, paintToUse)
        }

        drawWave(canvas, fadeAlpha)
        drawPreviewBubble(canvas, fadeAlpha)
    }

    private fun drawWave(canvas: Canvas, fadeAlpha: Int) {
        if (selectedIndex < 0 || waveProgress <= 0f) return
        val centerY = (trackTop + letterSpacing * selectedIndex).coerceIn(trackTop, trackBottom)
        val depth = maxWaveDepth * waveProgress
        val height = maxWaveHeight * waveProgress
        wavePaint.alpha = (fadeAlpha * 0.7f).toInt().coerceIn(0, 255)
        wavePath.reset()
        // Draw the smooth wave that stretches left from the track while dragging.
        wavePath.moveTo(trackX, centerY - height / 2f)
        wavePath.cubicTo(
            trackX - depth,
            centerY - height / 2f,
            trackX - depth,
            centerY + height / 2f,
            trackX,
            centerY + height / 2f
        )
        wavePath.lineTo(trackX, centerY - height / 2f)
        wavePath.close()
        canvas.drawPath(wavePath, wavePaint)
    }

    private fun drawPreviewBubble(canvas: Canvas, fadeAlpha: Int) {
        if (selectedIndex < 0 || waveProgress <= 0f) return
        val centerY = (trackTop + letterSpacing * selectedIndex).coerceIn(trackTop, trackBottom)
        val radius = previewRadius * waveProgress
        val bubbleX = (trackX - maxWaveDepth - radius * 0.25f).coerceAtLeast(radius)
        // Bubble preview follows the selected letter and grows with the drag intensity.
        previewPaint.alpha = (fadeAlpha * 0.4f).toInt().coerceIn(0, 255)
        canvas.drawCircle(bubbleX, centerY, radius, previewPaint)
        previewTextPaint.alpha = fadeAlpha
        val textOffset = (previewTextPaint.descent() + previewTextPaint.ascent()) / 2
        canvas.drawText(alphabet[selectedIndex], bubbleX, centerY - textOffset, previewTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isSliding = true
                animateAlpha(1f)
                animateWaveProgress(1f)
                parent?.requestDisallowInterceptTouchEvent(true)
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSliding) {
                    handleTouch(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isSliding = false
                selectedIndex = -1
                animateAlpha(0f)
                animateWaveProgress(0f)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(y: Float) {
        if (trackBottom <= trackTop) return
        var index = ((y - trackTop) / letterSpacing).toInt().coerceIn(0, alphabet.size - 1)
        if (index != selectedIndex) {
            selectedIndex = index
            scrollToLetter(alphabet[index])
        }
        invalidate()
    }

    private fun scrollToLetter(letter: String) {
        val adapter = recyclerView?.adapter as? AppAdapter ?: return
        val appList = adapter.appList
        var targetPosition = -1
        for (i in appList.indices) {
            val label = adapter.getAppLabel(i)
            if (letter == "#") {
                if (label.isNotEmpty() && !label[0].isLetter()) {
                    targetPosition = i
                    break
                }
            } else if (label.startsWith(letter, ignoreCase = true)) {
                targetPosition = i
                break
            }
        }

        if (targetPosition != -1) {
            val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(targetPosition, 0)

            val parent = recyclerView?.parent?.parent as? NestedScrollView
            if (parent != null) {
                recyclerView?.post {
                    val view = layoutManager?.findViewByPosition(targetPosition)
                    if (view != null) {
                        val top = view.top + (recyclerView?.top ?: 0)
                        parent.smoothScrollTo(0, top)
                    } else {
                        recyclerView?.scrollToPosition(targetPosition)
                        if (targetPosition == 0) {
                            parent.smoothScrollTo(0, recyclerView?.top ?: 0)
                        }
                    }
                }
            }
        }
    }
}
