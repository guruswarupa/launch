package com.guruswarupa.launch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.TimeUnit

class WeeklyUsageGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var usageData: List<Pair<String, Long>> = emptyList()
    private var appUsageData: List<Pair<String, Map<String, Long>>> = emptyList()

    private val barPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#CCCCCC")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val axisPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#444444")
        strokeWidth = 1f
    }

    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#20000000")
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#80000000") // 50% transparent black
    }

    private val legendPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 20f
    }

    // Predefined colors for different apps
    private val appColors = listOf(
        Color.parseColor("#E53935"), // Bright Red
        Color.parseColor("#8E24AA"), // Purple
        Color.parseColor("#3949AB"), // Deep Blue
        Color.parseColor("#00897B"), // Teal Green
        Color.parseColor("#FDD835"), // Bright Yellow
        Color.parseColor("#FB8C00"), // Orange
        Color.parseColor("#6D4C41"), // Brown
        Color.parseColor("#C0CA33"), // Lime
        Color.parseColor("#00ACC1"), // Cyan
        Color.parseColor("#D81B60")  // Pink
    )


    fun setUsageData(data: List<Pair<String, Long>>) {
        usageData = data
        invalidate()
    }

    fun setAppUsageData(data: List<Pair<String, Map<String, Long>>>) {
        appUsageData = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val dataToUse = if (appUsageData.isNotEmpty()) appUsageData else
            usageData.map { it.first to mapOf("Total" to it.second) }

        if (dataToUse.isEmpty()) return

        val padding = 60f
        val bottomPadding = 150f // Increased for legend
        val topPadding = 80f
        val graphWidth = width - 2 * padding
        val graphHeight = height - bottomPadding - topPadding

        // Find max usage for scaling
        val maxUsage = dataToUse.maxOfOrNull { it.second.values.sum() } ?: 1L
        if (maxUsage == 0L) return

        // Calculate bar dimensions
        val barWidth = graphWidth / dataToUse.size * 0.7f
        val barSpacing = graphWidth / dataToUse.size * 0.3f

        // Get top apps across all days and group others
        val topAppLimit = 5 // Show only top 5 apps
        val allAppUsage = mutableMapOf<String, Long>()

        // Calculate total usage for each app across all days
        dataToUse.forEach { (_, appUsages) ->
            appUsages.forEach { (appName, usage) ->
                allAppUsage[appName] = (allAppUsage[appName] ?: 0) + usage
            }
        }

        // Get top apps by total usage
        val topApps = allAppUsage.toList()
            .sortedByDescending { it.second }
            .take(topAppLimit)
            .map { it.first }
            .toSet()

        // Create color map for top apps + "Others"
        val allAppsForLegend = topApps.toList() + "Others"
        val appColorMap = allAppsForLegend.mapIndexed { index, app ->
            app to appColors[index % appColors.size]
        }.toMap()

        // Draw grid lines
        for (i in 0..5) {
            val y = height - bottomPadding - (i * graphHeight / 5)
            axisPaint.alpha = 30
            canvas.drawLine(padding, y, width - padding, y, axisPaint)
        }

        // Draw stacked bars
        dataToUse.forEachIndexed { index, (day, appUsages) ->
            val totalUsage = appUsages.values.sum()
            val x = padding + (index * graphWidth / dataToUse.size) + barSpacing / 2
            val barBottom = height - bottomPadding

            var currentTop = barBottom

            // Group apps into top apps and others
            val topAppUsages = mutableMapOf<String, Long>()
            var othersUsage = 0L

            appUsages.forEach { (appName, usage) ->
                if (topApps.contains(appName)) {
                    topAppUsages[appName] = usage
                } else {
                    othersUsage += usage
                }
            }

            // Add "Others" if there's usage
            if (othersUsage > 0) {
                topAppUsages["Others"] = othersUsage
            }

            // Draw each app's portion of the bar (sorted by usage)
            topAppUsages.entries.sortedByDescending { it.value }.forEach { (appName, usage) ->
                if (usage > 0) {
                    val segmentHeight = (usage.toFloat() / maxUsage * graphHeight)
                    val segmentTop = currentTop - segmentHeight

                    barPaint.color = appColorMap[appName] ?: appColors[0]

                    canvas.drawRect(
                        x,
                        segmentTop,
                        x + barWidth,
                        currentTop,
                        barPaint
                    )

                    currentTop = segmentTop
                }
            }

            // Draw day label
            labelPaint.textSize = 20f
            canvas.drawText(
                day,
                x + barWidth / 2,
                height - bottomPadding + 25f,
                labelPaint
            )

            // Draw total time above bar
            if (totalUsage > 0) {
                val usageText = formatUsageTime(totalUsage)
                textPaint.textSize = if (usageText.length > 5) 18f else 22f
                textPaint.color = Color.WHITE
                canvas.drawText(
                    usageText,
                    x + barWidth / 2,
                    currentTop - 10f,
                    textPaint
                )
            }
        }

        // Draw legend
        var legendY = height - bottomPadding + 60f
        var legendX = padding
        val legendItemWidth = 120f
        val legendItemHeight = 25f

        allAppsForLegend.forEachIndexed { index, appName ->
            val color = appColorMap[appName] ?: appColors[0]

            // Draw color square
            barPaint.color = color
            canvas.drawRect(
                legendX,
                legendY - 15f,
                legendX + 15f,
                legendY,
                barPaint
            )

            // Draw app name
            legendPaint.textSize = 16f
            legendPaint.color = Color.WHITE
            canvas.drawText(
                if (appName.length > 8) appName.take(8) + "..." else appName,
                legendX + 20f,
                legendY - 2f,
                legendPaint
            )

            legendX += legendItemWidth
            if (legendX + legendItemWidth > width - padding) {
                legendX = padding
                legendY += legendItemHeight
            }
        }

        // Draw title
        textPaint.textSize = 32f
        textPaint.color = Color.WHITE
        canvas.drawText(
            "",
            width / 2f,
            40f,
            textPaint
        )

        // Reset paint shader
        barPaint.shader = null
    }

    private fun formatUsageTime(timeInMillis: Long): String {
        if (timeInMillis <= 0) return ""

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}