package com.guruswarupa.launch

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Workspace(
    val id: String,
    val name: String,
    val appPackageNames: Set<String>
)

class WorkspaceManager(private val sharedPreferences: SharedPreferences) {
    private val WORKSPACES_KEY = "workspaces"
    private val ACTIVE_WORKSPACE_KEY = "active_workspace_id"
    
    fun createWorkspace(name: String, appPackageNames: Set<String>): Workspace {
        val workspaceId = "workspace_${System.currentTimeMillis()}"
        val workspace = Workspace(workspaceId, name, appPackageNames)
        saveWorkspace(workspace)
        return workspace
    }
    
    fun updateWorkspace(workspaceId: String, name: String, appPackageNames: Set<String>) {
        val workspace = Workspace(workspaceId, name, appPackageNames)
        saveWorkspace(workspace)
    }
    
    fun deleteWorkspace(workspaceId: String) {
        val workspaces = getAllWorkspaces().toMutableList()
        workspaces.removeAll { it.id == workspaceId }
        saveAllWorkspaces(workspaces)
        
        // If deleted workspace was active, clear active workspace
        if (getActiveWorkspaceId() == workspaceId) {
            setActiveWorkspaceId(null)
        }
    }
    
    fun getAllWorkspaces(): List<Workspace> {
        val workspacesJson = sharedPreferences.getString(WORKSPACES_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(workspacesJson)
            (0 until jsonArray.length()).mapNotNull { index ->
                val obj = jsonArray.getJSONObject(index)
                Workspace(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    appPackageNames = obj.getJSONArray("apps").let { appsArray ->
                        (0 until appsArray.length()).map { appsArray.getString(it) }.toSet()
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getWorkspace(workspaceId: String): Workspace? {
        return getAllWorkspaces().find { it.id == workspaceId }
    }
    
    private fun saveWorkspace(workspace: Workspace) {
        val workspaces = getAllWorkspaces().toMutableList()
        val existingIndex = workspaces.indexOfFirst { it.id == workspace.id }
        
        if (existingIndex >= 0) {
            workspaces[existingIndex] = workspace
        } else {
            workspaces.add(workspace)
        }
        
        saveAllWorkspaces(workspaces)
    }
    
    private fun saveAllWorkspaces(workspaces: List<Workspace>) {
        val jsonArray = JSONArray()
        workspaces.forEach { workspace ->
            val obj = JSONObject().apply {
                put("id", workspace.id)
                put("name", workspace.name)
                val appsArray = JSONArray()
                workspace.appPackageNames.forEach { appsArray.put(it) }
                put("apps", appsArray)
            }
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString(WORKSPACES_KEY, jsonArray.toString()).apply()
    }
    
    fun setActiveWorkspaceId(workspaceId: String?) {
        if (workspaceId != null) {
            sharedPreferences.edit().putString(ACTIVE_WORKSPACE_KEY, workspaceId).apply()
        } else {
            sharedPreferences.edit().remove(ACTIVE_WORKSPACE_KEY).apply()
        }
    }
    
    fun getActiveWorkspaceId(): String? {
        return sharedPreferences.getString(ACTIVE_WORKSPACE_KEY, null)
    }
    
    fun getActiveWorkspace(): Workspace? {
        val activeId = getActiveWorkspaceId()
        return if (activeId != null) getWorkspace(activeId) else null
    }
    
    fun isWorkspaceModeActive(): Boolean {
        return getActiveWorkspaceId() != null
    }
    
    fun isAppInActiveWorkspace(packageName: String): Boolean {
        val activeWorkspace = getActiveWorkspace()
        return if (activeWorkspace != null) {
            activeWorkspace.appPackageNames.contains(packageName)
        } else {
            true // If no workspace is active, show all apps
        }
    }
    
    /**
     * Get all apps that are already assigned to any workspace
     * @param excludeWorkspaceId If provided, apps from this workspace will be excluded from the result
     */
    fun getAppsInWorkspaces(excludeWorkspaceId: String? = null): Set<String> {
        val allWorkspaces = getAllWorkspaces()
        val appsInWorkspaces = mutableSetOf<String>()
        
        allWorkspaces.forEach { workspace ->
            if (workspace.id != excludeWorkspaceId) {
                appsInWorkspaces.addAll(workspace.appPackageNames)
            }
        }
        
        return appsInWorkspaces
    }
}
