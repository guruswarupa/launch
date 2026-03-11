package com.guruswarupa.launch.managers

import android.app.Activity
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants

object TypographyManager {

    private const val DEFAULT_SCALE_PERCENT = 100
    private const val MIN_SCALE_PERCENT = 80
    private const val MAX_SCALE_PERCENT = 140

    fun applyToActivity(activity: Activity) {
        val root = activity.window?.decorView ?: return
        applyToView(root)
    }

    fun applyToView(view: View) {
        val context = view.context
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Activity.MODE_PRIVATE)

        val configuredScalePercent = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, DEFAULT_SCALE_PERCENT)
            .coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)
        val fontScale = configuredScalePercent / 100f

        val fontStyle = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val intensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"

        applyToViewTree(view, fontScale, fontStyle, intensity)
    }

    fun applyToViewTree(view: View, fontScale: Float, fontStyle: String, intensity: String) {
        if (view is TextView) {
            val baseSizePx = (view.getTag(R.id.tag_typography_base_size_px) as? Float)
                ?: view.textSize.also { view.setTag(R.id.tag_typography_base_size_px, it) }
            val baseItalic = (view.getTag(R.id.tag_typography_base_italic) as? Boolean)
                ?: ((view.typeface?.style ?: Typeface.NORMAL) and Typeface.ITALIC != 0)
                    .also { view.setTag(R.id.tag_typography_base_italic, it) }

            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSizePx * fontScale)

            val baseFamily = resolveFamily(fontStyle, intensity)
            val style = when {
                intensity == "bold" && baseItalic -> Typeface.BOLD_ITALIC
                intensity == "bold" -> Typeface.BOLD
                baseItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            view.typeface = Typeface.create(baseFamily, style)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToViewTree(view.getChildAt(i), fontScale, fontStyle, intensity)
            }
        }
    }

    private fun resolveFamily(style: String, intensity: String): String {
        return when (style) {
            "serif" -> "serif"
            "monospace" -> "monospace"
            "condensed" -> "sans-serif-condensed"
            "rounded" -> "sans-serif-rounded"
            "condensed_light" -> "sans-serif-condensed-light"
            "condensed_medium" -> "sans-serif-condensed-medium"
            "serif_monospace" -> "serif-monospace"
            "display" -> "sans-serif"
            "thin" -> "sans-serif-thin"
            "medium" -> "sans-serif-medium"
            "black" -> "sans-serif-black"
            "smallcaps" -> "sans-serif-smallcaps"
            "casual" -> "casual"
            "cursive" -> "cursive"
            else -> if (intensity == "light") "sans-serif-light" else "sans-serif"
        }
    }
}
