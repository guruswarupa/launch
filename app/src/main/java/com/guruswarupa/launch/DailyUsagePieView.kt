package com.guruswarupa.launch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
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

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#1A1A1A")
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
        Color.parseColor("#E53935"), // Red
        Color.parseColor("#8E24AA"), // Purple
        Color.parseColor("#3949AB"), // Deep Blue
        Color.parseColor("#00897B"), // Teal Green
        Color.parseColor("#FDD835"), // Yellow
        Color.parseColor("#FB8C00"), // Orange
        Color.parseColor("#6D4C41"), // Brown
        Color.parseColor("#C0CA33"), // Lime
        Color.parseColor("#00ACC1"), // Cyan
        Color.parseColor("#D81B60"), // Pink
        Color.parseColor("#5E35B1"), // Deep Purple
        Color.parseColor("#0277BD"), // Light Blue
        Color.parseColor("#00796B"), // Teal
        Color.parseColor("#689F38"), // Light Green
        Color.parseColor("#F57C00"), // Deep Orange
        Color.parseColor("#455A64"), // Blue Grey
        Color.parseColor("#E91E63"), // Pink Red
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#673AB7"), // Deep Purple
        Color.parseColor("#3F51B5"), // Indigo
        Color.parseColor("#2196F3"), // Blue
        Color.parseColor("#03A9F4"), // Light Blue
        Color.parseColor("#00BCD4"), // Cyan
        Color.parseColor("#009688"), // Teal
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#8BC34A"), // Light Green
        Color.parseColor("#CDDC39"), // Lime
        Color.parseColor("#FFEB3B"), // Yellow
        Color.parseColor("#FFC107"), // Amber
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#FF5722"), // Deep Orange
        Color.parseColor("#795548"), // Brown
        Color.parseColor("#9E9E9E"), // Grey
        Color.parseColor("#607D8B"), // Blue Grey
        Color.parseColor("#EF5350"), // Red Light
        Color.parseColor("#EC407A"), // Pink Light
        Color.parseColor("#AB47BC"), // Purple Light
        Color.parseColor("#7E57C2"), // Deep Purple Light
        Color.parseColor("#5C6BC0"), // Indigo Light
        Color.parseColor("#42A5F5"), // Blue Light
        Color.parseColor("#29B6F6"), // Light Blue Light
        Color.parseColor("#26C6DA"), // Cyan Light
        Color.parseColor("#26A69A"), // Teal Light
        Color.parseColor("#66BB6A"), // Green Light
        Color.parseColor("#9CCC65"), // Light Green Light
        Color.parseColor("#D4E157"), // Lime Light
        Color.parseColor("#FFEE58"), // Yellow Light
        Color.parseColor("#FFCA28"), // Amber Light
        Color.parseColor("#FFA726"), // Orange Light
        Color.parseColor("#FF7043")  // Deep Orange Light
    )

    fun setAppUsageData(data: Map<String, Long>) {
        appUsages = data
        invalidate()
    }

    fun setSelectedSlice(index: Int) {
        selectedSliceIndex = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (appUsages.isEmpty()) {
            textPaint.textSize = 32f
            textPaint.color = Color.parseColor("#666666")
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
            piePaint.color = Color.parseColor("#2A2A2A")
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
