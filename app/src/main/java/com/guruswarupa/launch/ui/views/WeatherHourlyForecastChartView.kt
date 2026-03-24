package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.WeatherManager
import kotlin.math.max

class WeatherHourlyForecastChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var entries: List<WeatherManager.HourlyForecast> = emptyList()
    private var isFahrenheit = false

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 31f
        textAlign = Paint.Align.RIGHT
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        updateColors()
    }

    private fun updateColors() {
        axisPaint.color = ContextCompat.getColor(context, R.color.widget_divider)
        linePaint.color = ContextCompat.getColor(context, R.color.nord8)
        pointPaint.color = ContextCompat.getColor(context, R.color.nord8)
        labelPaint.color = ContextCompat.getColor(context, R.color.widget_text_secondary)
        valuePaint.color = ContextCompat.getColor(context, R.color.widget_text)
        cardPaint.color = ContextCompat.getColor(context, R.color.widget_item_background)
    }

    fun setForecastEntries(hourlyEntries: List<WeatherManager.HourlyForecast>, fahrenheit: Boolean) {
        entries = hourlyEntries
        isFahrenheit = fahrenheit
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (entries.isEmpty()) dpToPx(160) else max(dpToPx(180), dpToPx(42) * entries.size + dpToPx(36))
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.weather_forecast_chart_empty),
                width / 2f,
                height / 2f,
                valuePaint
            )
            return
        }

        val chartRect = RectF(
            paddingLeft + dpToPx(58).toFloat(),
            paddingTop + dpToPx(12).toFloat(),
            width - paddingRight - dpToPx(18).toFloat(),
            height - paddingBottom - dpToPx(18).toFloat()
        )
        canvas.drawRoundRect(chartRect, dpToPx(14).toFloat(), dpToPx(14).toFloat(), cardPaint)

        val minTemp = entries.minOf { it.temperature }
        val maxTemp = entries.maxOf { it.temperature }
        val tempRange = max(1, maxTemp - minTemp)
        val innerLeft = chartRect.left + dpToPx(18)
        val innerRight = chartRect.right - dpToPx(18)
        val innerTop = chartRect.top + dpToPx(16)
        val innerBottom = chartRect.bottom - dpToPx(16)
        val stepY = if (entries.size == 1) 0f else (innerBottom - innerTop) / (entries.size - 1)
        val linePath = Path()

        entries.forEachIndexed { index, entry ->
            val normalized = (entry.temperature - minTemp).toFloat() / tempRange.toFloat()
            val x = innerLeft + normalized * (innerRight - innerLeft)
            val y = innerTop + index * stepY
            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
            canvas.drawLine(chartRect.left + dpToPx(10), y, chartRect.right - dpToPx(10), y, axisPaint)
            canvas.drawCircle(x, y, dpToPx(4).toFloat(), pointPaint)
            canvas.drawText(entry.timeLabel, chartRect.left - dpToPx(10).toFloat(), y + dpToPx(4), labelPaint)
            canvas.drawText(formatTemperature(entry.temperature), x, y - dpToPx(8).toFloat(), valuePaint)
        }

        canvas.drawPath(linePath, linePaint)
    }

    private fun formatTemperature(value: Int): String {
        return if (isFahrenheit) "$value°F" else "$value°C"
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
