package com.guruswarupa.launch.managers

import android.content.pm.ResolveInfo
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import java.util.concurrent.Executor

/**
 * Handles updating the app list UI and filtering.
 * Extracted from MainActivity to reduce complexity.
 */
class AppListUIUpdater(
    private val activity: MainActivity,
    private val recyclerView: RecyclerView,
    private var adapter: AppAdapter?,
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val appListLoader: AppListLoader,
    private val appDockManager: AppDockManager,
    private val appListManager: AppListManager,
    private val handler: Handler,
    private val backgroundExecutor: Executor,
    private val searchBox: AutoCompleteTextView
) {

    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }

    /**
     * Sets up callbacks for appListLoader.
     */
    fun setupCallbacks() {
        appListLoader.onAppListUpdated = { sortedList, filteredList, isFinal ->
            updateAppListUI(sortedList, filteredList, isFinal)
        }
        appListLoader.onAdapterNeedsUpdate = { isGridMode ->
            if (recyclerView.layoutManager !is GridLayoutManager && isGridMode) {
                recyclerView.layoutManager = GridLayoutManager(activity, 4)
            } else if (recyclerView.layoutManager !is LinearLayoutManager && !isGridMode) {
                recyclerView.layoutManager = LinearLayoutManager(activity)
            }
            
            if (adapter != null) {
                adapter?.updateViewMode(isGridMode)
            } else {
                val newAdapter = AppAdapter(activity, appList, searchBox, isGridMode, activity)
                adapter = newAdapter
                recyclerView.adapter = newAdapter
            }
            activity.updateFastScrollerVisibility()
        }
    }

    /**
     * Updates the app list UI and AppSearchManager with new data.
     */
    fun updateAppListUI(
        newAppList: List<ResolveInfo>,
        newFullAppList: List<ResolveInfo>,
        isFinal: Boolean = false
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        
        // 1. Update full app list instance contents (needed for search)
        if (newFullAppList !== fullAppList) {
            fullAppList.clear()
            fullAppList.addAll(newFullAppList)
        }
        
        // 2. Update the adapter with the new items.
        // AppAdapter.updateAppList will handle the DiffUtil comparison and update 
        // the shared 'appList' instance contents on the UI thread.
        adapter?.updateAppList(newAppList)
        
        recyclerView.visibility = View.VISIBLE
        
        // Optimization #8: Only update fast scroller visibility on final load
        if (isFinal) {
            activity.updateFastScrollerVisibility()
        }
        
        // 3. Update AppSearchManager with new app data.
        // Pass both lists to ensure search manager has correct data for all search modes.
        activity.updateAppSearchManager(newFullAppList, newAppList)
    }

    /**
     * Filters apps without reloading from package manager.
     */
    fun filterAppsWithoutReload() {
        // Optimized: Filter existing list without reloading from package manager
        if (fullAppList.isEmpty()) {
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
            return
        }
        
        try {
            backgroundExecutor.execute {
                try {
                    val focusMode = appListManager.getFocusMode()
                    val workspaceMode = appListManager.getWorkspaceMode()
                    
                    // Take a snapshot to avoid ConcurrentModificationException
                    val currentFullList = ArrayList(fullAppList)
                    
                    // Optimization #3: Combined single-pass filter
                    val filteredApps = appListManager.filterAndPrepareApps(currentFullList, focusMode, workspaceMode)
                    
                    // Finally sort
                    val sortedFinalList = appListManager.sortAppsAlphabetically(filteredApps)
                    
                    // Update UI on main thread
                    activity.runOnUiThread {
                        updateAppListUI(sortedFinalList, currentFullList, isFinal = true)
                        appDockManager.refreshFavoriteToggle()
                    }
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Error filtering apps: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppListUIUpdater", "Task rejected by executor", e)
        }
    }

    fun refreshAppsForFocusMode() {
        appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
    }
    
    fun refreshAppsForWorkspace() {
        appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
    }
}
