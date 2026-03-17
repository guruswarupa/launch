package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.FocusModeConfigActivity
import com.guruswarupa.launch.ui.activities.SettingsActivity
import com.guruswarupa.launch.ui.activities.WorkspaceConfigActivity
import com.guruswarupa.launch.ui.activities.EncryptedVaultActivity
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView
import java.util.Locale
import kotlin.math.abs

class AppDockManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout
) {
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
    private val pomodoroManager: PomodoroManager
    private var isFocusMode: Boolean = false
    private val res = context.resources
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    private val workspaceManager: WorkspaceManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        isFocusMode = sharedPreferences.getBoolean(focusModeKey, false)
        workspaceManager = WorkspaceManager(sharedPreferences)
        
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
                if (sharedPreferences.getBoolean(focusModeDndEnabledKey, false)) {
                    updateDndState(true)
                }
            } else {
                // Timer expired, disable focus mode
                disableFocusMode()
            }
        }
    }

    private fun ensureVaultButton() {
        // Vault is now an app in the drawer
    }

    private fun openVault() {
        val intent = Intent(context, EncryptedVaultActivity::class.java)
        context.startActivity(intent)
    }

    private fun refreshDock() {
        appDock.removeAllViews()
        ensureWorkspaceToggle()      // 1. Workspace toggle
        ensureFocusModeToggle()      // 2. Focus mode
        updateDockVisibility()
    }
    
    fun updateDockIcons() {
        // Update all dock icons to match current theme
        if (::focusModeToggle.isInitialized) {
            updateFocusModeIcon()
        }
        if (::workspaceToggle.isInitialized) {
            updateWorkspaceIcon()
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
            // Use same translucent black as widgets (#80000000)
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

            // Find the position after workspace toggle
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
    
    private fun toggleWorkspace() {
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
        val nextIndex = (currentIndex + 1) % workspaces.size
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
                        setColor(Color.parseColor("#80000000")) // Translucent black (50% alpha) to match widgets
                        setStroke(2, Color.parseColor("#8FBCBB")) // Keep Nord7 stroke
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
                setColor(Color.parseColor("#80000000")) // Translucent black (50% alpha) to match widgets
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
                    setColor(Color.parseColor("#80000000")) // Translucent black (50% alpha) to match widgets
                    setStroke(2, Color.parseColor("#5E81AC")) // Keep Nord10 stroke
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
