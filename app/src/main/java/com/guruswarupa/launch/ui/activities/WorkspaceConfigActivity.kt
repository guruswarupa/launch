package com.guruswarupa.launch.ui.activities

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.managers.WorkspaceManager
import java.util.concurrent.Executors
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.Workspace
import com.guruswarupa.launch.ui.adapters.WorkspacesAppsAdapter
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.setDialogInputView
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import android.graphics.Color
import com.guruswarupa.launch.models.Constants

class WorkspaceConfigActivity : ComponentActivity() {
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var workspaceList: ListView
    private lateinit var createWorkspaceButton: Button
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var workspacesContainer: LinearLayout
    
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        
        setContentView(R.layout.activity_workspace_config)
        
        workspaceManager = WorkspaceManager(prefs)
        
        workspaceList = findViewById(R.id.workspace_list)
        createWorkspaceButton = findViewById(R.id.create_workspace_button)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        applyBackgroundTranslucency()
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        workspacesContainer = findViewById(R.id.workspaces_container)
        
        
        applyThemeAndWallpaper()
        
        createWorkspaceButton.setOnClickListener {
            showCreateWorkspaceDialog()
        }
        
        loadWorkspaces()
    }
    
    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val color = Color.argb(alpha, 0, 0, 0)
        themeOverlay.setBackgroundColor(color)
    }
    
    private fun applyThemeAndWallpaper() {
        
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)
        
        applyBackgroundTranslucency()
        
        workspacesContainer.setBackgroundResource(R.drawable.widget_background)
        
        val textColor = ContextCompat.getColor(this, R.color.white)
        val subTextColor = Color.parseColor("#B0B0B0")
        
        titleText.setTextColor(textColor)
        subtitleText.setTextColor(subTextColor)
        createWorkspaceButton.setTextColor(textColor)
        
        createWorkspaceButton.setBackgroundResource(R.drawable.settings_card_background)
    }
    
    override fun onResume() {
        super.onResume()
        loadWorkspaces()
        applyThemeAndWallpaper()
    }
    
    private fun loadWorkspaces() {
        val workspaces = workspaceManager.getAllWorkspaces()
        
        // Only show user-created workspaces (work profile is now separate)
        val allWorkspaces = workspaces
        
        val workspaceNames = allWorkspaces.map { 
            val appCount = it.appPackageNames.size
            "${it.name} ($appCount apps)"
        }.toTypedArray()
        
        
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, workspaceNames) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(android.R.id.text1)
                text.setTextColor(Color.WHITE)
                return view
            }
        }
        workspaceList.adapter = adapter
        
        workspaceList.setOnItemClickListener { _, _, position, _ ->
            val workspace = workspaces[position]
            showWorkspaceEditor(workspace)
        }
        
        workspaceList.setOnItemLongClickListener { _, _, position, _ ->
            val workspace = workspaces[position]
            showDeleteWorkspaceDialog(workspace)
            true
        }
    }
    
    private fun showCreateWorkspaceDialog() {
        val input = EditText(this)
        input.hint = "Workspace name"
        DialogStyler.styleInput(this, input)
        
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Create Workspace")
            .setDialogInputView(this, input)
            .setPositiveButton("Create") { _, _ ->
                val workspaceName = input.text.toString().trim()
                if (workspaceName.isNotEmpty()) {
                    showAppPickerForWorkspace(workspaceName, null)
                } else {
                    Toast.makeText(this, "Please enter a workspace name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAppPickerForWorkspace(workspaceName: String, existingWorkspaceId: String?) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        
        
        val allAppsRaw = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
        
        
        val appsInOtherWorkspaces = workspaceManager.getAppsInWorkspaces(existingWorkspaceId)
        
        
        val allApps = allAppsRaw.filter { app ->
            val packageName = app.activityInfo.packageName
            !appsInOtherWorkspaces.contains(packageName)
        }.sortedBy { it.loadLabel(pm).toString().lowercase() }
        
        if (allApps.isEmpty()) {
            if (appsInOtherWorkspaces.isNotEmpty()) {
                Toast.makeText(this, "All apps are already assigned to other workspaces", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        val selectedApps = mutableSetOf<String>()
        if (existingWorkspaceId != null) {
            val existingWorkspace = workspaceManager.getWorkspace(existingWorkspaceId)
            selectedApps.addAll(existingWorkspace?.appPackageNames ?: emptySet())
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_workspace_app_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.app_picker_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = WorkspacesAppsAdapter(allApps, selectedApps) { packageName, isChecked ->
            if (isChecked) {
                selectedApps.add(packageName)
            } else {
                selectedApps.remove(packageName)
            }
        }

        val positiveLabel = if (existingWorkspaceId != null) "Save" else "Create"
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Select Apps for '$workspaceName'")
            .setView(dialogView)
            .setPositiveButton(positiveLabel, null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selectedApps.isEmpty()) {
                    Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    if (existingWorkspaceId != null) {
                        workspaceManager.updateWorkspace(existingWorkspaceId, workspaceName, selectedApps)
                        Toast.makeText(this, "Workspace updated", Toast.LENGTH_SHORT).show()
                    } else {
                        workspaceManager.createWorkspace(workspaceName, selectedApps)
                        Toast.makeText(this, "Workspace created with ${selectedApps.size} apps", Toast.LENGTH_SHORT).show()
                    }
                    loadWorkspaces()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }
    
    private fun showWorkspaceEditor(workspace: Workspace) {
        val options = arrayOf("Edit Apps", "Rename", "Activate")
        
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(workspace.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppPickerForWorkspace(workspace.name, workspace.id)
                    1 -> showRenameWorkspaceDialog(workspace)
                    2 -> {
                        workspaceManager.setActiveWorkspaceId(workspace.id)
                        Toast.makeText(this, "Workspace '${workspace.name}' activated", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showRenameWorkspaceDialog(workspace: Workspace) {
        val input = EditText(this)
        input.setText(workspace.name)
        input.hint = "Workspace name"
        DialogStyler.styleInput(this, input)
        
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Rename Workspace")
            .setDialogInputView(this, input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    workspaceManager.updateWorkspace(workspace.id, newName, workspace.appPackageNames)
                    Toast.makeText(this, "Workspace renamed", Toast.LENGTH_SHORT).show()
                    loadWorkspaces()
                } else {
                    Toast.makeText(this, "Please enter a workspace name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteWorkspaceDialog(workspace: Workspace) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Delete Workspace")
            .setMessage("Are you sure you want to delete '${workspace.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                workspaceManager.deleteWorkspace(workspace.id)
                Toast.makeText(this, "Workspace deleted", Toast.LENGTH_SHORT).show()
                loadWorkspaces()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}
