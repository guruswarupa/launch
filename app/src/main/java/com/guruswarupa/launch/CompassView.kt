package com.guruswarupa.launch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var azimuth: Float = 0f
    private var directionName: String = "N"
    
    // Gradient colors
    private val outerRingColor = Color.parseColor("#88C0D0") // nord8
    private val innerRingColor = Color.parseColor("#8FBCBB") // nord7
    private val cardinalColor = Color.parseColor("#E5E9F0") // Light text
    private val intermediateColor = Color.parseColor("#88A3BE") // Muted
    private val northMarkerColor = Color.parseColor("#BF616A") // Red accent
    private val centerGradientStart = Color.parseColor("#5E81AC") // Blue
    private val centerGradientEnd = Color.parseColor("#88C0D0") // Light blue
    
    private val compassRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = outerRingColor
    }
    
    private val innerRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = innerRingColor
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
        setShadowLayer(8f, 0f, 2f, Color.parseColor("#40000000"))
    }
    
    private val cardinalPaint = Paint().apply {
        isAntiAlias = true
        color = cardinalColor
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 0f, 1f, Color.parseColor("#30000000"))
    }
    
    private val intermediatePaint = Paint().apply {
        isAntiAlias = true
        color = intermediateColor
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    
    private val markerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = northMarkerColor
    }
    
    private val tickPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.parseColor("#88A3BE")
    }
    
    private val backgroundGradient = LinearGradient(
        0f, 0f, 0f, 0f,
        intArrayOf(
            Color.parseColor("#2E3440"), // Dark background
            Color.parseColor("#3B4252")  // Slightly lighter
        ),
        null,
        Shader.TileMode.CLAMP
    )
    
    fun setDirection(azimuth: Float, directionName: String) {
        this.azimuth = azimuth
        this.directionName = directionName
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) - 30f
        
        // Draw background circle with gradient
        val backgroundPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            shader = RadialGradient(
                centerX, centerY, radius,
                Color.parseColor("#2E3440"),
                Color.parseColor("#3B4252"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, centerY, radius + 5f, backgroundPaint)
        
        // Save canvas state
        canvas.save()
        
        // Rotate canvas to show current direction (negative because we want compass to rotate)
        canvas.rotate(-azimuth, centerX, centerY)
        
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
            val endRadius = radius
            
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
            val endX = centerX + endRadius * sin(angleRad).toFloat()
            val endY = centerY - endRadius * cos(angleRad).toFloat()
            
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
        }
        
        // Draw cardinal directions with enhanced styling
        val directions = arrayOf("N", "E", "S", "W")
        val angles = floatArrayOf(0f, 90f, 180f, 270f)
        val directionColors = arrayOf(
            Color.parseColor("#BF616A"), // N - Red
            Color.parseColor("#88C0D0"), // E - Blue
            Color.parseColor("#A3BE8C"), // S - Green
            Color.parseColor("#D08770")  // W - Orange
        )
        
        for (i in directions.indices) {
            val angle = Math.toRadians(angles[i].toDouble())
            val x = centerX + radius * 0.82f * sin(angle).toFloat()
            val y = centerY - radius * 0.82f * cos(angle).toFloat()
            
            // Draw background circle for cardinal direction
            val bgPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = Color.parseColor("#40000000")
            }
            canvas.drawCircle(x, y, 28f, bgPaint)
            
            // Draw direction text with color
            val textBounds = Rect()
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
        val intermediateDirections = arrayOf("NE", "SE", "SW", "NW")
        val intermediateAngles = floatArrayOf(45f, 135f, 225f, 315f)
        
        for (i in intermediateDirections.indices) {
            val angle = Math.toRadians(intermediateAngles[i].toDouble())
            val x = centerX + radius * 0.78f * sin(angle).toFloat()
            val y = centerY - radius * 0.78f * cos(angle).toFloat()
            
            val textBounds = Rect()
            intermediatePaint.textSize = 22f
            intermediatePaint.getTextBounds(intermediateDirections[i], 0, intermediateDirections[i].length, textBounds)
            canvas.drawText(
                intermediateDirections[i],
                x,
                y + textBounds.height() / 2f,
                intermediatePaint
            )
        }
        
        // Restore canvas
        canvas.restore()
        
        // Draw north marker (fixed at top, doesn't rotate) with shadow
        val markerShadowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.parseColor("#40000000")
        }
        val markerPath = Path()
        val markerY = centerY - radius - 8f
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
        val centerGradient = RadialGradient(
            centerX, centerY, 35f,
            centerGradientStart,
            centerGradientEnd,
            Shader.TileMode.CLAMP
        )
        directionPaint.shader = centerGradient
        directionPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 35f, directionPaint)
        
        // Draw center circle border
        directionPaint.shader = null
        directionPaint.style = Paint.Style.STROKE
        directionPaint.color = outerRingColor
        directionPaint.strokeWidth = 3f
        canvas.drawCircle(centerX, centerY, 35f, directionPaint)
        
        // Draw current direction text (doesn't rotate) with enhanced styling
        val textBounds = Rect()
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
}
