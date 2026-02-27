package com.guruswarupa.launch.managers

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R

/**
 * Manages the search type selection popup menu for the search type button.
 * Handles menu creation, item selection, and button icon updates.
 */
class SearchTypeMenuManager(
    private val context: Context,
    private val searchTypeButton: ImageButton,
    private val appSearchManager: AppSearchManager?,
    private val isFocusModeActive: () -> Boolean
) {
    
    /**
     * Sets up the search type button click listener and popup menu functionality.
     */
    fun setup() {
        searchTypeButton.setOnClickListener { view ->
            showSearchTypeMenu(view)
        }
    }
    
    /**
     * Shows the search type selection popup menu.
     */
    private fun showSearchTypeMenu(anchor: View) {
        // Create popup with custom translucent style
        val popup = createTranslucentPopupMenu(anchor)
        popup.menu.add(0, 0, 0, context.getString(R.string.search_mode_all))
        popup.menu.add(0, 1, 1, context.getString(R.string.search_mode_apps))
        popup.menu.add(0, 2, 2, context.getString(R.string.search_mode_contacts))
        popup.menu.add(0, 3, 3, context.getString(R.string.search_mode_files))
        popup.menu.add(0, 4, 4, context.getString(R.string.search_mode_maps))
        
        // Only show web, Play Store, and YouTube options if not in focus mode
        if (!isFocusModeActive()) {
            popup.menu.add(0, 5, 5, context.getString(R.string.search_mode_web))
            popup.menu.add(0, 6, 6, context.getString(R.string.search_mode_playstore))
            popup.menu.add(0, 7, 7, context.getString(R.string.search_mode_youtube))
        }
        
        popup.setOnMenuItemClickListener { item ->
            val mode = when (item.itemId) {
                0 -> AppSearchManager.SearchMode.ALL
                1 -> AppSearchManager.SearchMode.APPS
                2 -> AppSearchManager.SearchMode.CONTACTS
                3 -> AppSearchManager.SearchMode.FILES
                4 -> AppSearchManager.SearchMode.MAPS
                5 -> {
                    // Only reachable if not in focus mode
                    if (isFocusModeActive()) {
                        AppSearchManager.SearchMode.ALL  // fallback if somehow accessed in focus mode
                    } else {
                        AppSearchManager.SearchMode.WEB
                    }
                }
                6 -> {
                    // Only reachable if not in focus mode
                    if (isFocusModeActive()) {
                        AppSearchManager.SearchMode.ALL  // fallback if somehow accessed in focus mode
                    } else {
                        AppSearchManager.SearchMode.PLAYSTORE
                    }
                }
                7 -> {
                    // Only reachable if not in focus mode
                    if (isFocusModeActive()) {
                        AppSearchManager.SearchMode.ALL  // fallback if somehow accessed in focus mode
                    } else {
                        AppSearchManager.SearchMode.YOUTUBE
                    }
                }
                else -> AppSearchManager.SearchMode.ALL
            }
            
            if (appSearchManager != null) {
                appSearchManager.setSearchMode(mode)
            }
            
            // Update button icon based on selected mode
            updateButtonIcon(mode)
            
            true
        }
        popup.show()
    }
    
    /**
     * Updates the search type button icon based on the selected search mode.
     */
    private fun updateButtonIcon(mode: AppSearchManager.SearchMode) {
        val iconRes = when (mode) {
            AppSearchManager.SearchMode.APPS -> R.drawable.ic_apps_grid_icon
            AppSearchManager.SearchMode.CONTACTS -> R.drawable.ic_person
            AppSearchManager.SearchMode.FILES -> R.drawable.ic_file
            AppSearchManager.SearchMode.MAPS -> R.drawable.ic_maps
            AppSearchManager.SearchMode.WEB -> R.drawable.ic_browser
            AppSearchManager.SearchMode.PLAYSTORE -> R.drawable.ic_play_store
            AppSearchManager.SearchMode.YOUTUBE -> R.drawable.ic_youtube
            else -> R.drawable.ic_search
        }
        searchTypeButton.setImageResource(iconRes)
    }
    
    /**
     * Creates a PopupMenu with translucent background.
     */
    private fun createTranslucentPopupMenu(anchor: View): PopupMenu {
        // Create with custom theme wrapper that ensures translucent style
        val wrapper = ContextThemeWrapper(context, R.style.Theme_Launch)
        val popup = PopupMenu(wrapper, anchor)
        
        // Apply translucent background using multiple approaches
        try {
            // Approach 1: Direct field access
            val popupField = popup.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popup)
            val cls = menuPopupHelper.javaClass
            
            // Set the translucent background drawable
            val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.menu_background)
            
            // Try multiple methods to set the background
            val methodsToTry = listOf(
                "setBackgroundDrawable" to arrayOf(android.graphics.drawable.Drawable::class.java),
                "setDropDownBackgroundDrawable" to arrayOf(android.graphics.drawable.Drawable::class.java)
            )
            
            var backgroundSet = false
            for ((methodName, paramTypes) in methodsToTry) {
                try {
                    val method = cls.getMethod(methodName, *paramTypes)
                    method.invoke(menuPopupHelper, backgroundDrawable)
                    backgroundSet = true
                    break
                } catch (_: Exception) {
                    // Continue trying other methods
                }
            }
            
            // Approach 2: If direct methods fail, try accessing the popup window
            if (!backgroundSet) {
                try {
                    val popupWindowField = cls.getDeclaredField("mPopup")
                    popupWindowField.isAccessible = true
                    val popupWindow = popupWindowField.get(menuPopupHelper)
                    val popupWindowClass = popupWindow?.javaClass
                    
                    popupWindowClass?.getMethod("setBackgroundDrawable", android.graphics.drawable.Drawable::class.java)
                        ?.invoke(popupWindow, backgroundDrawable)
                } catch (_: Exception) {}
            }
            
            // Approach 3: Set style if available
            try {
                cls.getMethod("setPopupStyle", Int::class.java)
                    .invoke(menuPopupHelper, R.style.PopupMenuStyle)
            } catch (_: Exception) {}
            
        } catch (_: Exception) {
            // If all reflection approaches fail, at least the theme wrapper should provide some styling
            // The popup will still use the theme's popupMenuStyle attribute
        }
        
        return popup
    }
}