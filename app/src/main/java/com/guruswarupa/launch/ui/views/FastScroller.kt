package com.guruswarupa.launch.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants

class FastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var alphabet = listOf("★") + ('A'..'Z').map { it.toString() } + "#"
    private var recyclerView: RecyclerView? = null

    private val density = resources.displayMetrics.density
    private val trackStroke = 1f * density
    private val maxWaveDepth = 80f * density
    private val maxWaveHeight = 160f * density
    private val previewRadius = 38f * density
    private val extraVerticalPadding = 48f * density
    private var currentColor = Color.WHITE

    private var currentAlpha = 1f // Always visible
    private var waveProgress = 0f
    private var waveAnimator: ValueAnimator? = null
    
    // Track touch position for wave animation origin
    private var touchX = 0f

    private var trackTop = 0f
    private var trackBottom = 0f
    private var trackX = 0f
    private var letterSpacing = 0f
    private var selectedIndex = -1
    private var scrollIndex = -1
    private var isSliding = false

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(currentColor, 30)
        strokeWidth = trackStroke
        strokeCap = Paint.Cap.ROUND
    }

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
    }

    private val selectedLetterPaint = Paint(letterPaint).apply {
        textSize = 14f * density
        isFakeBoldText = true
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(currentColor, 20)
        style = Paint.Style.STROKE
        strokeWidth = trackStroke * 8
        maskFilter = BlurMaskFilter(15f * density, BlurMaskFilter.Blur.NORMAL)
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
        style = Paint.Style.FILL
    }

    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f * density
        isFakeBoldText = true
    }

    private val wavePath = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        refreshTypography()
    }

    fun setFavoritesVisible(visible: Boolean) {
        val newAlphabet = if (visible) {
            listOf("★") + ('A'..'Z').map { it.toString() } + "#"
        } else {
            ('A'..'Z').map { it.toString() } + "#"
        }
        
        if (alphabet != newAlphabet) {
            alphabet = newAlphabet
            recalculateSpacing()
            invalidate()
        }
    }

    private fun recalculateSpacing() {
        if (trackBottom <= trackTop || alphabet.isEmpty()) {
            letterSpacing = 0f
            return
        }
        letterSpacing = (trackBottom - trackTop) / (alphabet.size - 1).coerceAtLeast(1)
    }

    fun setRecyclerView(rv: RecyclerView) {
        recyclerView = rv
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var previousState = RecyclerView.SCROLL_STATE_IDLE
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isSliding) {
                    updateScrollIndex()
                }
            }
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                
                val adapter = recyclerView.adapter as? AppAdapter
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        // User started scrolling
                        adapter?.setFastScrollingState(true)
                    }
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        // Fast scroll in progress
                        adapter?.setFastScrollingState(true)
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // Scrolling stopped
                        if (previousState != RecyclerView.SCROLL_STATE_IDLE) {
                            adapter?.setFastScrollingState(false)
                            // Force refresh visible icons after a short delay
                            if (!isSliding) {
                                forceRefreshVisibleIcons()
                            }
                        }
                    }
                }
                previousState = newState
            }
        })
        
        // Initial sync if the recycler view is already populated
        rv.post { updateScrollIndex() }
    }
    
    /**
     * Forces a refresh of visible item icons after scrolling stops.
     * This helps ensure icons load properly after fast scrolling with the fast scroller.
     */
    private fun forceRefreshVisibleIcons() {
        val rv = recyclerView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        
        val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePos = layoutManager.findLastVisibleItemPosition()
        
        if (firstVisiblePos == RecyclerView.NO_POSITION || lastVisiblePos == RecyclerView.NO_POSITION) return
        
        val adapter = rv.adapter as? AppAdapter ?: return
        
        // Notify adapter to rebind visible items to refresh icons
        // Use a small delay to ensure layout is stable
        rv.postDelayed({
            for (pos in firstVisiblePos..lastVisiblePos) {
                if (pos < adapter.itemCount) {
                    rv.findViewHolderForAdapterPosition(pos)?.let { viewHolder ->
                        // Force rebind by calling onBindViewHolder directly
                        // This will check cache and reload icons if needed
                        if (viewHolder is AppAdapter.ViewHolder) {
                            adapter.forceRebindViewHolder(viewHolder, pos)
                        }
                    }
                }
            }
        }, 50)
    }

    private fun updateScrollIndex() {
        val rv = recyclerView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePos == RecyclerView.NO_POSITION) return

        val adapter = rv.adapter as? AppAdapter ?: return
        val appList = adapter.appList
        if (firstVisiblePos >= appList.size) return

        var newIndex = -1
        
        // Find both favorites separators
        var favoritesStartSeparatorIndex = -1
        var favoritesEndSeparatorIndex = -1
        for (i in appList.indices) {
            if (appList[i].activityInfo.packageName == AppAdapter.SEPARATOR_PACKAGE) {
                if (appList[i].activityInfo.name == "favorites_separator") {
                    favoritesStartSeparatorIndex = i
                } else if (appList[i].activityInfo.name == "favorites_end_separator") {
                    favoritesEndSeparatorIndex = i
                }
            }
        }

        // Check if we're in the favorites section (between start and end separators)
        val isInFavoritesSection = if (favoritesStartSeparatorIndex != -1 && favoritesEndSeparatorIndex != -1) {
            firstVisiblePos >= favoritesStartSeparatorIndex && firstVisiblePos < favoritesEndSeparatorIndex
        } else if (favoritesStartSeparatorIndex != -1) {
            // Only start separator exists (no end separator yet)
            firstVisiblePos >= favoritesStartSeparatorIndex
        } else {
            false
        }

        if (isInFavoritesSection) {
            // In favorites section - always show star
            newIndex = alphabet.indexOf("★")
        } else {
            // Outside favorites section - find the correct letter
            // Scan backwards from firstVisiblePos to find the nearest letter separator
            for (i in firstVisiblePos downTo 0) {
                val currentApp = appList[i]
                if (currentApp.activityInfo.packageName == AppAdapter.SEPARATOR_PACKAGE) {
                    val separatorId = currentApp.activityInfo.name ?: ""
                    if (separatorId.startsWith("letter_separator_")) {
                        val letter = separatorId.removePrefix("letter_separator_")
                        newIndex = alphabet.indexOf(letter)
                        break
                    }
                }
            }
            
            // If no letter separator found, use the first visible app's label
            if (newIndex == -1) {
                val label = adapter.getAppLabel(firstVisiblePos)
                if (label.isNotEmpty()) {
                    val firstChar = label[0].uppercaseChar()
                    newIndex = if (firstChar.isLetter()) {
                        alphabet.indexOf(firstChar.toString())
                    } else {
                        alphabet.indexOf("#")
                    }
                }
            }
        }

        if (newIndex != -1 && newIndex != scrollIndex) {
            scrollIndex = newIndex
            invalidate()
        }
    }

    fun setTextColor(color: Int) {
        currentColor = color
        trackPaint.color = ColorUtils.setAlphaComponent(color, 20)
        letterPaint.color = color
        selectedLetterPaint.color = color
        glowPaint.color = ColorUtils.setAlphaComponent(color, 10)
        haloPaint.color = ColorUtils.setAlphaComponent(color, 40)
        previewPaint.color = color
        previewTextPaint.color = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        updateWaveShader()
        invalidate()
    }

    fun refreshTypography(preferences: SharedPreferences = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)) {
        val fontStyle = preferences.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val intensity = preferences.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
        applyTypeface(fontStyle, intensity)
        TypographyManager.getConfiguredFontColor(context)?.let { setTextColor(it) }
        invalidate()
    }

    private fun applyTypeface(fontStyle: String, intensity: String) {
        val family = resolveTypographyFamily(fontStyle, intensity)
        val style = if (intensity == "bold") Typeface.BOLD else Typeface.NORMAL
        val typeface = Typeface.create(family, style)
        letterPaint.typeface = typeface
        selectedLetterPaint.typeface = typeface
        previewTextPaint.typeface = typeface
        invalidate()
    }

    private fun resolveTypographyFamily(style: String, intensity: String): String {
        return when (style) {
            "serif" -> "serif"
            "monospace" -> "monospace"
            "condensed" -> "sans-serif-condensed"
            "rounded" -> "sans-serif-rounded"
            "condensed_light" -> "sans-serif-condensed-light"
            "condensed_medium" -> "sans-serif-condensed-medium"
            "serif_monospace" -> "serif-monospace"
            "display" -> "sans-serif"
            "thin" -> "sans-serif-thin"
            "medium" -> "sans-serif-medium"
            "black" -> "sans-serif-black"
            "smallcaps" -> "sans-serif-smallcaps"
            "casual" -> "casual"
            "cursive" -> "cursive"
            else -> if (intensity == "light") "sans-serif-light" else "sans-serif"
        }
    }

    private fun animateWaveProgress(to: Float) {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(waveProgress, to).apply {
            duration = 350
            interpolator = OvershootInterpolator(1.8f)
            addUpdateListener { animator ->
                waveProgress = animator.animatedValue as Float
                // Animate alpha based on wave progress for smooth fade in/out
                currentAlpha = if (to > 0f) {
                    // Fading in
                    (waveProgress * 1.2f).coerceIn(0f, 1f)
                } else {
                    // Fading out
                    ((1f - waveProgress) * 1.2f).coerceIn(0f, 1f)
                }
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackX = width - paddingEnd - 2f * density
        // Initialize touchX to trackX so wave fades from right when not touching
        touchX = trackX
        trackTop = paddingTop + extraVerticalPadding
        trackBottom = height - paddingBottom - extraVerticalPadding
        recalculateSpacing()
        updateWaveShader()
    }

    private fun updateWaveShader() {
        // Wave now animates from touch position towards the track
        val startX = (touchX - maxWaveDepth).coerceAtLeast(0f)
        // Darker gradient for better visibility on dark backgrounds
        val startColor = ColorUtils.setAlphaComponent(currentColor, 120)
        val endColor = ColorUtils.setAlphaComponent(currentColor, 0)
        val shader = LinearGradient(
            startX, 0f, touchX, 0f,
            startColor, endColor, Shader.TileMode.CLAMP
        )
        wavePaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackBottom <= trackTop || alphabet.isEmpty()) return

        val fadeAlpha = (currentAlpha * 255).toInt().coerceIn(0, 255)
        
        // Very subtle track line
        canvas.drawLine(trackX, trackTop, trackX, trackBottom, trackPaint)

        val baseOffset = (letterPaint.descent() + letterPaint.ascent()) / 2
        val textX = trackX - 16f * density

        for (i in alphabet.indices) {
            val y = trackTop + letterSpacing * i
            val isSelected = i == selectedIndex && isSliding
            val isCurrentScroll = !isSliding && i == scrollIndex

            val paintToUse = if (isSelected || i == scrollIndex) selectedLetterPaint else letterPaint
            
            // Improved alpha values for better dark theme visibility
            val alpha = if (isSelected || i == scrollIndex) 255 else (fadeAlpha * 0.5f).toInt()
            paintToUse.alpha = alpha
            
            if (alphabet[i] == "★") {
                paintToUse.textSize = (if (isSelected || i == scrollIndex) 18f else 12f) * density
            } else {
                paintToUse.textSize = (if (isSelected || i == scrollIndex) 14f else 9f) * density
            }
            
            if (isSelected) {
                // Enhanced halo effect for better visibility on dark backgrounds
                haloPaint.alpha = (fadeAlpha * 0.4f).toInt()
                canvas.drawCircle(textX, y, 18f * density * waveProgress, haloPaint)
                
                // Add subtle glow around selected letter
                glowPaint.alpha = (fadeAlpha * 0.3f).toInt()
                canvas.drawCircle(textX, y, 22f * density * waveProgress, glowPaint)
            }
            
            val letterY = y - baseOffset
            canvas.drawText(alphabet[i], textX, letterY, paintToUse)
        }

        drawWave(canvas, fadeAlpha)
        drawPreviewBubble(canvas, fadeAlpha)
    }

    private fun drawWave(canvas: Canvas, fadeAlpha: Int) {
        if (selectedIndex < 0 || waveProgress <= 0f) return
        val centerY = (trackTop + letterSpacing * selectedIndex).coerceIn(trackTop, trackBottom)
        val depth = maxWaveDepth * waveProgress
        val height = maxWaveHeight * waveProgress
        wavePaint.alpha = (fadeAlpha * 0.25f).toInt()
        wavePath.reset()
        wavePath.moveTo(trackX, centerY - height / 2f)
        wavePath.cubicTo(
            trackX - depth, centerY - height / 2f,
            trackX - depth, centerY + height / 2f,
            trackX, centerY + height / 2f
        )
        wavePath.lineTo(trackX, centerY - height / 2f)
        wavePath.close()
        canvas.drawPath(wavePath, wavePaint)
    }

    private fun drawPreviewBubble(canvas: Canvas, fadeAlpha: Int) {
        if (selectedIndex < 0 || waveProgress <= 0f) return
        val centerY = (trackTop + letterSpacing * selectedIndex).coerceIn(trackTop, trackBottom)
        val radius = previewRadius * waveProgress
        // Adjusted bubble position for wider FastScroller - moves more left when expanding
        val bubbleX = trackX - 140f * density - (120f * density * (1f - waveProgress))
        
        // Enhanced soft shadow for better depth on dark backgrounds
        val shadowPaint = Paint(previewPaint).apply {
            maskFilter = BlurMaskFilter(20f * density, BlurMaskFilter.Blur.NORMAL)
            alpha = (fadeAlpha * 0.3f).toInt()
        }
        canvas.drawCircle(bubbleX, centerY, radius + 6f * density, shadowPaint)
        
        // Add a subtle inner glow/halo effect
        val haloPaintLocal = Paint(previewPaint).apply {
            maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
            alpha = (fadeAlpha * 0.4f).toInt()
        }
        canvas.drawCircle(bubbleX, centerY, radius + 2f * density, haloPaintLocal)
        
        previewPaint.alpha = 255
        canvas.drawCircle(bubbleX, centerY, radius, previewPaint)
        
        previewTextPaint.alpha = 255
        val textOffset = (previewTextPaint.descent() + previewTextPaint.ascent()) / 2
        canvas.drawText(alphabet[selectedIndex], bubbleX, centerY - textOffset, previewTextPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchThreshold = width - 64f * density
                if (x < touchThreshold) return false
                
                // Store exact touch X position for wave animation origin
                touchX = x
                
                isSliding = true
                animateWaveProgress(1f)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                parent?.requestDisallowInterceptTouchEvent(true)
                handleTouch(y)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSliding) {
                    handleTouch(y)
                    return true
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isSliding = false
                selectedIndex = -1
                // Reset touchX to trackX for smooth fade out
                touchX = trackX
                animateWaveProgress(0f)
                updateScrollIndex() // Sync back to scroll position after touch
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(y: Float) {
        if (trackBottom <= trackTop) return
        val index = ((y - trackTop) / letterSpacing).toInt().coerceIn(0, alphabet.size - 1)
        if (index != selectedIndex) {
            selectedIndex = index
            scrollToLetter(alphabet[index])
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
    }

    private fun scrollToLetter(letter: String) {
        val adapter = recyclerView?.adapter as? AppAdapter ?: return
        val appList = adapter.appList
        var targetPosition = -1
        
        if (letter == "★") {
            targetPosition = 0
        } else {
            // Find the start of the alphabetical list section (after favorites end)
            // Search for favorites_end_separator to skip ALL favorites
            var searchStartIndex = 0
            for (i in appList.indices) {
                if (appList[i].activityInfo.packageName == AppAdapter.SEPARATOR_PACKAGE && 
                    appList[i].activityInfo.name == "favorites_end_separator") {
                    searchStartIndex = i + 1
                    break
                }
            }
            
            // If favorites_end_separator not found, try favorites_separator
            if (searchStartIndex == 0) {
                for (i in appList.indices) {
                    if (appList[i].activityInfo.packageName == AppAdapter.SEPARATOR_PACKAGE && 
                        appList[i].activityInfo.name == "favorites_separator") {
                        searchStartIndex = i + 1
                        break
                    }
                }
            }
            
            for (i in searchStartIndex until appList.size) {
                val app = appList[i]
                val packageName = app.activityInfo.packageName
                
                if (packageName == AppAdapter.SEPARATOR_PACKAGE) {
                    val separatorId = app.activityInfo.name ?: ""
                    if (letter == "#") {
                        if (separatorId == "letter_separator_#") {
                            targetPosition = i
                            break
                        }
                    } else if (separatorId == "letter_separator_$letter") {
                        targetPosition = i
                        break
                    }
                } else {
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
