package com.guruswarupa.launch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.*

class DailyUsagePieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var appUsages: Map<String, Long> = emptyMap()
    private var selectedSliceIndex: Int = -1

    private val piePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val strokeColor = "#1A1A1A".toColorInt()
    private val noDataColor = "#666666".toColorInt()
    private val zeroUsageColor = "#2A2A2A".toColorInt()

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = 3f
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Extended color palette for better app distinction
    private val appColors = listOf(
        "#E53935".toColorInt(), // Red
        "#8E24AA".toColorInt(), // Purple
        "#3949AB".toColorInt(), // Deep Blue
        "#00897B".toColorInt(), // Teal Green
        "#FDD835".toColorInt(), // Yellow
        "#FB8C00".toColorInt(), // Orange
        "#6D4C41".toColorInt(), // Brown
        "#C0CA33".toColorInt(), // Lime
        "#00ACC1".toColorInt(), // Cyan
        "#D81B60".toColorInt(), // Pink
        "#5E35B1".toColorInt(), // Deep Purple
        "#0277BD".toColorInt(), // Light Blue
        "#00796B".toColorInt(), // Teal
        "#689F38".toColorInt(), // Light Green
        "#F57C00".toColorInt(), // Deep Orange
        "#455A64".toColorInt(), // Blue Grey
        "#E91E63".toColorInt(), // Pink Red
        "#9C27B0".toColorInt(), // Purple
        "#673AB7".toColorInt(), // Deep Purple
        "#3F51B5".toColorInt(), // Indigo
        "#2196F3".toColorInt(), // Blue
        "#03A9F4".toColorInt(), // Light Blue
        "#00BCD4".toColorInt(), // Cyan
        "#009688".toColorInt(), // Teal
        "#4CAF50".toColorInt(), // Green
        "#8BC34A".toColorInt(), // Light Green
        "#CDDC39".toColorInt(), // Lime
        "#FFEB3B".toColorInt(), // Yellow
        "#FFC107".toColorInt(), // Amber
        "#FF9800".toColorInt(), // Orange
        "#FF5722".toColorInt(), // Deep Orange
        "#795548".toColorInt(), // Brown
        "#9E9E9E".toColorInt(), // Grey
        "#607D8B".toColorInt(), // Blue Grey
        "#EF5350".toColorInt(), // Red Light
        "#EC407A".toColorInt(), // Pink Light
        "#AB47BC".toColorInt(), // Purple Light
        "#7E57C2".toColorInt(), // Deep Purple Light
        "#5C6BC0".toColorInt(), // Indigo Light
        "#42A5F5".toColorInt(), // Blue Light
        "#29B6F6".toColorInt(), // Light Blue Light
        "#26C6DA".toColorInt(), // Cyan Light
        "#26A69A".toColorInt(), // Teal Light
        "#66BB6A".toColorInt(), // Green Light
        "#9CCC65".toColorInt(), // Light Green Light
        "#D4E157".toColorInt(), // Lime Light
        "#FFEE58".toColorInt(), // Yellow Light
        "#FFCA28".toColorInt(), // Amber Light
        "#FFA726".toColorInt(), // Orange Light
        "#FF7043".toColorInt()  // Deep Orange Light
    )

    fun setAppUsageData(data: Map<String, Long>) {
        appUsages = data
        invalidate()
    }

    @Suppress("unused")
    fun setSelectedSlice(index: Int) {
        selectedSliceIndex = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (appUsages.isEmpty()) {
            textPaint.textSize = 32f
            textPaint.color = noDataColor
            canvas.drawText(
                "No usage data",
                width / 2f,
                height / 2f,
                textPaint
            )
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 40f

        val totalUsage = appUsages.values.sum()
        if (totalUsage == 0L) {
            piePaint.color = zeroUsageColor
            canvas.drawCircle(centerX, centerY, radius, piePaint)
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
            return
        }

        // Sort apps by usage (descending)
        val sortedApps = appUsages.toList().sortedByDescending { it.second }

        // Draw pie slices
        var startAngle = -90f // Start from top
        sortedApps.forEachIndexed { sliceIndex, (_, usage) ->
            val sweepAngle = (usage.toFloat() / totalUsage) * 360f

            // Get color for this app
            val colorIndex = sliceIndex % appColors.size
            piePaint.color = appColors[colorIndex]

            // Highlight selected slice
            val drawRadius = if (selectedSliceIndex == sliceIndex) radius * 1.1f else radius

            canvas.drawArc(
                centerX - drawRadius,
                centerY - drawRadius,
                centerX + drawRadius,
                centerY + drawRadius,
                startAngle,
                sweepAngle,
                true,
                piePaint
            )

            // Draw stroke between slices
            canvas.drawArc(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                startAngle,
                sweepAngle,
                true,
                strokePaint
            )

            startAngle += sweepAngle
        }
    }

    fun getColorForApp(index: Int): Int {
        return appColors[index % appColors.size]
    }
}
