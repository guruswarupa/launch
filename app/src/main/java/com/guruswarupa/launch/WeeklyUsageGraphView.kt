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

    fun setUsageData(data: List<Pair<String, Long>>) {
        usageData = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (usageData.isEmpty()) return

        val padding = 60f
        val bottomPadding = 100f
        val topPadding = 80f
        val graphWidth = width - 2 * padding
        val graphHeight = height - bottomPadding - topPadding

        // Find max usage for scaling
        val maxUsage = usageData.maxOfOrNull { it.second } ?: 1L
        if (maxUsage == 0L) return

        // Calculate bar dimensions
        val barWidth = graphWidth / usageData.size * 0.7f
        val barSpacing = graphWidth / usageData.size * 0.3f

        // Draw grid lines
        for (i in 0..5) {
            val y = height - bottomPadding - (i * graphHeight / 5)
            axisPaint.alpha = 30
            canvas.drawLine(padding, y, width - padding, y, axisPaint)
        }

        // Draw bars
        usageData.forEachIndexed { index, (day, usage) ->
            val barHeight = (usage.toFloat() / maxUsage * graphHeight)
            val x = padding + (index * graphWidth / usageData.size) + barSpacing / 2
            val barTop = height - bottomPadding - barHeight
            val barBottom = height - bottomPadding

            // Create gradient colors based on usage
            val normalizedUsage = usage.toFloat() / maxUsage
            val gradientColors = when {
                normalizedUsage > 0.7f -> intArrayOf(
                    Color.parseColor("#FF6B6B"), // Red top
                    Color.parseColor("#FF5252")  // Red bottom
                )

                normalizedUsage > 0.4f -> intArrayOf(
                    Color.parseColor("#FFD93D"), // Yellow top
                    Color.parseColor("#FFB300")  // Yellow bottom
                )

                else -> intArrayOf(
                    Color.parseColor("#4ECDC4"), // Teal top
                    Color.parseColor("#26A69A")  // Teal bottom
                )
            }

            // Create gradient shader
            val gradient = LinearGradient(
                0f, barTop, 0f, barBottom,
                gradientColors[0], gradientColors[1],
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradient

            // Draw shadow
            canvas.drawRoundRect(
                x + 4f, barTop + 4f, x + barWidth + 4f, barBottom + 4f,
                12f, 12f, shadowPaint
            )

            // Draw bar with rounded corners
            canvas.drawRoundRect(
                x, barTop, x + barWidth, barBottom,
                12f, 12f, barPaint
            )

            // Draw day label
            canvas.drawText(
                day,
                x + barWidth / 2,
                height - bottomPadding + 40f,
                labelPaint
            )

            // Draw usage value on top of bar if there's enough space
            if (barHeight > 50f) {
                val hours = TimeUnit.MILLISECONDS.toHours(usage)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(usage) % 60
                val usageText = when {
                    hours > 0 -> "${hours}h ${minutes}m"
                    minutes > 0 -> "${minutes}m"
                    else -> "<1m"
                }

                textPaint.textSize = if (usageText.length > 5) 22f else 26f
                canvas.drawText(
                    usageText,
                    x + barWidth / 2,
                    barTop - 15f,
                    textPaint
                )
            }
        }

        // Draw title
        textPaint.textSize = 32f
        textPaint.color = Color.WHITE
        canvas.drawText(
            "Weekly Screen Time",
            width / 2f,
            40f,
            textPaint
        )

        // Reset paint shader
        barPaint.shader = null
    }
}
