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
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout,
    private val packageManager: PackageManager
) {
    private val DOCK_APPS_KEY = "dock_apps"
    private lateinit var addIcon: ImageView

    init {
        ensureAddIcon()
    }

    fun openAppPicker() {
        val options = arrayOf("Add Single App", "Create Group")
        AlertDialog.Builder(context)
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

        AlertDialog.Builder(context)
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

        AlertDialog.Builder(context)
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

    private fun addAppToDock(packageName: String) {
        addAppToDockUI(packageName)
        saveDockItem("single:$packageName")
    }

    private fun addGroupToDock(packageNames: List<String>, groupName: String) {
        addGroupToDockUI(packageNames, groupName)
        saveDockItem("group:$groupName:${packageNames.joinToString(",")}")
    }

    private fun addAppToDockUI(packageName: String) {
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
                    launchApp(packageName)
                }

                setOnLongClickListener {
                    showRemoveDockAppDialog("single:$packageName")
                    true
                }
                appDock.addView(this, appDock.childCount - 1)
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

        appDock.addView(groupLayout, appDock.childCount - 1)
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

        packageNames.forEach { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appIcon = packageManager.getApplicationIcon(appInfo)

                ImageView(context).apply {
                    setImageDrawable(appIcon)
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = context.resources.getDimensionPixelSize(R.dimen.group_icon_size) * 2
                        height = context.resources.getDimensionPixelSize(R.dimen.group_icon_size) * 2
                        setMargins(8, 8, 8, 8)
                    }
                    setOnClickListener {
                        launchApp(packageName)
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

        AlertDialog.Builder(context)
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
        AlertDialog.Builder(context)
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

    private fun refreshDock() {
        appDock.removeAllViews()
        loadDockApps()
        ensureAddIcon()
    }

    fun loadDockApps() {
        val dockItems = migrateLegacyItems(sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf()) ?: mutableSetOf())

        dockItems.forEach { item ->
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
        ensureAddIcon()
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
}

fun Activity.dismissPopupWindow() {
    if (currentFocus != null && currentFocus!!.parent is PopupWindow) {
        (currentFocus!!.parent as PopupWindow).dismiss()
    }
}