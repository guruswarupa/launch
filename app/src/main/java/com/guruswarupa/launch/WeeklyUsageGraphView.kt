package com.guruswarupa.launch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class WeeklyUsageGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var usageData: List<Pair<String, Long>> = emptyList()
    private var appUsageData: List<Pair<String, Map<String, Long>>> = emptyList()
    
    var onDaySelected: ((String, Map<String, Long>) -> Unit)? = null
    
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var touchedCardIndex: Int = -1
    private val TAP_THRESHOLD = 50f // Maximum distance for a tap (in pixels)
    
    private val cardPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val cardStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val dayTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val timeTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 28f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val instructionPaint = Paint().apply {
        isAntiAlias = true
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }

    private val dividerPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 1f
    }

    init {
        updatePaints()
    }

    private fun updatePaints() {
        val widgetText = ContextCompat.getColor(context, R.color.widget_text)
        val widgetTextSecondary = ContextCompat.getColor(context, R.color.widget_text_secondary)
        val itemBackground = ContextCompat.getColor(context, R.color.widget_item_background)
        val itemStroke = ContextCompat.getColor(context, R.color.widget_item_stroke)
        val divider = ContextCompat.getColor(context, R.color.widget_divider)

        cardPaint.color = itemBackground
        cardStrokePaint.color = itemStroke
        dayTextPaint.color = widgetText
        timeTextPaint.color = widgetTextSecondary
        instructionPaint.color = widgetTextSecondary
        dividerPaint.color = divider
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        updatePaints()
        invalidate()
    }

    fun setUsageData(data: List<Pair<String, Long>>) {
        usageData = data
        requestLayout()
        invalidate()
    }

    fun setAppUsageData(data: List<Pair<String, Map<String, Long>>>) {
        appUsageData = data
        requestLayout()
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dataToUse = if (appUsageData.isNotEmpty()) appUsageData else
            usageData.map { it.first to mapOf("Total" to it.second) }
        
        if (dataToUse.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        
        val cardHeight = 100f
        val cardSpacing = 12f
        val topPadding = 4f
        val bottomPadding = 8f
        val instructionHeight = 20f
        
        val totalHeight = topPadding + 
            (cardHeight + cardSpacing) * dataToUse.size - cardSpacing + 
            bottomPadding + instructionHeight
        
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = totalHeight.toInt()
        
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Ensure paints are up to date
        updatePaints()

        val dataToUse = if (appUsageData.isNotEmpty()) appUsageData else
            usageData.map { it.first to mapOf("Total" to it.second) }

        if (dataToUse.isEmpty()) {
            // Draw "No data" message
            instructionPaint.textSize = 32f
            canvas.drawText(
                "No usage data available",
                width / 2f,
                height / 2f,
                instructionPaint
            )
            return
        }

        val padding = 20f
        val cardSpacing = 12f
        val cardHeight = 100f
        val topPadding = 4f
        val bottomPadding = 8f
        
        // Start drawing from top with minimal spacing
        val startY = topPadding
        
        // Draw each day card vertically
        dataToUse.forEachIndexed { dayIndex, (day, appUsages) ->
            val cardY = startY + (dayIndex * (cardHeight + cardSpacing))
            val cardLeft = padding
            val cardRight = width - padding
            val cardTop = cardY
            val cardBottom = cardY + cardHeight
            
            // Calculate total usage for this day
            val totalUsage = appUsages.values.sum()
            
            // Draw card background
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
            
            // Draw subtle shadow (only in dark mode or if appropriate)
            val isNightMode = (resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            if (isNightMode) {
                val shadowPaint = Paint().apply {
                    color = Color.parseColor("#15000000")
                }
                canvas.drawRoundRect(
                    RectF(cardLeft + 1f, cardTop + 2f, cardRight + 1f, cardBottom + 2f),
                    14f, 14f, shadowPaint
                )
            }
            
            // Draw card background
            canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint)
            canvas.drawRoundRect(cardRect, 14f, 14f, cardStrokePaint)
            
            // Draw day name
            dayTextPaint.textAlign = Paint.Align.LEFT
            dayTextPaint.textSize = 36f
            val dayY = cardTop + cardHeight / 2f - 15f
            canvas.drawText(day, cardLeft + 24f, dayY, dayTextPaint)
            
            // Draw usage time in hours
            val usageText = formatUsageTimeInHours(totalUsage)
            timeTextPaint.textAlign = Paint.Align.RIGHT
            timeTextPaint.textSize = 28f
            canvas.drawText(usageText, cardRight - 24f, dayY + 8f, timeTextPaint)
            
            // Draw subtle divider line
            val dividerY = cardTop + cardHeight - 1f
            canvas.drawLine(cardLeft + 24f, dividerY, cardRight - 24f, dividerY, dividerPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dataToUse = if (appUsageData.isNotEmpty()) appUsageData else
            usageData.map { it.first to mapOf("Total" to it.second) }
        
        if (dataToUse.isEmpty()) return super.onTouchEvent(event)
        
        val padding = 20f
        val cardSpacing = 12f
        val cardHeight = 100f
        val topPadding = 4f
        val startY = topPadding
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchedCardIndex = -1
                
                // Check which card was touched
                dataToUse.forEachIndexed { dayIndex, (day, appUsages) ->
                    val cardY = startY + (dayIndex * (cardHeight + cardSpacing))
                    val cardLeft = padding
                    val cardRight = width - padding
                    val cardTop = cardY
                    val cardBottom = cardY + cardHeight
                    
                    if (touchDownX >= cardLeft && touchDownX <= cardRight &&
                        touchDownY >= cardTop && touchDownY <= cardBottom) {
                        touchedCardIndex = dayIndex
                        return true
                    }
                }
                return false
            }
            
            MotionEvent.ACTION_UP -> {
                // Only trigger if it was a tap (not a swipe)
                if (touchedCardIndex >= 0) {
                    val deltaX = kotlin.math.abs(event.x - touchDownX)
                    val deltaY = kotlin.math.abs(event.y - touchDownY)
                    val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                    
                    // If the touch moved less than the threshold, it's a tap
                    if (distance < TAP_THRESHOLD) {
                        val (day, appUsages) = dataToUse[touchedCardIndex]
                        onDaySelected?.invoke(day, appUsages)
                        return true
                    }
                }
                touchedCardIndex = -1
                return false
            }
            
            MotionEvent.ACTION_CANCEL -> {
                touchedCardIndex = -1
                return false
            }
        }
        
        return super.onTouchEvent(event)
    }

    private fun formatUsageTimeInHours(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0h"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
