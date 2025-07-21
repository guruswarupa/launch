package com.guruswarupa.launch

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AppDockManager(
    activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout,
    private val packageManager: PackageManager,
    private val appLockManager: AppLockManager
) {
    private val context: Context = activity
    private val DOCK_APPS_KEY = "dock_apps"
    private val FOCUS_MODE_KEY = "focus_mode_enabled"
    private val FOCUS_MODE_HIDDEN_APPS_KEY = "focus_mode_hidden_apps"
    private val FOCUS_MODE_END_TIME_KEY = "focus_mode_end_time"
    private val POWER_SAVER_MODE_KEY = "power_saver_mode_enabled"

    private lateinit var addIcon: ImageView
    private lateinit var focusModeToggle: ImageView
    private lateinit var focusTimerText: TextView
    private lateinit var restartButton: ImageView
    private lateinit var apkShareButton: ImageView
    private lateinit var powerSaverToggle: ImageView
    private var isFocusMode: Boolean = false
    private var isPowerSaverMode: Boolean = false
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null

    init {
        isFocusMode = sharedPreferences.getBoolean(FOCUS_MODE_KEY, false)
        isPowerSaverMode = sharedPreferences.getBoolean(POWER_SAVER_MODE_KEY, false)

        if (isPowerSaverMode) {
            (context as MainActivity).applyPowerSaverMode(true)
        }

        // Handle focus mode expiry
        val focusEndTime = sharedPreferences.getLong(FOCUS_MODE_END_TIME_KEY, 0)
        if (isFocusMode && focusEndTime > 0 && System.currentTimeMillis() > focusEndTime) {
            // Just update the state without calling methods that depend on MainActivity
            isFocusMode = false
            sharedPreferences.edit()
                .putBoolean(FOCUS_MODE_KEY, false)
                .remove(FOCUS_MODE_END_TIME_KEY)
                .apply()
        }

        // Initialize dock with all components
        refreshDock()

        // Check if focus mode timer should be restored
        if (isFocusMode) {
            val endTime = sharedPreferences.getLong(FOCUS_MODE_END_TIME_KEY, 0)
            if (endTime > System.currentTimeMillis()) {
                startTimerDisplay()
                startFocusModeTimer(endTime)
            } else {
                // Timer expired, disable focus mode
                disableFocusMode()
            }
        }
    }

    fun openAppPicker() {
        val options = arrayOf("Add Single App", "Create Group")
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Add to Dock")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSingleAppPicker()
                    1 -> openMultiAppPickerForGroup()
                }
            }
            .show()
    }

    private fun openSingleAppPicker() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
        val sortedApps = apps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val appNames = sortedApps.map { it.loadLabel(packageManager).toString() }
        val appPackageNames = sortedApps.map { it.activityInfo.packageName }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Choose an app")
            .setItems(appNames.toTypedArray()) { _, which ->
                addAppToDock(appPackageNames[which])
            }
            .show()
    }

    private fun openMultiAppPickerForGroup() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(intent, 0)
        val sortedApps = apps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val appNames = sortedApps.map { it.loadLabel(packageManager).toString() }
        val appPackageNames = sortedApps.map { it.activityInfo.packageName }

        val selectedPackages = mutableListOf<String>()
        val checkedItems = BooleanArray(appNames.size) { false }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Select apps for group")
            .setMultiChoiceItems(appNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedPackages.add(appPackageNames[which])
                } else {
                    selectedPackages.remove(appPackageNames[which])
                }
            }
            .setPositiveButton("Create Group") { _, _ ->
                if (selectedPackages.size >= 2) {
                    showGroupNameDialog(selectedPackages)
                } else {
                    Toast.makeText(context, "Select at least 2 apps", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    internal fun addAppToDock(packageName: String) {
        saveDockItem("single:$packageName")
        refreshDock()
    }

    private fun addGroupToDock(packageNames: List<String>, groupName: String) {
        saveDockItem("group:$groupName:${packageNames.joinToString(",")}")
        refreshDock()
    }

    fun addAppToDockUI(packageName: String) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appIcon = packageManager.getApplicationIcon(appInfo)

            ImageView(context).apply {
                setImageDrawable(appIcon)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setMargins(16, 0, 16, 0)
                }

                setOnClickListener {
                    launchAppWithLockCheck(packageName)
                }

                setOnLongClickListener {
                    showRemoveDockAppDialog("single:$packageName")
                    true
                }
                // Insert app before the add icon
                val addIconIndex = (0 until appDock.childCount).firstOrNull {
                    appDock.getChildAt(it).tag == "add_icon"
                } ?: appDock.childCount
                appDock.addView(this, addIconIndex)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addGroupToDockUI(packageNames: List<String>, groupName: String) {
        val squircleSize = context.resources.getDimensionPixelSize(R.dimen.squircle_size)
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.group_icon_size)

        val groupLayout = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(squircleSize, squircleSize).apply {
                setMargins(16, 0, 16, 0)
            }
            background = createSquircleBackground()
            clipToOutline = true
        }

        // Icon container
        val iconContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(squircleSize, squircleSize)
        }

        // Add up to 4 app icons
        packageNames.take(4).forEachIndexed { index, packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appIcon = packageManager.getApplicationIcon(appInfo)

                ImageView(context).apply {
                    setImageDrawable(appIcon)
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = when(index) {
                            0 -> Gravity.TOP or Gravity.START
                            1 -> Gravity.TOP or Gravity.END
                            2 -> Gravity.BOTTOM or Gravity.START
                            else -> Gravity.BOTTOM or Gravity.END
                        }
                        val margin = squircleSize / 4
                        setMargins(margin, margin, margin, margin)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    iconContainer.addView(this)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Handle error
            }
        }

        groupLayout.addView(iconContainer)

        // Group name overlay
        TextView(context).apply {
            text = groupName
            setTextColor(Color.WHITE)
            textSize = 10f
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            groupLayout.addView(this)
        }

        groupLayout.setOnClickListener {
            showGroupPopup(groupLayout, packageNames)
        }

        groupLayout.setOnLongClickListener {
            showRemoveDockAppDialog("group:$groupName:${packageNames.joinToString(",")}")
            true
        }

        // Insert group before the add icon
        val addIconIndex = (0 until appDock.childCount).firstOrNull {
            appDock.getChildAt(it).tag == "add_icon"
        } ?: appDock.childCount
        appDock.addView(groupLayout, addIconIndex)
    }

    private fun createSquircleBackground(): Drawable {
        val radius = context.resources.getDimension(R.dimen.squircle_corner_radius)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.argb(150, 255, 255, 255))
            setStroke(2, Color.WHITE)
        }
    }

    private fun showGroupPopup(anchor: View, packageNames: List<String>) {
        val popupView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createPopupBackground()
            elevation = 8f
        }

        val grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 3
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.group_popup_padding),
                context.resources.getDimensionPixelSize(R.dimen.group_popup_padding),
                context.resources.getDimensionPixelSize(R.dimen.group_popup_padding),
                context.resources.getDimensionPixelSize(R.dimen.group_popup_padding)
            )
        }

        val iconCache = mutableMapOf<String, Drawable>()

        packageNames.forEach { packageName ->
            try {
                val appIcon = iconCache.getOrPut(packageName) {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationIcon(appInfo)
                }

                ImageView(context).apply {
                    setImageDrawable(appIcon)
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = context.resources.getDimensionPixelSize(R.dimen.group_icon_size) * 2
                        height = context.resources.getDimensionPixelSize(R.dimen.group_icon_size) * 2
                        setMargins(8, 8, 8, 8)
                    }
                    setOnClickListener {
                        launchAppWithLockCheck(packageName)
                        (context as? Activity)?.dismissPopupWindow()
                    }
                    grid.addView(this)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Handle error
            }
        }

        popupView.addView(grid)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val x = location[0] - (popupView.width - anchor.width) / 2
        val y = location[1] - popupView.height - anchor.height / 2

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    private fun createPopupBackground(): Drawable {
        val radius = context.resources.getDimension(R.dimen.squircle_corner_radius)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
            setStroke(2, Color.LTGRAY)
        }
    }

    private fun migrateLegacyItems(items: Set<String>): Set<String> {
        return items.map { item ->
            when {
                item.startsWith("group:") -> {
                    val parts = item.split(":", limit = 3)
                    when (parts.size) {
                        2 -> "group:Group:${parts[1]}" // Migrate old groups
                        3 -> item // Correct format
                        else -> item
                    }
                }
                !item.startsWith("single:") -> "single:$item"
                else -> item
            }
        }.toSet()
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchAppWithLockCheck(packageName: String) {
        if (appLockManager.isAppLocked(packageName)) {
            appLockManager.verifyPin { isAuthenticated ->
                if (isAuthenticated) {
                    launchApp(packageName)
                }
            }
        } else {
            launchApp(packageName)
        }
    }

    private fun saveDockItem(item: String) {
        val dockItems = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        dockItems.add(item)
        sharedPreferences.edit().putStringSet(DOCK_APPS_KEY, dockItems).apply()
    }

    private fun showRemoveDockAppDialog(item: String) {
        // Options for the dialog
        val options = if (item.startsWith("group:")) {
            arrayOf("Remove", "Rename", "Modify Group")
        } else {
            arrayOf("Remove")
        }

        // Create the dialog
        MaterialAlertDialogBuilder(context, R.style.CustomDialogTheme)
            .setTitle("Manage Dock Item")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeDockItem(item)
                    1 -> if (item.startsWith("group:")) showRenameGroupDialog(item)
                    2 -> if (item.startsWith("group:")) showModifyGroupDialog(item)
                }
                refreshDock()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setBackground(ContextCompat.getDrawable(context, R.drawable.dialog_background)) // Custom background
            .show()
    }

    private fun showRenameGroupDialog(item: String) {
        if (!item.startsWith("group:")) return

        val parts = item.split(":", limit = 3)
        if (parts.size != 3) return

        val currentGroupName = parts[1]
        val packageNames = parts[2].split(',')

        val inputLayout = TextInputLayout(context).apply {
            setPadding(32, 32, 32, 0)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val editText = TextInputEditText(context).apply {
            setText(currentGroupName)
            inputLayout.addView(this)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Rename Group")
            .setView(inputLayout)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                when {
                    newName.isEmpty() -> Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    newName.contains(":") -> Toast.makeText(context, "Name cannot contain ':'", Toast.LENGTH_SHORT).show()
                    else -> {
                        removeDockItem(item)
                        addGroupToDock(packageNames, newName)
                        refreshDock()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showModifyGroupDialog(item: String) {
        if (!item.startsWith("group:")) return

        val parts = item.split(":", limit = 3)
        if (parts.size != 3) return

        val groupName = parts[1]
        val currentPackageNames = parts[2].split(',').toMutableList()

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(intent, 0)
        val sortedApps = apps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val appNames = sortedApps.map { it.loadLabel(packageManager).toString() }
        val appPackageNames = sortedApps.map { it.activityInfo.packageName }

        val checkedItems = BooleanArray(appNames.size) { false }
        appPackageNames.forEachIndexed { index, packageName ->
            if (currentPackageNames.contains(packageName)) {
                checkedItems[index] = true
            }
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Modify Group: $groupName")
            .setMultiChoiceItems(appNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                val packageName = appPackageNames[which]
                if (isChecked) {
                    currentPackageNames.add(packageName)
                } else {
                    currentPackageNames.remove(packageName)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                if (currentPackageNames.size >= 2) {
                    removeDockItem(item)
                    addGroupToDock(currentPackageNames, groupName)
                    refreshDock()
                } else {
                    Toast.makeText(context, "A group must have at least 2 apps", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupNameDialog(selectedPackages: List<String>) {
        val editText = EditText(context)
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Enter group name")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                when {
                    name.isEmpty() -> Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    name.contains(":") -> Toast.makeText(context, "Name cannot contain ':'", Toast.LENGTH_SHORT).show()
                    else -> addGroupToDock(selectedPackages, name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeDockItem(item: String) {
        val dockItems = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        dockItems.remove(item)
        sharedPreferences.edit().putStringSet(DOCK_APPS_KEY, dockItems).apply()
    }

    private fun ensureApkShareButton() {
        if (appDock.findViewWithTag<ImageView>("apk_share_button") == null) {
            apkShareButton = ImageView(context).apply {
                tag = "apk_share_button"
                setImageResource(R.drawable.ic_share) // You'll need to create this drawable
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(24, 24, 24, 24)
                }
                setOnClickListener { showApkShareDialog() }
                setOnLongClickListener {
                    Toast.makeText(context, "Share Apps/Files", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            // Insert after focus mode container
            val focusContainerIndex = (0 until appDock.childCount).firstOrNull {
                appDock.getChildAt(it).tag == "focus_mode_container"
            } ?: 0
            appDock.addView(apkShareButton, focusContainerIndex + 1)
        }
    }

    fun isPowerSaverActive(): Boolean {
        return isPowerSaverMode
    }

    private fun togglePowerSaverMode() {
        isPowerSaverMode = !isPowerSaverMode
        sharedPreferences.edit().putBoolean(POWER_SAVER_MODE_KEY, isPowerSaverMode).apply()

        // Update power saver toggle icon
        powerSaverToggle.setImageResource(
            if (isPowerSaverMode) R.drawable.ic_power_saver_on else R.drawable.ic_power_saver_off
        )

        // Apply power saver mode changes
        (context as MainActivity).applyPowerSaverMode(isPowerSaverMode)

        updateDockVisibility()
    }

    private fun refreshDock() {
        appDock.removeAllViews()
        ensureRestartButton()
        ensureFocusModeToggle()
        ensurePowerSaverToggle()
        ensureApkShareButton()
        ensureAddIcon()
        loadDockApps()
        updateDockVisibility()
    }

    fun loadDockApps() {
        val dockItems = migrateLegacyItems(sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf()) ?: mutableSetOf())

        dockItems.forEach { item ->
            // In focus mode, skip loading any dock apps or groups
            if (isFocusMode) return@forEach

            when {
                item.startsWith("single:") -> {
                    val packageName = item.substringAfter("single:")
                    addAppToDockUI(packageName)
                }
                item.startsWith("group:") -> {
                    val parts = item.split(":", limit = 3)
                    if (parts.size == 3) {
                        val groupName = parts[1]
                        val packages = parts[2].split(',')
                        addGroupToDockUI(packages, groupName)
                    }
                }
            }
        }
    }

    private fun ensureAddIcon() {
        if (appDock.findViewWithTag<ImageView>("add_icon") == null) {
            addIcon = ImageView(context).apply {
                tag = "add_icon"
                setImageResource(R.drawable.ic_add)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(24,24,24,24)
                }
                setOnClickListener { openAppPicker() }
            }
            appDock.addView(addIcon)
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
                    setPadding(24,24,24,24)
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
                setTextColor(context.resources.getColor(android.R.color.white))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginStart = 8
                }
                gravity = android.view.Gravity.CENTER_VERTICAL
                visibility = if (isFocusMode) View.VISIBLE else View.GONE
            }

            // Add settings icon
            val settingsIcon = ImageView(context).apply {
                tag = "settings_icon"
                setImageResource(R.drawable.ic_settings)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(24,24,24,24)
                    marginStart = 8
                }
                setOnClickListener {
                    val intent = Intent(context, SettingsActivity::class.java)
                    context.startActivity(intent)
                }
            }

            focusContainer.addView(settingsIcon)
            focusContainer.addView(focusModeToggle)
            focusContainer.addView(focusTimerText)

            val insertIndex = if (appDock.childCount > 1) 1 else appDock.childCount
            appDock.addView(focusContainer, insertIndex)

            if (isFocusMode) {
                startTimerDisplay()
            }
        }
    }

    private fun ensurePowerSaverToggle() {
        if (appDock.findViewWithTag<ImageView>("power_saver_toggle") == null) {
            powerSaverToggle = ImageView(context).apply {
                tag = "power_saver_toggle"
                setImageResource(if (isPowerSaverMode) R.drawable.ic_power_saver_on else R.drawable.ic_power_saver_off)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(24, 24, 24, 24)
                }
                setOnClickListener { togglePowerSaverMode() }
                setOnLongClickListener {
                    Toast.makeText(context, if (isPowerSaverMode) "Power Saver: ON" else "Power Saver: OFF", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            // Insert after focus mode container
            val focusContainerIndex = (0 until appDock.childCount).firstOrNull {
                appDock.getChildAt(it).tag == "focus_mode_container"
            } ?: 1
            appDock.addView(powerSaverToggle, focusContainerIndex + 1)
        }
    }

    private fun ensureRestartButton() {
        if (appDock.findViewWithTag<ImageView>("restart_button") == null) {
            restartButton = ImageView(context).apply {
                tag = "restart_button"
                setImageResource(R.drawable.ic_restart)
                layoutParams = LinearLayout.LayoutParams(
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size),
                    context.resources.getDimensionPixelSize(R.dimen.squircle_size)
                ).apply {
                    setPadding(24, 24, 24, 24)
                }
                setOnClickListener { restartLauncher() }
                setOnLongClickListener {
                    Toast.makeText(context, "Restart Launcher", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            appDock.addView(restartButton)
        }
    }

    private fun restartLauncher() {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to restart launcher", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFocusMode() {
        isFocusMode = sharedPreferences.getBoolean(FOCUS_MODE_KEY, false)
    }

    private fun saveFocusMode() {
        sharedPreferences.edit().putBoolean(FOCUS_MODE_KEY, isFocusMode).apply()
    }

    private fun toggleFocusMode() {
        if (isFocusMode) {
            // Check if timer has expired
            val endTime = sharedPreferences.getLong(FOCUS_MODE_END_TIME_KEY, 0)
            val currentTime = System.currentTimeMillis()

            if (currentTime < endTime) {
                val remainingMinutes = (endTime - currentTime) / (1000 * 60)
                Toast.makeText(context, "Focus mode active for ${remainingMinutes} more minutes", Toast.LENGTH_LONG).show()
                return
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
        sharedPreferences.edit().putLong(FOCUS_MODE_END_TIME_KEY, endTime).apply()

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
        sharedPreferences.edit().remove(FOCUS_MODE_END_TIME_KEY).apply()

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
            if (isFocusMode) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode
        )
    }

    private fun refreshAppsForFocusMode() {
        (context as? MainActivity)?.refreshAppsForFocusMode()
    }

    private fun updateDockVisibility() {
        // Hide/show all dock items except the focus mode container, power saver toggle and restart button
        for (i in 0 until appDock.childCount) {
            val child = appDock.getChildAt(i)
            when (child.tag) {                "focus_mode_container", "restart_button", "power_saver_toggle" -> {
                child.visibility = View.VISIBLE
            }
                "add_icon", "apk_share_button" -> {
                    child.visibility = if (isFocusMode || isPowerSaverMode) View.GONE else View.VISIBLE
                }
                else -> {
                    // Hide all dock apps and groups when in focus mode or power saver mode
                    child.visibility = if (isFocusMode || isPowerSaverMode) View.GONE else View.VISIBLE
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
                    val endTime = sharedPreferences.getLong(FOCUS_MODE_END_TIME_KEY, 0)
                    val currentTime = System.currentTimeMillis()

                    if (endTime > currentTime) {
                        val remainingTime = endTime - currentTime
                        val minutes = (remainingTime / (1000 * 60)).toInt()
                        val seconds = ((remainingTime % (1000 * 60)) / 1000).toInt()

                        focusTimerText.text = String.format("%02d:%02d", minutes, seconds)
                        timerHandler?.postDelayed(this, 1000)
                    } else {
                        focusTimerText.text = "00:00"
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
        return sharedPreferences.getStringSet(FOCUS_MODE_HIDDEN_APPS_KEY, mutableSetOf()) ?: mutableSetOf()
    }

    private fun addAppToHiddenList(packageName: String) {
        val hiddenApps = getHiddenAppsInFocusMode().toMutableSet()
        hiddenApps.add(packageName)
        sharedPreferences.edit().putStringSet(FOCUS_MODE_HIDDEN_APPS_KEY, hiddenApps).apply()
    }

    private fun removeAppFromHiddenList(packageName: String) {
        val hiddenApps = getHiddenAppsInFocusMode().toMutableSet()
        hiddenApps.remove(packageName)
        sharedPreferences.edit().putStringSet(FOCUS_MODE_HIDDEN_APPS_KEY, hiddenApps).apply()
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

fun Activity.dismissPopupWindow() {
    if (currentFocus != null && currentFocus!!.parent is PopupWindow) {
        (currentFocus!!.parent as PopupWindow).dismiss()
    }
}