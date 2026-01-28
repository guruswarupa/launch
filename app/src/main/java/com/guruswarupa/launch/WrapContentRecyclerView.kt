package com.guruswarupa.launch

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A RecyclerView that wraps its content height instead of scrolling independently.
 * This allows it to work seamlessly within a ScrollView for unified scrolling.
 */
class WrapContentRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        // Disable nested scrolling to prevent independent scrolling
        isNestedScrollingEnabled = false
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        
        val layoutManager = layoutManager
        if (layoutManager is LinearLayoutManager && adapter != null && adapter!!.itemCount > 0) {
            val itemCount = adapter!!.itemCount
            var totalHeight = paddingTop + paddingBottom
            
            // Measure items that are currently visible/attached
            var measuredItems = 0
            for (i in 0 until itemCount) {
                val view = layoutManager.findViewByPosition(i)
                if (view != null) {
                    if (view.measuredHeight == 0) {
                        measureChild(view, widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    }
                    totalHeight += view.measuredHeight
                    measuredItems++
                }
            }
            
            // If we couldn't measure all items, estimate based on measured ones
            if (measuredItems > 0 && measuredItems < itemCount) {
                val averageItemHeight = (totalHeight - paddingTop - paddingBottom) / measuredItems
                totalHeight = paddingTop + paddingBottom + (averageItemHeight * itemCount)
            } else if (measuredItems == 0 && itemCount > 0) {
                // If no items measured yet, use a reasonable default estimate
                // This will be corrected in onLayout
                val estimatedItemHeight = 80 // Approximate height per item in dp, will be converted
                totalHeight = paddingTop + paddingBottom + (estimatedItemHeight * itemCount)
            }
            
            // Set measured dimension with calculated height
            if (totalHeight > 0) {
                val heightMode = MeasureSpec.getMode(heightSpec)
                val heightSize = MeasureSpec.getSize(heightSpec)
                
                val finalHeight = when (heightMode) {
                    MeasureSpec.EXACTLY -> heightSize
                    MeasureSpec.AT_MOST -> minOf(totalHeight, heightSize)
                    else -> totalHeight
                }
                
                setMeasuredDimension(
                    MeasureSpec.getSize(widthSpec),
                    finalHeight
                )
            }
        }
    }
    
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        
        // Recalculate height after layout
        post {
            recalculateHeight()
        }
    }
    
    private var isRecalculating = false
    
    private fun recalculateHeight() {
        // Prevent infinite loops
        if (isRecalculating) return
        
        val layoutManager = layoutManager
        if (layoutManager is LinearLayoutManager && adapter != null && adapter!!.itemCount > 0) {
            val itemCount = adapter!!.itemCount
            val currentHeight = layoutParams.height
            
            // Don't recalculate if we already have a reasonable height and all items are measured
            val measuredCount = (0 until itemCount).count { 
                layoutManager.findViewByPosition(it) != null 
            }
            
            // If we've measured most items, calculate height directly
            if (measuredCount >= itemCount * 0.8) {
                isRecalculating = true
                var totalHeight = paddingTop + paddingBottom
                
                // Measure all items
                for (i in 0 until itemCount) {
                    val view = layoutManager.findViewByPosition(i)
                    if (view != null && view.height > 0) {
                        val itemLayoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
                        val topMargin = itemLayoutParams?.topMargin ?: 0
                        val bottomMargin = itemLayoutParams?.bottomMargin ?: 0
                        totalHeight += view.height + topMargin + bottomMargin
                    }
                }
                
                // Add reasonable extra padding (reduced from 64dp to 32dp)
                val extraPaddingDp = 32f
                val extraPadding = (extraPaddingDp * resources.displayMetrics.density).toInt()
                totalHeight += extraPadding
                
                // Only update if height changed significantly (more than 10px difference)
                if (totalHeight > 0 && kotlin.math.abs(totalHeight - currentHeight) > 10) {
                    layoutParams.height = totalHeight
                    requestLayout()
                }
                
                isRecalculating = false
                return
            }
            
            // If we haven't measured enough items, temporarily expand to measure them
            if (currentHeight < 10000) { // Only if not already expanded
                isRecalculating = true
                layoutParams.height = 10000 // Large but not excessive
                requestLayout()
                
                post {
                    // Now recalculate with all items laid out
                    var totalHeight = paddingTop + paddingBottom
                    var totalMeasuredHeight = 0
                    var finalMeasuredCount = 0
                    
                    for (i in 0 until itemCount) {
                        val view = layoutManager.findViewByPosition(i)
                        if (view != null && view.height > 0) {
                            val itemLayoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
                            val topMargin = itemLayoutParams?.topMargin ?: 0
                            val bottomMargin = itemLayoutParams?.bottomMargin ?: 0
                            totalMeasuredHeight += view.height + topMargin + bottomMargin
                            finalMeasuredCount++
                        }
                    }
                    
                    totalHeight += totalMeasuredHeight
                    
                    // If we still didn't measure all, estimate the rest
                    if (finalMeasuredCount > 0 && finalMeasuredCount < itemCount) {
                        val averageHeight = totalMeasuredHeight / finalMeasuredCount
                        totalHeight += averageHeight * (itemCount - finalMeasuredCount)
                    }
                    
                    // Add reasonable extra padding
                    val extraPaddingDp = 32f
                    val extraPadding = (extraPaddingDp * resources.displayMetrics.density).toInt()
                    totalHeight += extraPadding
                    
                    // Set final height
                    if (totalHeight > 0) {
                        layoutParams.height = totalHeight
                        requestLayout()
                    }
                    
                    isRecalculating = false
                }
            }
        }
    }
    
    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        // Recalculate height when adapter is set
        post {
            recalculateHeight()
        }
    }
}
