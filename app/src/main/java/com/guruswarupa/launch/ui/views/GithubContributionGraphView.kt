package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.getColorOrThrow
import java.util.*

class GithubContributionGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var contributions: Map<String, Int> = emptyMap()
    private var cellSize = 0f
    private var cellSpacing = 0f
    private var cornerRadius = 0f
    
    // Predefined colors matching GitHub's contribution graph
    private val colorLevels = listOf(
        android.graphics.Color.parseColor("#EBEDF0"), // No contribution
        android.graphics.Color.parseColor("#9BE9A8"), // Level 1
        android.graphics.Color.parseColor("#40C463"), // Level 2
        android.graphics.Color.parseColor("#30A14E"), // Level 3
        android.graphics.Color.parseColor("#216E39")  // Level 4 (highest)
    )

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 1f
        borderPaint.color = android.graphics.Color.parseColor("#D1D5DA")
        cellSize = resources.getDimension(com.guruswarupa.launch.R.dimen.contribution_cell_size)
        cellSpacing = resources.getDimension(com.guruswarupa.launch.R.dimen.contribution_cell_spacing)
        cornerRadius = resources.getDimension(com.guruswarupa.launch.R.dimen.contribution_cell_radius)
    }

    fun setContributions(contributions: Map<String, Int>) {
        this.contributions = contributions
        // Sort contributions by date to ensure proper display
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (contributions.isEmpty()) {
            drawPlaceholder(canvas)
            return
        }

        // Calculate grid dimensions based on available space
        val maxWeeks = calculateMaxWeeks()
        val cellWidth = (width - paddingRight - paddingLeft - (maxWeeks - 1) * cellSpacing) / maxWeeks
        val cellHeight = (height - paddingTop - paddingBottom - 6 * cellSpacing) / 7 // 7 days a week
        
        // Adjust cell size to fit the available space
        val adjustedCellSize = minOf(cellWidth, cellHeight)
        
        // Draw the contribution grid
        var currentX = paddingLeft.toFloat()
        var currentY = paddingTop.toFloat()
        
        // Find the maximum contribution count to normalize colors
        val maxCount = if (contributions.values.maxOrNull() ?: 0 > 0) contributions.values.maxOrNull() ?: 0 else 1
        
        // Iterate through the contribution data in proper chronological order
        val sortedDates = contributions.keys.sorted()
        var dateIndex = 0
        
        for (weekIndex in 0 until maxWeeks) {
            currentY = paddingTop.toFloat()
            
            for (dayIndex in 0 until 7) { // 7 days in a week (Sunday to Saturday)
                if (dateIndex < sortedDates.size) {
                    val date = sortedDates[dateIndex]
                    val count = contributions[date] ?: 0
                    
                    // Calculate color based on contribution level using GitHub's actual algorithm
                    val colorLevel = when {
                        count == 0 -> 0
                        maxCount <= 4 -> when (count) {
                            1 -> 1
                            2 -> 2
                            3 -> 3
                            else -> 4
                        }
                        else -> {
                            val percentile = count.toDouble() / maxCount.toDouble()
                            when {
                                percentile <= 0.25 -> 1
                                percentile <= 0.5 -> 2
                                percentile <= 0.75 -> 3
                                else -> 4
                            }
                        }
                    }
                    
                    cellPaint.color = colorLevels[colorLevel]
                    
                    // Draw rounded rectangle for each contribution cell
                    val rect = RectF(
                        currentX,
                        currentY,
                        currentX + adjustedCellSize,
                        currentY + adjustedCellSize
                    )
                    
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
                    
                    currentY += adjustedCellSize + cellSpacing
                    dateIndex++
                } else {
                    // Draw empty cell
                    cellPaint.color = colorLevels[0]
                    val rect = RectF(
                        currentX,
                        currentY,
                        currentX + adjustedCellSize,
                        currentY + adjustedCellSize
                    )
                    
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
                    
                    currentY += adjustedCellSize + cellSpacing
                }
            }
            
            currentX += adjustedCellSize + cellSpacing
        }
    }

    private fun calculateMaxWeeks(): Int {
        return if (contributions.isEmpty()) 52 else {
            // Calculate the number of weeks based on the date range
            val sortedDates = contributions.keys.sorted()
            if (sortedDates.size < 7) 1 else sortedDates.size / 7
        }
    }

    private fun drawPlaceholder(canvas: Canvas) {
        // Draw a placeholder indicating no data
        cellPaint.color = colorLevels[0]
        cellPaint.style = Paint.Style.FILL
        
        val rect = RectF(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            width - paddingRight.toFloat(),
            height - paddingBottom.toFloat()
        )
        
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (7 * cellSize + 6 * cellSpacing + paddingTop + paddingBottom).toInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }
        
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec)
            else -> (52 * cellSize + 51 * cellSpacing + paddingLeft + paddingRight).toInt() // Default to 52 weeks
        }
        
        setMeasuredDimension(width, height)
    }
}