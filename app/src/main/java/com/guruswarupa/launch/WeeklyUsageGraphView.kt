package com.guruswarupa.launch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class WeeklyUsageGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var usageData: List<Pair<String, Long>> = emptyList()
    private var appUsageData: List<Pair<String, Map<String, Long>>> = emptyList()
    
    var onDaySelected: ((String, Map<String, Long>) -> Unit)? = null
    
    private val cardPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#1E1E1E")
    }
    
    private val cardStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#333333")
        strokeWidth = 1.5f
    }

    private val dayTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val timeTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#AAAAAA")
        textSize = 28f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val instructionPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#666666")
        textSize = 14f
        textAlign = Paint.Align.CENTER
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

        // Don't draw background - let widget background show through

        val dataToUse = if (appUsageData.isNotEmpty()) appUsageData else
            usageData.map { it.first to mapOf("Total" to it.second) }

        if (dataToUse.isEmpty()) {
            // Draw "No data" message
            instructionPaint.textSize = 32f
            instructionPaint.color = Color.parseColor("#666666")
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
        val availableHeight = height - topPadding - bottomPadding
        val totalCardHeight = (cardHeight + cardSpacing) * dataToUse.size - cardSpacing
        
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
            
            // Draw subtle shadow
            val shadowPaint = Paint().apply {
                color = Color.parseColor("#15000000")
            }
            canvas.drawRoundRect(
                RectF(cardLeft + 1f, cardTop + 2f, cardRight + 1f, cardBottom + 2f),
                14f, 14f, shadowPaint
            )
            
            // Draw card background
            canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint)
            canvas.drawRoundRect(cardRect, 14f, 14f, cardStrokePaint)
            
            // Draw day name (larger and bolder)
            dayTextPaint.textAlign = Paint.Align.LEFT
            dayTextPaint.textSize = 36f
            val dayY = cardTop + cardHeight / 2f - 15f
            canvas.drawText(day, cardLeft + 24f, dayY, dayTextPaint)
            
            // Draw usage time in hours (larger)
            val usageText = formatUsageTimeInHours(totalUsage)
            timeTextPaint.textAlign = Paint.Align.RIGHT
            timeTextPaint.textSize = 28f
            timeTextPaint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(usageText, cardRight - 24f, dayY + 8f, timeTextPaint)
            
            // Draw subtle divider line
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#2A2A2A")
                strokeWidth = 1f
            }
            val dividerY = cardTop + cardHeight - 1f
            canvas.drawLine(cardLeft + 24f, dividerY, cardRight - 24f, dividerY, dividerPaint)
        }
        
        // Draw instruction text at the bottom of the last card area
        val lastCardBottom = if (dataToUse.isNotEmpty()) {
            val lastCardY = startY + ((dataToUse.size - 1) * (cardHeight + cardSpacing))
            lastCardY + cardHeight + 12f
        } else {
            height - 8f
        }
        
        instructionPaint.textSize = 12f
        instructionPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "Tap a day card to see detailed usage",
            width / 2f,
            lastCardBottom,
            instructionPaint
        )
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val dataToUse = if (appUsageData.isNotEmpty()) appUsageData else
                usageData.map { it.first to mapOf("Total" to it.second) }
            
            if (dataToUse.isEmpty()) return false
            
            val padding = 20f
            val cardSpacing = 12f
            val cardHeight = 100f
            val topPadding = 4f
            val bottomPadding = 8f
            
            val startY = topPadding
            
            val touchX = event.x
            val touchY = event.y
            
            // Check which card was touched
            dataToUse.forEachIndexed { dayIndex, (day, appUsages) ->
                val cardY = startY + (dayIndex * (cardHeight + cardSpacing))
                val cardLeft = padding
                val cardRight = width - padding
                val cardTop = cardY
                val cardBottom = cardY + cardHeight
                
                if (touchX >= cardLeft && touchX <= cardRight &&
                    touchY >= cardTop && touchY <= cardBottom) {
                    // Show dialog for this day
                    onDaySelected?.invoke(day, appUsages)
                    return true
                }
            }
            
            // Clicked outside, do nothing
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
