package com.guruswarupa.launch.managers

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
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
        val fontColorValue = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT)
            ?: Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT
        val fontColor = resolveFontColor(fontColorValue)

        applyToViewTree(view, fontScale, fontStyle, intensity, fontColor)
    }

    fun applyToViewTree(view: View, fontScale: Float, fontStyle: String, intensity: String, fontColor: Int?) {
        if (view is TextView) {
            val baseSizePx = (view.getTag(R.id.tag_typography_base_size_px) as? Float)
                ?: view.textSize.also { view.setTag(R.id.tag_typography_base_size_px, it) }
            val baseItalic = (view.getTag(R.id.tag_typography_base_italic) as? Boolean)
                ?: ((view.typeface?.style ?: Typeface.NORMAL) and Typeface.ITALIC != 0)
                    .also { view.setTag(R.id.tag_typography_base_italic, it) }
            val baseFakeBold = (view.getTag(R.id.tag_typography_base_fake_bold) as? Boolean)
                ?: view.paint.isFakeBoldText.also { view.setTag(R.id.tag_typography_base_fake_bold, it) }
            val baseScaleX = (view.getTag(R.id.tag_typography_base_scale_x) as? Float)
                ?: view.textScaleX.also { view.setTag(R.id.tag_typography_base_scale_x, it) }
            val baseAlpha = (view.getTag(R.id.tag_typography_base_alpha) as? Float)
                ?: view.alpha.also { view.setTag(R.id.tag_typography_base_alpha, it) }

            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSizePx * fontScale)

            val wantsBold = intensity == "bold" || intensity == "extra_bold"
            val style = when {
                wantsBold && baseItalic -> Typeface.BOLD_ITALIC
                wantsBold -> Typeface.BOLD
                baseItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            view.typeface = resolveTypeface(view.context, fontStyle, intensity, style)
            view.paint.isFakeBoldText = if (intensity == "extra_bold") true else baseFakeBold
            val (scaleFactor, alphaFactor) = resolveIntensityVisuals(intensity)
            view.textScaleX = baseScaleX * scaleFactor
            view.alpha = baseAlpha * alphaFactor
            val baseTextColor = resolveBaseTextColor(view)
            view.setTextColor(fontColor ?: baseTextColor)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToViewTree(view.getChildAt(i), fontScale, fontStyle, intensity, fontColor)
            }
        }
    }

    private fun resolveBaseTextColor(textView: TextView): Int {
        return (textView.getTag(R.id.tag_typography_base_text_color) as? Int)
            ?: textView.currentTextColor.also {
                textView.setTag(R.id.tag_typography_base_text_color, it)
            }
    }

    private fun resolveFontColor(value: String): Int? {
        if (value == Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT) return null
        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun getConfiguredFontColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Activity.MODE_PRIVATE)
        val fontColorValue = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT)
            ?: Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT
        return resolveFontColor(fontColorValue)
    }

    private fun resolveTypeface(context: Context, styleValue: String, intensity: String, style: Int): Typeface {
        val customTypeface = resolveCustomTypeface(context, styleValue)
        return if (customTypeface != null) {
            Typeface.create(customTypeface, style)
        } else {
            val baseFamily = resolveFamily(styleValue, intensity)
            Typeface.create(baseFamily, style)
        }
    }

    private fun resolveCustomTypeface(context: Context, style: String): Typeface? {
        return when (style) {
            "droid_sans_fallback" -> ResourcesCompat.getFont(context, R.font.droid_sans_fallback)
            "ubuntu_regular" -> ResourcesCompat.getFont(context, R.font.ubuntu_regular)
            "noto_sans" -> ResourcesCompat.getFont(context, R.font.noto_sans)
            "noto_serif" -> ResourcesCompat.getFont(context, R.font.noto_serif)
            "noto_sans_display" -> ResourcesCompat.getFont(context, R.font.noto_sans_display)
            "dejavu_sans" -> ResourcesCompat.getFont(context, R.font.dejavu_sans)
            "dejavu_serif" -> ResourcesCompat.getFont(context, R.font.dejavu_serif)
            "dejavu_mono" -> ResourcesCompat.getFont(context, R.font.dejavu_mono)
            "fira_code" -> ResourcesCompat.getFont(context, R.font.fira_code)
            else -> null
        }
    }

    private fun resolveIntensityVisuals(intensity: String): Pair<Float, Float> {
        return when (intensity) {
            "light" -> Pair(0.94f, 0.88f)
            "bold" -> Pair(1.05f, 1f)
            "extra_bold" -> Pair(1.12f, 1f)
            else -> Pair(1f, 1f)
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
            "sans_serif_light" -> "sans-serif-light"
            "droid_sans_fallback" -> "Droid Sans"
            "ubuntu_regular" -> "Ubuntu"
            "droid_sans" -> "Droid Sans"
            "droid_serif" -> "Droid Serif"
            "noto_sans" -> "Noto Sans"
            "noto_serif" -> "Noto Serif"
            "noto_sans_display" -> "Noto Sans Display"
            else -> if (intensity == "light") "sans-serif-light" else "sans-serif"
        }
    }
}
