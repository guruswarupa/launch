package com.guruswarupa.launch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.pm.ResolveInfo
import androidx.core.content.ContextCompat

class FastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".split("").filter { it.isNotEmpty() }
    private var recyclerView: RecyclerView? = null
    
    private var currentAlpha = 0f
    private var alphaAnimator: ValueAnimator? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 30f
    }
    
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 45f
        isFakeBoldText = true
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 80f
        isFakeBoldText = true
    }

    private var selectedIndex = -1
    private var isSliding = false

    fun setRecyclerView(rv: RecyclerView) {
        this.recyclerView = rv
    }

    fun setTextColor(color: Int) {
        paint.color = color
        selectedPaint.color = color
        bubbleTextPaint.color = color
        invalidate()
    }

    private fun animateAlpha(to: Float) {
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofFloat(currentAlpha, to).apply {
            duration = 200
            addUpdateListener {
                currentAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (currentAlpha <= 0f) return
        
        val saveCount = canvas.save()
        paint.alpha = (currentAlpha * 180).toInt()
        selectedPaint.alpha = (currentAlpha * 255).toInt()
        
        val itemHeight = height.toFloat() / alphabet.size
        for (i in alphabet.indices) {
            val y = itemHeight * i + itemHeight / 2 + paint.textSize / 2
            val x = width.toFloat() / 2
            
            if (i == selectedIndex) {
                canvas.drawText(alphabet[i], x, y, selectedPaint)
                
                // Draw preview bubble in the middle of the screen (to the left of scroller)
                val bubbleSize = 150f
                val bubbleX = -bubbleSize // Position it to the left of the view
                val bubbleY = height.toFloat() / 2
                val rect = RectF(bubbleX - bubbleSize/2, bubbleY - bubbleSize/2, bubbleX + bubbleSize/2, bubbleY + bubbleSize/2)
                bubblePaint.alpha = (currentAlpha * 200).toInt()
                canvas.drawRoundRect(rect, 20f, 20f, bubblePaint)
                
                bubbleTextPaint.alpha = (currentAlpha * 255).toInt()
                canvas.drawText(alphabet[i], bubbleX, bubbleY + bubbleTextPaint.textSize / 3, bubbleTextPaint)
            } else {
                canvas.drawText(alphabet[i], x, y, paint)
            }
        }
        canvas.restoreToCount(saveCount)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only trigger if touch is within the view width
        if (event.x < 0 || event.x > width && !isSliding) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isSliding = true
                animateAlpha(1f)
                handleTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSliding) {
                    handleTouch(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSliding = false
                selectedIndex = -1
                animateAlpha(0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(y: Float) {
        val index = (y / height * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
        if (index != selectedIndex) {
            selectedIndex = index
            scrollToLetter(alphabet[index])
            invalidate()
        }
    }

    private fun scrollToLetter(letter: String) {
        val adapter = recyclerView?.adapter as? AppAdapter ?: return
        val appList = adapter.appList
        
        var targetPosition = -1
        for (i in appList.indices) {
            val label = adapter.getAppLabel(i)
            if (letter == "#") {
                if (label.isNotEmpty() && !label[0].isLetter()) {
                    targetPosition = i
                    break
                }
            } else {
                if (label.startsWith(letter, ignoreCase = true)) {
                    targetPosition = i
                    break
                }
            }
        }
        
        if (targetPosition != -1) {
            val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(targetPosition, 0)
            
            val parent = recyclerView?.parent?.parent as? androidx.core.widget.NestedScrollView
            if (parent != null) {
                recyclerView?.post {
                    val view = layoutManager?.findViewByPosition(targetPosition)
                    if (view != null) {
                        val top = view.top + (recyclerView?.top ?: 0)
                        parent.smoothScrollTo(0, top)
                    } else {
                        // Estimate top if view is not visible yet
                        // NestedScrollView usually needs the exact Y
                        // If it's a huge list, this might be tricky, but scroll to position usually works
                        recyclerView?.scrollToPosition(targetPosition)
                        // Forced scroll to top of RV if it's the first match
                        if (targetPosition == 0) {
                            parent.smoothScrollTo(0, recyclerView?.top ?: 0)
                        }
                    }
                }
            }
        }
    }
}
