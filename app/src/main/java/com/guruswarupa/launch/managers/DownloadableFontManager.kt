package com.guruswarupa.launch.managers

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.guruswarupa.launch.R
import java.util.concurrent.ConcurrentHashMap

private const val PREFS_NAME = "com.guruswarupa.launch.DOWNLOADABLE_FONTS"
private const val KEY_DOWNLOADED_FONTS = "downloaded_fonts"

object DownloadableFontManager {

    data class FontOption(
        val styleKey: String,
        val displayName: String,
        val queryName: String,
        val weight: Int = 400
    )

    private val fontOptionsList = listOf(
        FontOption("droid_sans_fallback", "Droid Sans Fallback", "Droid Sans"),
        FontOption("ubuntu_regular", "Ubuntu Regular", "Ubuntu"),
        FontOption("noto_sans", "Noto Sans", "Noto Sans"),
        FontOption("noto_serif", "Noto Serif", "Noto Serif"),
        FontOption("noto_sans_display", "Noto Sans Display", "Noto Sans Display"),
        FontOption("dejavu_sans", "DejaVu Sans", "DejaVu Sans"),
        FontOption("dejavu_serif", "DejaVu Serif", "DejaVu Serif"),
        FontOption("dejavu_mono", "DejaVu Mono", "DejaVu Mono"),
        FontOption("fira_code", "Fira Code", "Fira Code")
    )

    private val fontOptionsMap = fontOptionsList.associateBy { it.styleKey }
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val pendingCallbacks = ConcurrentHashMap<String, MutableList<(Boolean) -> Unit>>()
    private val handler = Handler(Looper.getMainLooper())

    fun getFontOptions(): List<FontOption> = fontOptionsList

    fun hasTypeface(styleKey: String): Boolean = typefaceCache.containsKey(styleKey)

    fun getTypeface(styleKey: String): Typeface? = typefaceCache[styleKey]

    fun isDownloaded(context: Context, styleKey: String): Boolean {
        return getDownloadedFonts(context).contains(styleKey)
    }

    fun uninstallFont(context: Context, styleKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getDownloadedFonts(context).apply { remove(styleKey) }
        prefs.edit().putStringSet(KEY_DOWNLOADED_FONTS, updated).apply()
        typefaceCache.remove(styleKey)
    }

    private fun getDownloadedFonts(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DOWNLOADED_FONTS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun requestFont(context: Context, styleKey: String, callback: (Boolean) -> Unit) {
        val option = fontOptionsMap[styleKey]
        if (option == null) {
            callback(false)
            return
        }
        typefaceCache[styleKey]?.let {
            callback(true)
            return
        }
        val queue = pendingCallbacks.computeIfAbsent(styleKey) { mutableListOf() }
        synchronized(queue) {
            queue.add(callback)
            if (queue.size > 1) return
        }
        val query = "name=${option.queryName}&weight=${option.weight}&width=100"
        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            query,
            R.array.com_google_android_gms_fonts_certs
        )
        FontsContractCompat.requestFont(context, request, object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) {
                typefaceCache[styleKey] = typeface
                markFontDownloaded(context, styleKey)
                dispatch(styleKey, true)
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                dispatch(styleKey, false)
            }
        }, handler)
    }

    private fun markFontDownloaded(context: Context, styleKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getDownloadedFonts(context).apply { add(styleKey) }
        prefs.edit().putStringSet(KEY_DOWNLOADED_FONTS, updated).apply()
    }

    private fun dispatch(styleKey: String, success: Boolean) {
        val callbacks = pendingCallbacks.remove(styleKey) ?: return
        handler.post {
            callbacks.forEach { it(success) }
        }
    }
}
