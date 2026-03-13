package com.guruswarupa.launch.managers

import android.content.pm.ResolveInfo
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
    private val backgroundExecutor: Executor,
    private val searchBox: AutoCompleteTextView
) {
    private var isGridMode: Boolean = false

    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }

    /**
     * Sets up callbacks for appListLoader.
     */
    fun setupCallbacks() {
        appListLoader.onAppListUpdated = { sortedList, filteredList, isFinal ->
            // Inject separators before updating UI.
            val listWithSeparators = appListManager.addSeparators(sortedList, isGridMode)
            updateAppListUI(listWithSeparators, filteredList, isFinal)
        }
        appListLoader.onAdapterNeedsUpdate = { isGrid ->
            this.isGridMode = isGrid
            val columns = activity.getPreferredGridColumns()
            if (recyclerView.layoutManager !is GridLayoutManager && isGrid) {
                val gridLayoutManager = GridLayoutManager(activity, columns)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (adapter?.getItemViewType(position) == AppAdapter.VIEW_TYPE_SEPARATOR) {
                            columns
                        } else {
                            1
                        }
                    }
                }
                recyclerView.layoutManager = gridLayoutManager
            } else if (recyclerView.layoutManager !is LinearLayoutManager && !isGrid) {
                recyclerView.layoutManager = LinearLayoutManager(activity)
            } else if (recyclerView.layoutManager is GridLayoutManager && isGrid) {
                val gm = recyclerView.layoutManager as GridLayoutManager
                gm.spanCount = columns
                gm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (adapter?.getItemViewType(position) == AppAdapter.VIEW_TYPE_SEPARATOR) {
                            columns
                        } else {
                            1
                        }
                    }
                }
            }
            
            if (adapter != null) {
                adapter?.updateViewMode(isGrid)
            } else {
                val newAdapter = AppAdapter(activity, appList, searchBox, isGrid, activity)
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
        adapter?.updateAppList(newAppList)
        
        recyclerView.visibility = View.VISIBLE
        
        if (isFinal) {
            activity.updateFastScrollerVisibility()
        }
        
        // 3. Update AppSearchManager with new app data.
        activity.updateAppSearchManager(newFullAppList, newAppList)
    }

    /**
     * Filters apps without reloading from package manager.
     */
    fun filterAppsWithoutReload() {
        if (fullAppList.isEmpty()) {
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
            return
        }
        
        try {
            backgroundExecutor.execute {
                try {
                    val focusMode = appListManager.getFocusMode()
                    val workspaceMode = appListManager.getWorkspaceMode()
                    
                    val currentFullList = ArrayList(fullAppList)
                    val filteredApps = appListManager.filterAndPrepareApps(currentFullList, focusMode, workspaceMode)
                    val sortedFinalList = appListManager.sortAppsAlphabetically(filteredApps)
                    
                    // Inject separators.
                    val listWithSeparators = appListManager.addSeparators(sortedFinalList, isGridMode)
                    
                    activity.runOnUiThread {
                        updateAppListUI(listWithSeparators, currentFullList, isFinal = true)
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
