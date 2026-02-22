package com.guruswarupa.launch.ui.views

import android.content.Context
import android.graphics.*
import android.graphics.PorterDuff
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var azimuth: Float = 0f
    private var directionName: String = "N"
    private var accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    
    // Gradient colors
    private val outerRingColor = "#5E81AC".toColorInt() // nord10 - Deeper blue
    private val innerRingColor = "#81A1C1".toColorInt() // nord9 - Medium blue
    private val cardinalColor = "#ECEFF4".toColorInt() // nord6 - Light text
    private val intermediateColor = "#88C0D0".toColorInt() // nord8 - Light blue
    private val northMarkerColor = "#BF616A".toColorInt() // nord11 - Red accent
    private val centerGradientStart = "#88C0D0".toColorInt() // nord8 - Light blue
    private val centerGradientEnd = "#5E81AC".toColorInt() // nord10 - Deep blue
    
    private val compassRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = outerRingColor
        setShadowLayer(6f, 0f, 3f, "#60000000".toColorInt())
    }
    
    private val innerRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = innerRingColor
        setShadowLayer(4f, 0f, 2f, "#40000000".toColorInt())
    }
    
    private val directionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 56f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(12f, 0f, 4f, "#80000000".toColorInt())
    }
    
    private val cardinalPaint = Paint().apply {
        isAntiAlias = true
        color = cardinalColor
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 2f, "#80000000".toColorInt())
    }
    
    private val intermediatePaint = Paint().apply {
        isAntiAlias = true
        color = intermediateColor
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        setShadowLayer(4f, 0f, 1f, "#60000000".toColorInt())
    }
    
    private val markerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = northMarkerColor
        setShadowLayer(6f, 0f, 3f, "#60000000".toColorInt())
    }
    
    private val tickPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = "#88A3BE".toColorInt()
        setShadowLayer(2f, 0f, 1f, "#30000000".toColorInt())
    }

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val cardinalBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = "#40000000".toColorInt()
    }

    private val markerShadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = "#40000000".toColorInt()
    }

    private val calibrationPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = "#EBCB8B".toColorInt() // nord13 yellow
        strokeCap = Paint.Cap.ROUND
    }

    private val markerPath = Path()
    private val textBounds = Rect()
    
    private val directionColors = intArrayOf(
        "#BF616A".toColorInt(), // N - Red
        "#88C0D0".toColorInt(), // E - Blue
        "#A3BE8C".toColorInt(), // S - Green
        "#D08770".toColorInt()  // W - Orange
    )
    private val directions = arrayOf("N", "E", "S", "W")
    private val angles = floatArrayOf(0f, 90f, 180f, 270f)
    private val intermediateDirections = arrayOf("NE", "SE", "SW", "NW")
    private val intermediateAngles = floatArrayOf(45f, 135f, 225f, 315f)

    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f
    private var backgroundShader: Shader? = null
    private var centerShader: Shader? = null

    private var calibrationPhase = 0f

    fun setDirection(azimuth: Float, directionName: String, accuracy: Int) {
        this.azimuth = azimuth
        this.directionName = directionName
        this.accuracy = accuracy
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            calibrationPhase = (calibrationPhase + 0.1f) % (2 * PI.toFloat())
        }
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = minOf(centerX, centerY) - 30f
        
        backgroundShader = RadialGradient(
            centerX, centerY, radius + 5f,
            "#2E3440".toColorInt(),
            "#3B4252".toColorInt(),
            Shader.TileMode.CLAMP
        )
        
        // Add outer glow effect
        val outerGlowShader = RadialGradient(
            centerX, centerY, radius + 15f,
            Color.TRANSPARENT,
            "#405E81AC".toColorInt(),
            Shader.TileMode.CLAMP
        )
        
        // Combine shaders
        backgroundShader = ComposeShader(
            backgroundShader!!, outerGlowShader, PorterDuff.Mode.ADD
        )
        
        centerShader = RadialGradient(
            centerX, centerY, 35f,
            centerGradientStart,
            centerGradientEnd,
            Shader.TileMode.CLAMP
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (radius <= 0) return

        // Draw background circle with gradient
        backgroundPaint.shader = backgroundShader
        canvas.drawCircle(centerX, centerY, radius + 5f, backgroundPaint)

        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            drawCalibration(canvas)
            return
        }
        
        // Rotate canvas to show current direction (negative because we want compass to rotate)
        canvas.withRotation(-azimuth, centerX, centerY) {
            // Draw outer ring with glow effect
            compassRingPaint.color = outerRingColor
            compassRingPaint.strokeWidth = 8f
            canvas.drawCircle(centerX, centerY, radius, compassRingPaint)

            // Draw inner decorative ring
            innerRingPaint.color = innerRingColor
            innerRingPaint.strokeWidth = 2f
            canvas.drawCircle(centerX, centerY, radius * 0.92f, innerRingPaint)

            // Draw middle ring
            canvas.drawCircle(centerX, centerY, radius * 0.75f, innerRingPaint)

            // Draw tick marks with varying sizes
            for (i in 0 until 72) {
                val angle = i * 5f
                val angleRad = Math.toRadians(angle.toDouble())

                val isMajor = i % 6 == 0 // Every 30 degrees
                val isMedium = i % 3 == 0 // Every 15 degrees

                val startRadius = when {
                    isMajor -> radius * 0.88f
                    isMedium -> radius * 0.92f
                    else -> radius * 0.96f
                }

                tickPaint.strokeWidth = when {
                    isMajor -> 3f
                    isMedium -> 2f
                    else -> 1f
                }
                tickPaint.color = when {
                    isMajor -> outerRingColor
                    isMedium -> innerRingColor
                    else -> intermediateColor
                }

                val startX = centerX + startRadius * sin(angleRad).toFloat()
                val startY = centerY - startRadius * cos(angleRad).toFloat()
                val endX = centerX + radius * sin(angleRad).toFloat()
                val endY = centerY - radius * cos(angleRad).toFloat()

                canvas.drawLine(startX, startY, endX, endY, tickPaint)
            }

            // Draw cardinal directions with enhanced styling
            for (i in directions.indices) {
                val angle = Math.toRadians(angles[i].toDouble())
                val x = centerX + radius * 0.82f * sin(angle).toFloat()
                val y = centerY - radius * 0.82f * cos(angle).toFloat()

                // Draw background circle for cardinal direction
                canvas.drawCircle(x, y, 28f, cardinalBgPaint)

                // Draw direction text with color
                cardinalPaint.color = directionColors[i]
                cardinalPaint.textSize = 38f
                cardinalPaint.getTextBounds(directions[i], 0, directions[i].length, textBounds)
                canvas.drawText(
                    directions[i],
                    x,
                    y + textBounds.height() / 2f,
                    cardinalPaint
                )
            }

            // Draw intermediate directions
            for (i in intermediateDirections.indices) {
                val angle = Math.toRadians(intermediateAngles[i].toDouble())
                val x = centerX + radius * 0.78f * sin(angle).toFloat()
                val y = centerY - radius * 0.78f * cos(angle).toFloat()

                intermediatePaint.textSize = 22f
                intermediatePaint.getTextBounds(intermediateDirections[i], 0, intermediateDirections[i].length, textBounds)
                canvas.drawText(
                    intermediateDirections[i],
                    x,
                    y + textBounds.height() / 2f,
                    intermediatePaint
                )
            }
        }
        
        // Draw north marker (fixed at top, doesn't rotate) with shadow
        val markerY = centerY - radius - 8f
        markerPath.reset()
        markerPath.moveTo(centerX, markerY - 2f)
        markerPath.lineTo(centerX - 18f, markerY + 12f - 2f)
        markerPath.lineTo(centerX + 18f, markerY + 12f - 2f)
        markerPath.close()
        canvas.drawPath(markerPath, markerShadowPaint)
        
        markerPath.reset()
        markerPath.moveTo(centerX, markerY)
        markerPath.lineTo(centerX - 18f, markerY + 12f)
        markerPath.lineTo(centerX + 18f, markerY + 12f)
        markerPath.close()
        canvas.drawPath(markerPath, markerPaint)
        
        // Draw center circle with gradient
        directionPaint.shader = centerShader
        directionPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 35f, directionPaint)
        
        // Draw center circle border
        directionPaint.shader = null
        directionPaint.style = Paint.Style.STROKE
        directionPaint.color = outerRingColor
        directionPaint.strokeWidth = 3f
        canvas.drawCircle(centerX, centerY, 35f, directionPaint)
        
        // Draw current direction text (doesn't rotate) with enhanced styling
        textPaint.textSize = 64f
        textPaint.color = Color.WHITE
        textPaint.getTextBounds(directionName, 0, directionName.length, textBounds)
        canvas.drawText(
            directionName,
            centerX,
            centerY + textBounds.height() / 2f - 5f,
            textPaint
        )
        
        // Draw azimuth value below with smaller text
        textPaint.textSize = 28f
        val azimuthText = "${azimuth.toInt()}°"
        textPaint.getTextBounds(azimuthText, 0, azimuthText.length, textBounds)
        canvas.drawText(
            azimuthText,
            centerX,
            centerY + 50f,
            textPaint
        )
    }

    private fun drawCalibration(canvas: Canvas) {
        val path = Path()
        val size = radius * 0.6f
        
        // Draw an "8" figure (lemniscate)
        for (i in 0..100) {
            val t = (i / 100f) * 2 * PI.toFloat()
            val scale = 2 / (3 - cos(2 * t))
            val x = centerX + scale * cos(t) * size
            val y = centerY + scale * sin(2 * t) / 2 * size
            
            if (i == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }
        path.close()
        
        canvas.drawPath(path, calibrationPaint)
        
        // Draw moving dot on the path
        val dotT = calibrationPhase
        val dotScale = 2 / (3 - cos(2 * dotT))
        val dotX = centerX + dotScale * cos(dotT) * size
        val dotY = centerY + dotScale * sin(2 * dotT) / 2 * size
        
        calibrationPaint.style = Paint.Style.FILL
        canvas.drawCircle(dotX, dotY, 15f, calibrationPaint)
        calibrationPaint.style = Paint.Style.STROKE

        textPaint.textSize = 32f
        val msg = "Recalibrate (Rotate in ∞ shape)"
        textPaint.getTextBounds(msg, 0, msg.length, textBounds)
        canvas.drawText(msg, centerX, centerY + size + 60f, textPaint)
    }
}
