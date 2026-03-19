package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.AppAdapter
import java.util.concurrent.Executor

/**
 * Handles focus mode UI logic and app filtering.
 * Extracted from MainActivity to reduce complexity.
 */
class FocusModeApplier(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val appListManager: AppListManager,
    private val appDockManager: AppDockManager,
    private val searchBox: EditText,
    private val voiceSearchButton: ImageButton,
    private val searchContainer: LinearLayout,
    private var adapter: AppAdapter?,
    private val fullAppList: MutableList<android.content.pm.ResolveInfo>,
    private val appList: MutableList<android.content.pm.ResolveInfo>,
    private val onUpdateAppSearchManager: () -> Unit,
    private val onUpdateFastScrollerVisibility: () -> Unit
) {
    /**
     * Safely execute a task on the background executor.
     */
    private fun safeExecute(task: Runnable): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        try {
            backgroundExecutor.execute(task)
            return true
        } catch (e: Exception) {
            Log.w("FocusModeApplier", "Task rejected by executor", e)
            return false
        }
    }

    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }

    /**
     * Applies focus mode by filtering apps.
     */
    fun applyFocusMode(isFocusMode: Boolean) {
        // Don't modify views if activity is finishing or stopped
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        // Run filtering/sorting in background thread
        safeExecute {
            try {
                val workspaceMode = appListManager.getWorkspaceMode()
                val filteredOrSortedApps = if (isFocusMode) {
                    // Filter out hidden apps (fast operation)
                    appListManager.filterAppsByMode(fullAppList, true, workspaceMode)
                } else {
                    // Restore all apps - use AppListManager for filtering
                    appListManager.filterAppsByMode(fullAppList, false, workspaceMode)
                }

                val finalFilteredApps = appListManager.applyFavoritesFilter(filteredOrSortedApps)
                val sortedFinalList = appListManager.sortAppsAlphabetically(finalFilteredApps)
                
                // Inject separators to keep list consistent with main loader
                val listWithSeparators = appListManager.addSeparators(sortedFinalList)

                // Update UI on main thread
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

                    // Update shared appList instance contents
                    appList.clear()
                    appList.addAll(listWithSeparators)

                    searchContainer.visibility = View.VISIBLE

                    // Also update the drawer lock state based on focus mode
                    appDockManager.lockDrawerForFocusMode(isFocusMode)

                    // Update adapter with new list
                    adapter?.updateAppList(listWithSeparators)

                    // Update search manager with new app list
                    if (!activity.isFinishing) {
                        onUpdateAppSearchManager()
                        onUpdateFastScrollerVisibility()
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusModeApplier", "Error applying focus mode", e)
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        @SuppressLint("NotifyDataSetChanged")
                        adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }
    
    private val sharedPreferences by lazy {
        activity.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
    }
}
