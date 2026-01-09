package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WorkspaceConfigActivity : ComponentActivity() {
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var workspaceList: ListView
    private lateinit var createWorkspaceButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace_config)
        
        workspaceManager = WorkspaceManager(getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE))
        
        workspaceList = findViewById(R.id.workspace_list)
        createWorkspaceButton = findViewById(R.id.create_workspace_button)
        
        createWorkspaceButton.setOnClickListener {
            showCreateWorkspaceDialog()
        }
        
        loadWorkspaces()
    }
    
    override fun onResume() {
        super.onResume()
        loadWorkspaces()
    }
    
    private fun loadWorkspaces() {
        val workspaces = workspaceManager.getAllWorkspaces()
        val workspaceNames = workspaces.map { 
            val appCount = it.appPackageNames.size
            "${it.name} ($appCount apps)"
        }.toTypedArray()
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, workspaceNames)
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
        
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Create Workspace")
            .setView(input)
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
        
        // Get all apps
        val allAppsRaw = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
        
        // Get apps that are already in other workspaces (exclude current workspace if editing)
        val appsInOtherWorkspaces = workspaceManager.getAppsInWorkspaces(existingWorkspaceId)
        
        // Filter out apps that are already in other workspaces
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
        
        val appNames = allApps.map { 
            it.loadLabel(pm).toString()
        }.toTypedArray()
        
        val selectedApps = mutableSetOf<String>()
        if (existingWorkspaceId != null) {
            val existingWorkspace = workspaceManager.getWorkspace(existingWorkspaceId)
            selectedApps.addAll(existingWorkspace?.appPackageNames ?: emptySet())
        }
        
        val checkedItems = BooleanArray(allApps.size) { index ->
            val packageName = allApps[index].activityInfo.packageName
            selectedApps.contains(packageName)
        }
        
        val message = if (appsInOtherWorkspaces.isNotEmpty()) {
        } else {
            null
        }
        
        val dialogBuilder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Select Apps for '$workspaceName'")

        dialogBuilder
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                val packageName = allApps[which].activityInfo.packageName
                if (isChecked) {
                    selectedApps.add(packageName)
                } else {
                    selectedApps.remove(packageName)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                if (selectedApps.isEmpty()) {
                    Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                    // Re-show dialog if no apps selected
                    showAppPickerForWorkspace(workspaceName, existingWorkspaceId)
                } else {
                    try {
                        if (existingWorkspaceId != null) {
                            workspaceManager.updateWorkspace(existingWorkspaceId, workspaceName, selectedApps)
                            Toast.makeText(this, "Workspace updated", Toast.LENGTH_SHORT).show()
                        } else {
                            workspaceManager.createWorkspace(workspaceName, selectedApps)
                            Toast.makeText(this, "Workspace created with ${selectedApps.size} apps", Toast.LENGTH_SHORT).show()
                        }
                        loadWorkspaces()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Rename Workspace")
            .setView(input)
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
}
