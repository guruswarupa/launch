package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Locale

class AppDockManager(
    activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout,
    private val packageManager: PackageManager,
    private val favoriteAppManager: FavoriteAppManager
) {
    private val context: Context = activity
    private val focusModeKey = "focus_mode_enabled"
    private val focusModeHiddenAppsKey = "focus_mode_hidden_apps"
    private val focusModeEndTimeKey = "focus_mode_end_time"
    private lateinit var focusModeToggle: ImageView
    private lateinit var focusTimerText: TextView
    private lateinit var workspaceToggle: ImageView
    private lateinit var apkShareButton: ImageView
    private lateinit var favoriteToggle: ImageView
    private var isFocusMode: Boolean = false
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    private val workspaceManager: WorkspaceManager

    init {
        isFocusMode = sharedPreferences.getBoolean(focusModeKey, false)
        workspaceManager = WorkspaceManager(sharedPreferences)

        // Handle focus mode expiry
        val focusEndTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
        if (isFocusMode && focusEndTime > 0 && System.currentTimeMillis() > focusEndTime) {
            // Just update the state without calling methods that depend on MainActivity
            isFocusMode = false
            sharedPreferences.edit {
                putBoolean(focusModeKey, false)
                remove(focusModeEndTimeKey)
            }
        }

        // Initialize dock with all components
        refreshDock()
        
        // Ensure workspace toggle is added
        ensureWorkspaceToggle()

        // Check if focus mode timer should be restored
        if (isFocusMode) {
            val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
            if (endTime > System.currentTimeMillis()) {
                startTimerDisplay()
                startFocusModeTimer(endTime)
            } else {
                // Timer expired, disable focus mode
                disableFocusMode()
            }
        }
    }

    private fun ensureApkShareButton() {
        if (appDock.findViewWithTag<ImageView>("apk_share_button") == null) {
            apkShareButton = ImageView(context).apply {
                tag = "apk_share_button"
                setImageResource(R.drawable.ic_share)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(12, 12, 12, 12)
                }
                setOnClickListener { showApkShareDialog() }
                setOnLongClickListener {
                    Toast.makeText(context, "Share Apps/Files", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            // Find the position after focus mode container
            var insertIndex = appDock.childCount
            for (i in 0 until appDock.childCount) {
                val child = appDock.getChildAt(i)
                if (child.tag == "focus_mode_container") {
                    insertIndex = i + 1
                    break
                }
            }
            appDock.addView(apkShareButton, insertIndex)
        }
    }



    private fun refreshDock() {
        appDock.removeAllViews()
        ensureSettingsButton()       // 1. Settings
        ensureFavoriteToggle()      // 2. Favorite/All apps toggle (after settings)
        ensureWorkspaceToggle()      // 3. Workspace toggle
        ensureFocusModeToggle()      // 4. Focus mode
        ensureApkShareButton()       // 5. Share APK icon
        updateDockVisibility()
    }
    
    fun refreshWorkspaceToggle() {
        if (::workspaceToggle.isInitialized) {
            updateWorkspaceIcon()
        }
    }

    fun refreshFavoriteToggle() {
        if (::favoriteToggle.isInitialized) {
            updateFavoriteToggleIcon()
        }
    }


    private fun ensureFocusModeToggle() {
        if (appDock.findViewWithTag<ImageView>("focus_mode_toggle") == null) {
            // Create container for focus mode toggle and timer
            val focusContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                )
                tag = "focus_mode_container"
            }

            focusModeToggle = ImageView(context).apply {
                tag = "focus_mode_toggle"
                setImageResource(if (isFocusMode) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(12,12,12,12)
                }
                setOnClickListener { toggleFocusMode() }
                setOnLongClickListener {
                    if (!isFocusMode) {
                        showFocusModeSettings()
                    } else {
                        Toast.makeText(context, "Focus mode settings unavailable during focus mode", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }

            focusTimerText = TextView(context).apply {
                tag = "focus_timer_text"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginStart = 8
                }
                gravity = android.view.Gravity.CENTER_VERTICAL
                visibility = if (isFocusMode) View.VISIBLE else View.GONE
            }

            focusContainer.addView(focusModeToggle)
            focusContainer.addView(focusTimerText)

            // Find the position after workspace toggle
            var insertIndex = 3
            for (i in 0 until appDock.childCount) {
                val child = appDock.getChildAt(i)
                if (child.tag == "workspace_toggle") {
                    insertIndex = i + 1
                    break
                }
            }
            appDock.addView(focusContainer, insertIndex)

            if (isFocusMode) {
                startTimerDisplay()
            }
        }
    }
    
    private fun ensureWorkspaceToggle() {
        if (appDock.findViewWithTag<ImageView>("workspace_toggle") == null) {
            val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
            workspaceToggle = ImageView(context).apply {
                tag = "workspace_toggle"
                setImageResource(if (isWorkspaceActive) R.drawable.ic_workspace_active else R.drawable.ic_workspace_inactive)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(12, 12, 12, 12)
                    marginStart = 8
                }
                setOnClickListener { toggleWorkspace() }
                setOnLongClickListener {
                    showWorkspaceSettings()
                    true
                }
            }
            // Find the position after favorite toggle (or after settings if no favorite toggle)
            var insertIndex = 2
            for (i in 0 until appDock.childCount) {
                val child = appDock.getChildAt(i)
                if (child.tag == "favorite_toggle") {
                    insertIndex = i + 1
                    break
                } else if (child.tag == "settings_button") {
                    insertIndex = i + 1
                }
            }
            appDock.addView(workspaceToggle, insertIndex)
        }
    }
    
    private fun toggleWorkspace() {
        // Always show workspace selector to allow switching or turning off
        showWorkspaceSelector()
    }
    
    private fun showWorkspaceSelector() {
        val workspaces = workspaceManager.getAllWorkspaces()
        if (workspaces.isEmpty()) {
            Toast.makeText(context, "No workspaces available. create one.", Toast.LENGTH_SHORT).show()
            showWorkspaceSettings()
            return
        }
        
        val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
        
        // Build workspace names list
        val workspaceNames = workspaces.map { it.name }.toMutableList()
        
        // Add "Turn Off" option if a workspace is currently active
        if (isWorkspaceActive) {
            workspaceNames.add("Turn Off")
        }
        
        val itemsArray = workspaceNames.toTypedArray()
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(if (isWorkspaceActive) "Switch Workspace" else "Select Workspace")
            .setItems(itemsArray) { _, which ->
                // Check if "Turn Off" was selected (last item when workspace is active)
                if (isWorkspaceActive && which == itemsArray.size - 1) {
                    // Turn off workspace mode
                    workspaceManager.setActiveWorkspaceId(null)
                    updateWorkspaceIcon()
                    refreshAppsForWorkspace()
                    Toast.makeText(context, "Workspace mode disabled", Toast.LENGTH_SHORT).show()
                } else {
                    // Select a workspace
                    val selectedWorkspace = workspaces[which]
                    workspaceManager.setActiveWorkspaceId(selectedWorkspace.id)
                    updateWorkspaceIcon()
                    refreshAppsForWorkspace()
                    Toast.makeText(context, "Workspace '${selectedWorkspace.name}' activated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showWorkspaceSettings() {
        val intent = Intent(context, WorkspaceConfigActivity::class.java)
        context.startActivity(intent)
    }
    
    private fun updateWorkspaceIcon() {
        val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
        workspaceToggle.setImageResource(
            if (isWorkspaceActive) R.drawable.ic_workspace_active else R.drawable.ic_workspace_inactive
        )
    }
    
    private fun refreshAppsForWorkspace() {
        (context as? MainActivity)?.refreshAppsForWorkspace()
    }
    
    fun isWorkspaceModeActive(): Boolean {
        return workspaceManager.isWorkspaceModeActive()
    }
    
    fun isAppInActiveWorkspace(packageName: String): Boolean {
        return workspaceManager.isAppInActiveWorkspace(packageName)
    }



    private fun ensureSettingsButton() {
        if (appDock.findViewWithTag<ImageView>("settings_button") == null) {
            val settingsButton = ImageView(context).apply {
                tag = "settings_button"
                setImageResource(R.drawable.ic_settings)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(12, 12, 12, 12)
                    marginStart = 8
                }
                setOnClickListener {
                    val intent = Intent(context, SettingsActivity::class.java)
                    context.startActivity(intent)
                }
                setOnLongClickListener {
                    Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            // Add at the beginning (index 0)
            appDock.addView(settingsButton, 0)
        }
    }

    private fun ensureFavoriteToggle() {
        if (appDock.findViewWithTag<ImageView>("favorite_toggle") == null) {
            favoriteToggle = ImageView(context).apply {
                tag = "favorite_toggle"
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(12, 12, 12, 12)
                    marginStart = 8
                }
                setOnClickListener { toggleFavoriteMode() }
                setOnLongClickListener {
                    val mode = if (favoriteAppManager.isShowAllAppsMode()) "All Apps" else "Favorites"
                    Toast.makeText(context, "Show: $mode", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            updateFavoriteToggleIcon()
            
            // Find the index after settings button
            var insertIndex = 2
            for (i in 0 until appDock.childCount) {
                val child = appDock.getChildAt(i)
                if (child.tag == "settings_button") {
                    insertIndex = i + 1
                    break
                }
            }
            appDock.addView(favoriteToggle, insertIndex)
        }
    }

    private fun updateFavoriteToggleIcon() {
        if (!::favoriteToggle.isInitialized) {
            return
        }
        
        val favorites = favoriteAppManager.getFavoriteApps()
        if (favorites.isEmpty()) {
            // Hide toggle if no favorites
            favoriteToggle.visibility = View.GONE
            return
        }
        
        favoriteToggle.visibility = View.VISIBLE
        val isShowAllMode = favoriteAppManager.isShowAllAppsMode()
        // Show star if favorites are shown (clicking will switch to all apps)
        // Show grid if all apps are shown (clicking will switch to favorites)
        favoriteToggle.setImageResource(
            if (isShowAllMode) R.drawable.ic_apps_grid else R.drawable.ic_star
        )
    }

    private fun toggleFavoriteMode() {
        val favorites = favoriteAppManager.getFavoriteApps()
        if (favorites.isEmpty()) {
            Toast.makeText(context, "No favorite apps set", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentMode = favoriteAppManager.isShowAllAppsMode()
        val newMode = !currentMode
        favoriteAppManager.setShowAllAppsMode(newMode)
        
        // Update MainActivity's isShowAllAppsMode variable
        (context as? MainActivity)?.let { activity ->
            activity.isShowAllAppsMode = newMode
        }
        
        updateFavoriteToggleIcon()
        
        // Optimize: Filter existing list instead of reloading everything
        (context as? MainActivity)?.filterAppsWithoutReload()
        
        val modeText = if (newMode) "All Apps" else "Favorites"
        Toast.makeText(context, "Showing: $modeText", Toast.LENGTH_SHORT).show()
    }

    private fun saveFocusMode() {
        sharedPreferences.edit { putBoolean(focusModeKey, isFocusMode) }
    }

    private fun toggleFocusMode() {
        if (isFocusMode) {
            // Check if timer has expired
            val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime < endTime) {
                val remainingMinutes = (endTime - currentTime) / (1000 * 60)
                Toast.makeText(context, "Focus mode active for $remainingMinutes more minutes", Toast.LENGTH_LONG).show()
            } else {
                // Timer expired, allow switching to normal mode
                disableFocusMode()
            }
        } else {
            // Show duration picker dialog
            showFocusModeDurationPicker()
        }
    }

    private fun showFocusModeDurationPicker() {
        val durations = arrayOf("15 minutes", "30 minutes", "1 hour", "2 hours", "4 hours", "Custom")
        val durationValues = arrayOf(15, 30, 60, 120, 240, -1) // -1 for custom

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Select Focus Mode Duration")
            .setItems(durations) { _, which ->
                if (durationValues[which] == -1) {
                    showCustomDurationDialog()
                } else {
                    enableFocusMode(durationValues[which])
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomDurationDialog() {
        val input = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter minutes (1-480)"
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Custom Duration")
            .setMessage("Enter duration in minutes:")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes in 1..480) {
                    enableFocusMode(minutes)
                } else {
                    Toast.makeText(context, "Please enter a valid duration (1-480 minutes)", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enableFocusMode(durationMinutes: Int) {
        isFocusMode = true
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        saveFocusMode()
        sharedPreferences.edit { putLong(focusModeEndTimeKey, endTime) }

        updateFocusModeIcon()
        updateDockVisibility()
        refreshAppsForFocusMode()
        startTimerDisplay()

        Toast.makeText(context, "Focus mode enabled for $durationMinutes minutes", Toast.LENGTH_LONG).show()

        // Start timer to automatically disable focus mode
        startFocusModeTimer(endTime)
    }

    private fun disableFocusMode() {
        isFocusMode = false
        saveFocusMode()
        sharedPreferences.edit { remove(focusModeEndTimeKey) }

        updateFocusModeIcon()
        updateDockVisibility()
        refreshAppsForFocusMode()
        stopTimerDisplay()

        Toast.makeText(context, "Focus mode disabled", Toast.LENGTH_SHORT).show()
    }

    private fun startFocusModeTimer(endTime: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkTimer = object : Runnable {
            override fun run() {
                if (isFocusMode && System.currentTimeMillis() >= endTime) {
                    disableFocusMode()
                } else if (isFocusMode) {
                    handler.postDelayed(this, 60000) // Check every minute
                }
            }
        }
        handler.postDelayed(checkTimer, 60000)
    }

    private fun updateFocusModeIcon() {
        focusModeToggle.setImageResource(
            if (isFocusMode) R.drawable.ic_focus_mode
            else R.drawable.ic_normal_mode
        )
    }

    private fun refreshAppsForFocusMode() {
        (context as? MainActivity)?.refreshAppsForFocusMode()
    }

    private fun updateDockVisibility() {
        // Hide/show all dock items except the focus mode container, workspace toggle and restart button
        for (i in 0 until appDock.childCount) {
            val child = appDock.getChildAt(i)
            when (child.tag) {
                "focus_mode_container", "workspace_toggle", "favorite_toggle" -> {
                    child.visibility = View.VISIBLE
                }
                "add_icon", "apk_share_button" -> {
                    child.visibility = if (isFocusMode) View.GONE else View.VISIBLE
                }
                else -> {
                    // Hide other dock items when in focus mode
                    // But keep settings button visible
                    if (child.tag == "settings_button") {
                        child.visibility = View.VISIBLE
                    } else {
                        child.visibility = if (isFocusMode) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun startTimerDisplay() {
        stopTimerDisplay() // Stop any existing timer

        focusTimerText.visibility = View.VISIBLE
        timerHandler = android.os.Handler(android.os.Looper.getMainLooper())

        timerRunnable = object : Runnable {
            override fun run() {
                if (isFocusMode) {
                    val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
                    val currentTime = System.currentTimeMillis()

                    if (endTime > currentTime) {
                        val remainingTime = endTime - currentTime
                        val minutes = (remainingTime / (1000 * 60)).toInt()
                        val seconds = ((remainingTime % (1000 * 60)) / 1000).toInt()

                        focusTimerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        timerHandler?.postDelayed(this, 1000)
                    } else {
                        focusTimerText.text = context.getString(R.string.timer_zero)
                    }
                }
            }
        }

        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimerDisplay() {
        timerHandler?.removeCallbacks(timerRunnable ?: return)
        timerHandler = null
        timerRunnable = null
        focusTimerText.visibility = View.GONE
    }

    private fun showFocusModeSettings() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
        val sortedApps = apps.filter { it.activityInfo.name != "com.guruswarupa.launch.MainActivity" }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val appNames = sortedApps.map { it.loadLabel(packageManager).toString() }
        val appPackageNames = sortedApps.map { it.activityInfo.packageName }

        val hiddenApps = getHiddenAppsInFocusMode()
        val checkedItems = BooleanArray(appNames.size) { index ->
            hiddenApps.contains(appPackageNames[index])
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Focus Mode — Select apps to hide")
            .setMultiChoiceItems(appNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                val packageName = appPackageNames[which]
                if (isChecked) {
                    addAppToHiddenList(packageName)
                } else {
                    removeAppFromHiddenList(packageName)
                }
            }
            .setPositiveButton("Done") { _, _ ->
                if (isFocusMode) {
                    refreshAppsForFocusMode()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getHiddenAppsInFocusMode(): Set<String> {
        return sharedPreferences.getStringSet(focusModeHiddenAppsKey, mutableSetOf()) ?: mutableSetOf()
    }

    private fun addAppToHiddenList(packageName: String) {
        val hiddenApps = getHiddenAppsInFocusMode().toMutableSet()
        hiddenApps.add(packageName)
        sharedPreferences.edit { putStringSet(focusModeHiddenAppsKey, hiddenApps) }
    }

    private fun removeAppFromHiddenList(packageName: String) {
        val hiddenApps = getHiddenAppsInFocusMode().toMutableSet()
        hiddenApps.remove(packageName)
        sharedPreferences.edit { putStringSet(focusModeHiddenAppsKey, hiddenApps) }
    }

    fun isAppHiddenInFocusMode(packageName: String): Boolean {
        return if (isFocusMode) {
            getHiddenAppsInFocusMode().contains(packageName)
        } else {
            false
        }
    }

    fun getCurrentMode(): Boolean {
        return isFocusMode
    }

    // APK sharing functionality moved to ApkShareManager
    private val shareManager = ShareManager(context)

    private fun showApkShareDialog() {
        shareManager.showApkSharingDialog()
    }
}
