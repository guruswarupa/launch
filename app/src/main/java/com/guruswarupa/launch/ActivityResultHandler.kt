package com.guruswarupa.launch

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.EditText
import androidx.fragment.app.FragmentActivity

/**
 * Handles all activity result callbacks.
 * Extracted from MainActivity to reduce complexity.
 */
class ActivityResultHandler(
    private val activity: FragmentActivity,
    private val searchBox: EditText,
    private var voiceCommandHandler: VoiceCommandHandler?,
    private val shareManager: ShareManager,
    private val widgetManager: WidgetManager,
    private val wallpaperManagerHelper: WallpaperManagerHelper?,
    private val onBlockBackGestures: () -> Unit
) {
    fun setVoiceCommandHandler(handler: VoiceCommandHandler?) {
        voiceCommandHandler = handler
    }
    companion object {
        const val WALLPAPER_REQUEST_CODE = 456
        const val REQUEST_PICK_WIDGET = 800
        const val REQUEST_CONFIGURE_WIDGET = 801
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST && resultCode == Activity.RESULT_OK) {
            handleVoiceSearchResult(data)
        } else if (requestCode == ShareManager.FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            shareManager.handleFilePickerResult(data?.data)
        } else if (requestCode == REQUEST_PICK_WIDGET && resultCode == Activity.RESULT_OK) {
            onBlockBackGestures()
            widgetManager.handleWidgetPicked(activity, data, REQUEST_PICK_WIDGET)
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET && resultCode == Activity.RESULT_OK) {
            onBlockBackGestures()
            val appWidgetId = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return
            widgetManager.handleWidgetConfigured(activity, appWidgetId)
        } else if (requestCode == WALLPAPER_REQUEST_CODE) {
            wallpaperManagerHelper?.let {
                it.clearCache()
                it.setWallpaperBackground(forceReload = true)
            }
        }
    }

    private fun handleVoiceSearchResult(data: Intent?) {
        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        results?.get(0)?.let { result ->
            searchBox.setText(result)
            searchBox.setSelection(result.length)
            
            // Try handling locally first
            val handledLocally = voiceCommandHandler?.handleCommand(result) ?: false
            
            if (!handledLocally) {
                // If not handled locally, and it's not a generic app-opening/search command,
                // we could trigger the system assistant or search.
                // For now, let's keep the text in the box and maybe provide a Toast or just let it be.
                // Alternatively, we can trigger the official assistant if the user wants "powerful" search.
                
                // Let's check with VoiceSearchManager if it's available
                // To do this properly, we should probably pass VoiceSearchManager to ActivityResultHandler
                // or just rely on the searchBox text for manual triggers.
            }
        }
    }
}
