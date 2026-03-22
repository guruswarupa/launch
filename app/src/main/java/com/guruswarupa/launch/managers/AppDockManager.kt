package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.UserManager
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.FocusModeConfigActivity
import com.guruswarupa.launch.ui.activities.WorkspaceConfigActivity
import com.guruswarupa.launch.ui.activities.EncryptedVaultActivity
import com.guruswarupa.launch.ui.adapters.WorkspacesAppsAdapter
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView
import java.util.Locale
import kotlin.math.abs

class AppDockManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout
) {
    companion object {
        private const val TAG = "AppDockManager"
    }

    private val context: Context = activity
    private val dockIconSizePx = (20 * context.resources.displayMetrics.density).toInt()
    private val focusModeKey = "focus_mode_enabled"
    private val focusModeAllowedAppsKey = "focus_mode_allowed_apps"
    private val focusModeEndTimeKey = "focus_mode_end_time"
    private val focusModeDndEnabledKey = "focus_mode_dnd_enabled"
    private lateinit var focusModeToggle: ImageView
    private lateinit var focusTimerText: TextView
    private lateinit var workspaceToggle: ImageView
    private lateinit var workspaceNameText: TextView
    private lateinit var workProfileToggle: ImageView
    private lateinit var workProfileNameText: TextView
    private val pomodoroManager: PomodoroManager
    private var isFocusMode: Boolean = false
    private val res = context.resources
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    private val workspaceManager: WorkspaceManager
    private val workProfileManager: WorkProfileManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        isFocusMode = sharedPreferences.getBoolean(focusModeKey, false)
        workspaceManager = WorkspaceManager(sharedPreferences)
        workProfileManager = WorkProfileManager(context, sharedPreferences)
        
        val focusModeManager = FocusModeManager(context, sharedPreferences)
        pomodoroManager = PomodoroManager(context, sharedPreferences, focusModeManager)
        pomodoroManager.onTimerTick = { remainingMillis, state ->
            updatePomodoroTimerDisplay(remainingMillis, state)
        }
        pomodoroManager.onStateChanged = { _, isWorkFocus ->
            isFocusMode = isWorkFocus
            updateFocusModeIcon()
            updateDockVisibility()
            lockDrawerForFocusMode(isWorkFocus)
            refreshAppsForFocusMode()
            
            if (isWorkFocus && sharedPreferences.getBoolean(focusModeDndEnabledKey, false)) {
                updateDndState(true)
            } else if (!isWorkFocus) {
                updateDndState(false)
            }
        }
        pomodoroManager.onSessionEnded = {
            isFocusMode = false
            updateFocusModeIcon()
            stopTimerDisplay()
            updateDockVisibility()
            lockDrawerForFocusMode(false)
            refreshAppsForFocusMode()
            updateDndState(false)
        }
        
        pomodoroManager.resumeIfNeeded()

        
        val focusEndTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
        if (isFocusMode && focusEndTime > 0 && System.currentTimeMillis() > focusEndTime) {
            
            isFocusMode = false
            sharedPreferences.edit {
                putBoolean(focusModeKey, false)
                remove(focusModeEndTimeKey)
            }
        }

        
        refreshDock()
        
        
        ensureWorkspaceToggle()

        
        if (isFocusMode) {
            val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
            if (endTime > System.currentTimeMillis()) {
                startTimerDisplay()
                startFocusModeTimer(endTime)
                if (sharedPreferences.getBoolean(focusModeDndEnabledKey, false)) {
                    updateDndState(true)
                }
            } else {
                
                disableFocusMode()
            }
        }
    }

    private fun ensureVaultButton() {
        
    }

    private fun openVault() {
        val intent = Intent(context, EncryptedVaultActivity::class.java)
        context.startActivity(intent)
    }

    private fun refreshDock() {
        appDock.removeAllViews()
        ensureWorkProfileToggle()
        ensureWorkspaceToggle()      
        ensureFocusModeToggle()      
        updateDockVisibility()
    }
    
    fun updateDockIcons() {
        
        if (::focusModeToggle.isInitialized) {
            updateFocusModeIcon()
        }
        if (::workspaceToggle.isInitialized) {
            updateWorkspaceIcon()
        }
        if (::workProfileToggle.isInitialized) {
            updateWorkProfileIcon()
        }
    }
    
    fun refreshWorkspaceToggle() {
        if (::workspaceToggle.isInitialized) {
            updateWorkspaceIcon()
        }
    }

    private fun createDockItemContainer(containerTag: String): LinearLayout {
        val horizontalPadding = (28 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            tag = containerTag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (44 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = 16
            }
            background = getGlassyBackground()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            isClickable = true
            isFocusable = true
        }
    }

    private fun getGlassyBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 1000f
            
            setColor(Color.parseColor("#80000000"))
            setStroke(1, Color.parseColor("#40FFFFFF"))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureFocusModeToggle() {
        if (appDock.findViewWithTag<View>("focus_mode_container") == null) {
            val focusContainer = createDockItemContainer("focus_mode_container")

            focusModeToggle = ImageView(context).apply {
                tag = "focus_mode_toggle"
                setImageResource(if (isFocusMode) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
                layoutParams = LinearLayout.LayoutParams(dockIconSizePx, dockIconSizePx)
                isClickable = false
                isFocusable = false
            }

            focusTimerText = TextView(context).apply {
                tag = "focus_timer_text"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 12
                }
                gravity = Gravity.CENTER
                text = if (isFocusMode) "" else "Focus"
                visibility = View.VISIBLE
                isClickable = false
                isFocusable = false
            }

            focusContainer.addView(focusModeToggle)
            focusContainer.addView(focusTimerText)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    toggleFocusMode()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    if (!isFocusMode) {
                        showFocusModeSettings()
                    } else {
                        Toast.makeText(context, "Focus mode settings unavailable during focus mode", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            focusContainer.setOnTouchListener { v, event ->
                if (gestureDetector.onTouchEvent(event)) true else {
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
            }

            
            var insertIndex = 1
            for (i in 0 until appDock.childCount) {
                val child = appDock.getChildAt(i)
                if (child.tag == "workspace_container") {
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
    
    @SuppressLint("ClickableViewAccessibility")
    private fun ensureWorkspaceToggle() {
        if (appDock.findViewWithTag<View>("workspace_container") == null) {
            val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
            val workspaceContainer = createDockItemContainer("workspace_container")

            workspaceToggle = ImageView(context).apply {
                tag = "workspace_toggle"
                layoutParams = LinearLayout.LayoutParams(dockIconSizePx, dockIconSizePx)
                isClickable = false
                isFocusable = false
            }

            workspaceNameText = TextView(context).apply {
                tag = "workspace_name_text"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 12
                }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                text = if (isWorkspaceActive) (workspaceManager.getActiveWorkspace()?.name ?: "") else "Workspace"
                visibility = View.VISIBLE
                isClickable = false
                isFocusable = false
            }

            workspaceContainer.addView(workspaceToggle)
            workspaceContainer.addView(workspaceNameText)
            
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (abs(diffY) > abs(diffX)) {
                        if (abs(diffY) > 50 && abs(velocityY) > 100) {
                            if (diffY < 0) cycleWorkspaces() else turnOffWorkspace()
                            return true
                        }
                    }
                    return false
                }
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    toggleWorkspace()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    showWorkspaceSettings()
                }
            })

            workspaceContainer.setOnTouchListener { v, event ->
                if (gestureDetector.onTouchEvent(event)) true else {
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
            }
            
            appDock.addView(workspaceContainer, 0)
            updateWorkspaceIcon()
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun ensureWorkProfileToggle() {
        if (appDock.findViewWithTag<View>("work_profile_container") == null) {
            val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
            val workProfileContainer = createDockItemContainer("work_profile_container")

            workProfileToggle = ImageView(context).apply {
                tag = "work_profile_toggle"
                layoutParams = LinearLayout.LayoutParams(dockIconSizePx, dockIconSizePx)
                isClickable = false
                isFocusable = false
            }

            workProfileNameText = TextView(context).apply {
                tag = "work_profile_name_text"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 12
                }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                text = if (isWorkProfileEnabled) "Work Profile" else "Work"
                visibility = View.VISIBLE
                isClickable = false
                isFocusable = false
            }

            workProfileContainer.addView(workProfileToggle)
            workProfileContainer.addView(workProfileNameText)
            
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (abs(diffY) > abs(diffX)) {
                        if (abs(diffY) > 50 && abs(velocityY) > 100) {
                            if (diffY < 0) {
                                // Swipe up - enable work profile or activate if already enabled
                                if (!workProfileManager.isWorkProfileEnabled()) {
                                    toggleWorkProfile()
                                } else {
                                    // Work profile already enabled, just refresh
                                    updateWorkProfileIcon()
                                    refreshAppsForWorkspace()
                                    Toast.makeText(context, "Work Profile active", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Swipe down - disable work profile
                                if (workProfileManager.isWorkProfileEnabled()) {
                                    toggleWorkProfile()
                                }
                            }
                            return true
                        }
                    }
                    return false
                }
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    toggleWorkProfile()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    showWorkProfileManagementDialog()
                }
            })

            workProfileContainer.setOnTouchListener { v, event ->
                if (gestureDetector.onTouchEvent(event)) true else {
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
            }
            
            appDock.addView(workProfileContainer, 0)
            updateWorkProfileIcon()
        }
    }
    
    private fun toggleWorkspace() {
        showWorkspaceSelector()
    }
    
    private fun toggleWorkProfile() {
        val hasActualWorkProfile = workProfileManager.hasActualWorkProfile()
        
        if (workProfileManager.isWorkProfileEnabled()) {
            workProfileManager.setWorkProfileEnabled(false)
            updateWorkProfileIcon()
            activity.refreshAppsForWorkspace()
            Toast.makeText(context, "Work Profile disabled", Toast.LENGTH_SHORT).show()
        } else {
            if (!hasActualWorkProfile) {
                AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle("Create Work Profile")
                    .setMessage("No work profile found. Would you like to create one now? This will set up a separate work space on your device.")
                    .setPositiveButton("Create") { _, _ ->
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                workProfileManager.createWorkProfile(activity)
                            } else {
                                Toast.makeText(context, "Work profiles require Android 9.0 or higher", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to create work profile: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Failed to create work profile", e)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                workProfileManager.setWorkProfileEnabled(true)
                updateWorkProfileIcon()
                activity.refreshAppsForWorkspace()
                Toast.makeText(context, "Work Profile enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showWorkProfileSettings() {
        val intent = Intent(context, WorkspaceConfigActivity::class.java)
        context.startActivity(intent)
    }
    
    private fun showWorkProfileManagementDialog() {
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        if (!isWorkProfileEnabled) {
            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle("Work Profile")
                .setMessage("Create a work profile to separate your work apps from personal apps. Work profiles keep your data isolated and secure.")
                .setPositiveButton("Create Work Profile") { _, _ ->
                    startWorkProfileCreation()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            showManageWorkProfileDialog()
        }
    }
    
    private fun showManageWorkProfileDialog() {
        val options = arrayOf("Manage Apps", "Remove Work Profile")
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Work Profile")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showWorkProfileAppPicker()
                    1 -> showRemoveWorkProfileDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startWorkProfileCreation() {
        if (!workProfileManager.isWorkProfileSupported()) {
            AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle("Not Supported")
                .setMessage("Work profiles are not supported on this device. This feature requires Android 9.0 (Pie) or higher and device support for managed profiles.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                workProfileManager.createWorkProfile(activity)
            } else {
                Toast.makeText(context, "Work profiles require Android 9.0 or higher", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create work profile: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showWorkProfileAppPicker() {
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        
        val allApps = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
            .sortedWith(compareBy<ResolveInfo> { 
                val label = it.loadLabel(pm).toString().lowercase()
                label.removeSuffix(" (work)")
            }.thenBy { 
                if (it.loadLabel(pm).toString().endsWith(" (work)")) 1 else 0
            })
        
        val selectedApps = workProfileManager.getWorkProfileApps().toMutableSet()

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_workspace_app_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.app_picker_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = WorkspacesAppsAdapter(allApps, selectedApps) { packageName, isChecked ->
            if (isChecked) {
                workProfileManager.addAppToWorkProfile(packageName)
            } else {
                workProfileManager.removeAppFromWorkProfile(packageName)
            }
        }

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Select Work Profile Apps")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                updateWorkProfileIcon()
                if (workProfileManager.isWorkProfileEnabled()) {
                    refreshAppsForWorkspace()
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }
    
    private fun showRemoveWorkProfileDialog() {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Remove Work Profile")
            .setMessage("Removing the work profile will delete all work profile apps and data. The work profile itself will be removed from the device. This action cannot be undone.\n\nAre you sure you want to remove the work profile?")
            .setPositiveButton("Remove") { _, _ ->
                workProfileManager.setWorkProfileEnabled(false)
                workProfileManager.setWorkProfileApps(emptySet())
                workspaceManager.setActiveWorkspaceId(null)
                updateWorkProfileIcon()
                updateWorkspaceIcon()
                refreshAppsForWorkspace()
                Toast.makeText(context, "Work profile removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showWorkspaceSelector() {
        val workspaces = workspaceManager.getAllWorkspaces()
        if (workspaces.isEmpty()) {
            Toast.makeText(context, "No workspaces available. create one.", Toast.LENGTH_SHORT).show()
            showWorkspaceSettings()
            return
        }
        
        val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
        val workspaceNames = workspaces.map { it.name }.toMutableList()
        if (isWorkspaceActive) workspaceNames.add("Turn Off")
        
        val itemsArray = workspaceNames.toTypedArray()
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(if (isWorkspaceActive) "Switch Workspace" else "Select Workspace")
            .setItems(itemsArray) { _, which ->
                if (isWorkspaceActive && which == itemsArray.size - 1) {
                    workspaceManager.setActiveWorkspaceId(null)
                    Toast.makeText(context, "Workspace mode disabled", Toast.LENGTH_SHORT).show()
                } else {
                    val selectedWorkspace = workspaces[which]
                    workspaceManager.setActiveWorkspaceId(selectedWorkspace.id)
                    Toast.makeText(context, "Workspace '${selectedWorkspace.name}' activated", Toast.LENGTH_SHORT).show()
                }
                updateWorkspaceIcon()
                refreshAppsForWorkspace()
                scrollToTop()
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun cycleWorkspaces() {
        val workspaces = workspaceManager.getAllWorkspaces()
        
        if (workspaces.isEmpty()) {
            showWorkspaceSettings()
            return
        }
        
        val activeId = workspaceManager.getActiveWorkspaceId()
        val currentIndex = workspaces.indexOfFirst { it.id == activeId }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % workspaces.size
        val selectedWorkspace = workspaces[nextIndex]
        
        workspaceManager.setActiveWorkspaceId(selectedWorkspace.id)
        updateWorkspaceIcon()
        refreshAppsForWorkspace()
        scrollToTop()
        Toast.makeText(context, "Workspace '${selectedWorkspace.name}' activated", Toast.LENGTH_SHORT).show()
    }

    private fun turnOffWorkspace() {
        if (workspaceManager.isWorkspaceModeActive()) {
            workspaceManager.setActiveWorkspaceId(null)
            updateWorkspaceIcon()
            refreshAppsForWorkspace()
            scrollToTop()
            Toast.makeText(context, "Workspace mode disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scrollToTop() {
        if (activity.views.isRecyclerViewInitialized()) {
            activity.views.recyclerView.postDelayed({
                activity.views.recyclerView.scrollToPosition(0)
            }, 100)
        }
    }
    
    private fun showWorkspaceSettings() {
        val intent = Intent(context, WorkspaceConfigActivity::class.java)
        context.startActivity(intent)
    }

    private fun updateWorkspaceIcon() {
        if (!::workspaceToggle.isInitialized) return
        
        val isWorkspaceActive = workspaceManager.isWorkspaceModeActive()
        workspaceToggle.setImageResource(
            if (isWorkspaceActive) R.drawable.ic_workspace_active else R.drawable.ic_workspace_inactive
        )
        
        if (::workspaceNameText.isInitialized) {
            val activeWorkspace = workspaceManager.getActiveWorkspace()
            val newName = if (isWorkspaceActive) (activeWorkspace?.name ?: "") else "Workspace"
            workspaceNameText.text = newName

            val container = appDock.findViewWithTag<LinearLayout>("workspace_container")
            if (container != null) {
                if (isWorkspaceActive) {
                    val bg = GradientDrawable().apply {
                        cornerRadius = 1000f
                        setColor(Color.parseColor("#80000000")) 
                        setStroke(2, Color.parseColor("#8FBCBB")) 
                    }
                    container.background = bg
                } else {
                    container.background = getGlassyBackground()
                }
            }
        }
    }
    
    private fun updateWorkProfileIcon() {
        if (!::workProfileToggle.isInitialized) return
        
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        workProfileToggle.setImageResource(
            when {
                !isWorkProfileEnabled -> R.drawable.ic_work_inactive
                else -> R.drawable.ic_work_profile_active
            }
        )
        
        if (::workProfileNameText.isInitialized) {
            workProfileNameText.text = if (isWorkProfileEnabled) "Work Profile" else "Work"
            
            val container = appDock.findViewWithTag<LinearLayout>("work_profile_container")
            if (container != null) {
                if (isWorkProfileEnabled) {
                    val bg = GradientDrawable().apply {
                        cornerRadius = 1000f
                        setColor(Color.parseColor("#80000000")) 
                        setStroke(2, Color.parseColor("#4CAF50"))
                    }
                    container.background = bg
                } else {
                    container.background = getGlassyBackground()
                }
            }
        }
    }
    
    private fun refreshAppsForWorkspace() {
        activity.refreshAppsForWorkspace()
    }
    
    fun isWorkspaceModeActive(): Boolean {
        return workspaceManager.isWorkspaceModeActive()
    }
    
    fun isAppInActiveWorkspace(packageName: String): Boolean {
        return workspaceManager.isAppInActiveWorkspace(packageName)
    }



    private fun ensureSettingsButton() {
    }

    private fun saveFocusMode() {
        sharedPreferences.edit { putBoolean(focusModeKey, isFocusMode) }
    }

    private fun toggleFocusMode() {
        if (isFocusMode) {
            val endTime = sharedPreferences.getLong(focusModeEndTimeKey, 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime < endTime) {
                val remainingMinutes = (endTime - currentTime) / (1000 * 60)
                Toast.makeText(context, "Focus mode active for $remainingMinutes more minutes", Toast.LENGTH_LONG).show()
            } else if (pomodoroManager.isPomodoroActive()) {
                val state = pomodoroManager.getCurrentState()
                Toast.makeText(context, "Pomodoro $state session active", Toast.LENGTH_SHORT).show()
            } else {
                disableFocusMode()
            }
        } else if (pomodoroManager.isPomodoroActive()) {
            val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle(res.getString(R.string.pomodoro_stop_title))
                .setMessage(res.getString(R.string.pomodoro_stop_message))
                .setPositiveButton("Stop") { _, _ ->
                    pomodoroManager.stopPomodoro()
                }
                .setNegativeButton("Cancel", null)
                .create()
            DialogStyler.styleDialog(dialog)
            dialog.show()
        } else {
            showFocusModeDurationPicker()
        }
    }

    private fun showFocusModeDurationPicker() {
        val durations = arrayOf(res.getString(R.string.pomodoro_mode), "15 minutes", "30 minutes", "1 hour", "2 hours", "4 hours", "Custom")
        val durationValues = arrayOf(-2, 15, 30, 60, 120, 240, -1)

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Select Focus Mode Duration")
            .setItems(durations) { _, which ->
                when (durationValues[which]) {
                    -2 -> pomodoroManager.startPomodoro()
                    -1 -> showCustomDurationDialog()
                    else -> promptForDnd(durationValues[which])
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun showCustomDurationDialog() {
        val input = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter minutes (1-480)"
            DialogStyler.styleInput(context, this)
        }

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Custom Duration")
            .setMessage("Enter duration in minutes:")
            .setDialogInputView(context, input)
            .setPositiveButton("Start") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes in 1..480) {
                    promptForDnd(minutes)
                } else {
                    Toast.makeText(context, "Please enter a duration between 1-480 min", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun promptForDnd(durationMinutes: Int) {
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Enable Do Not Disturb?")
            .setMessage("Would you like to enable Do Not Disturb mode to mute notifications during this focus session?")
            .setPositiveButton("Yes") { _, _ ->
                enableFocusMode(durationMinutes, true)
            }
            .setNegativeButton("No") { _, _ ->
                enableFocusMode(durationMinutes, false)
            }
            .setNeutralButton("Cancel", null)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun enableFocusMode(durationMinutes: Int, enableDnd: Boolean) {
        if (enableDnd && !notificationManager.isNotificationPolicyAccessGranted) {
            showDndPermissionDialog()
            return
        }

        isFocusMode = true
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        saveFocusMode()
        sharedPreferences.edit { 
            putLong(focusModeEndTimeKey, endTime)
            putBoolean(focusModeDndEnabledKey, enableDnd)
        }

        val focusModeManager = FocusModeManager(context, sharedPreferences)
        focusModeManager.setFocusModeEnabled(true)

        updateFocusModeIcon()
        updateDockVisibility()
        lockDrawerForFocusMode(true)
        refreshAppsForFocusMode()
        startTimerDisplay()
        
        if (enableDnd) updateDndState(true)

        Toast.makeText(context, "Focus mode enabled for $durationMinutes minutes", Toast.LENGTH_LONG).show()
        startFocusModeTimer(endTime)
    }

    private fun updatePomodoroTimerDisplay(remainingMillis: Long, state: String) {
        if (!::focusTimerText.isInitialized) return
        
        val container = appDock.findViewWithTag<LinearLayout>("focus_mode_container")
        if (container != null) {
            val isWork = state == PomodoroManager.STATE_WORK
            val bg = GradientDrawable().apply {
                cornerRadius = 1000f
                setColor(Color.parseColor("#80000000")) 
                setStroke(2, if (isWork) Color.parseColor("#BF616A") else Color.parseColor("#A3BE8C"))
            }
            container.background = bg
        }

        focusTimerText.visibility = View.VISIBLE
        val minutes = (remainingMillis / (1000 * 60)).toInt()
        val seconds = ((remainingMillis % (1000 * 60)) / 1000).toInt()
        
        val stateLabel = if (state == PomodoroManager.STATE_WORK) res.getString(R.string.pomodoro_work) else res.getString(R.string.pomodoro_break)
        focusTimerText.text = String.format(Locale.getDefault(), "%s %02d:%02d", stateLabel, minutes, seconds)
        
        val isWorkFocus = state == PomodoroManager.STATE_WORK
        focusModeToggle.setImageResource(if (isWorkFocus) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
    }

    private fun showDndPermissionDialog() {
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("DND Access Required")
            .setMessage("Muting notifications requires Do Not Disturb access. Please grant it in the settings or start Focus Mode without DND.")
            .setPositiveButton("Grant Access") { _, _ ->
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        DialogStyler.styleDialog(dialog)
        dialog.show()
    }

    private fun updateDndState(enabled: Boolean) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        val filter = if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        if (notificationManager.currentInterruptionFilter != filter) {
            try { notificationManager.setInterruptionFilter(filter) } catch (_: Exception) {}
        }
    }

    private fun disableFocusMode() {
        isFocusMode = false
        saveFocusMode()
        
        val dndWasEnabled = sharedPreferences.getBoolean(focusModeDndEnabledKey, false)
        sharedPreferences.edit { 
            remove(focusModeEndTimeKey)
            remove(focusModeDndEnabledKey)
        }

        val focusModeManager = FocusModeManager(context, sharedPreferences)
        focusModeManager.setFocusModeEnabled(false)

        updateFocusModeIcon()
        updateDockVisibility()
        lockDrawerForFocusMode(false)
        refreshAppsForFocusMode()
        stopTimerDisplay()
        
        if (dndWasEnabled) updateDndState(false)

        Toast.makeText(context, "Focus mode disabled", Toast.LENGTH_SHORT).show()
    }

    private fun startFocusModeTimer(endTime: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkTimer = object : Runnable {
            override fun run() {
                if (isFocusMode && System.currentTimeMillis() >= endTime) {
                    disableFocusMode()
                } else if (isFocusMode) {
                    val shouldHaveDnd = sharedPreferences.getBoolean(focusModeDndEnabledKey, false)
                    if (shouldHaveDnd && notificationManager.isNotificationPolicyAccessGranted && 
                        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        updateDndState(true)
                    }
                    handler.postDelayed(this, 30000)
                }
            }
        }
        handler.postDelayed(checkTimer, 30000)
    }

    private fun updateFocusModeIcon() {
        focusModeToggle.setImageResource(if (isFocusMode) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
        
        val container = appDock.findViewWithTag<LinearLayout>("focus_mode_container")
        if (container != null) {
            if (isFocusMode) {
                val bg = GradientDrawable().apply {
                    cornerRadius = 1000f
                    setColor(Color.parseColor("#80000000")) 
                    setStroke(2, Color.parseColor("#5E81AC")) 
                }
                container.background = bg
            } else {
                container.background = getGlassyBackground()
                if (::focusTimerText.isInitialized) {
                    focusTimerText.text = "Focus"
                }
            }
        }
    }

    private fun refreshAppsForFocusMode() {
        activity.refreshAppsForFocusMode()
    }
    
    fun lockDrawerForFocusMode(lock: Boolean) {
        val mainActivity = activity
        if (lock) {
            mainActivity.setWidgetsPageLocked(true)
            mainActivity.openDefaultHomePage(animated = true)
        } else {
            mainActivity.setWidgetsPageLocked(false)
        }
    }

    private fun updateDockVisibility() {
        for (i in 0 until appDock.childCount) {
            val child = appDock.getChildAt(i)
            when (child.tag) {
                "focus_mode_container", "workspace_container" -> child.visibility = View.VISIBLE
                else -> child.visibility = if (isFocusMode) View.GONE else View.VISIBLE
            }
        }
    }

    private fun startTimerDisplay() {
        stopTimerDisplay()
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
        if (!pomodoroManager.isPomodoroActive()) {
            if (::focusTimerText.isInitialized) {
                focusTimerText.text = "Focus"
            }
        }
    }

    private fun showFocusModeSettings() {
        val intent = Intent(context, FocusModeConfigActivity::class.java)
        context.startActivity(intent)
    }

    private fun getAllowedAppsInFocusMode(): Set<String> {
        return sharedPreferences.getStringSet(focusModeAllowedAppsKey, mutableSetOf()) ?: mutableSetOf()
    }

    fun isAppHiddenInFocusMode(packageName: String): Boolean {
        return if (isFocusMode) !getAllowedAppsInFocusMode().contains(packageName) else false
    }

    fun getCurrentMode(): Boolean {
        return isFocusMode
    }
}
