package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.managers.AppListManager
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
    private val adapter: AppAdapter,
    private val fullAppList: MutableList<android.content.pm.ResolveInfo>,
    private val appList: MutableList<android.content.pm.ResolveInfo>,
    private val onUpdateAppSearchManager: () -> Unit
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

                val finalFilteredApps = appListManager.applyFavoritesFilter(filteredOrSortedApps, workspaceMode)
                val sortedFinalList = appListManager.sortAppsAlphabetically(finalFilteredApps)

                // Update UI on main thread
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

                    appList.clear()
                    appList.addAll(sortedFinalList)

                    searchContainer.visibility = View.VISIBLE

                    // Also update the drawer lock state based on focus mode
                    appDockManager.lockDrawerForFocusMode(isFocusMode)

                    adapter.updateAppList(appList)

                    // Update search manager with new app list
                    if (!activity.isFinishing) {
                        onUpdateAppSearchManager()
                    }
                }
            } catch (_: Exception) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        @SuppressLint("NotifyDataSetChanged")
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }
}
