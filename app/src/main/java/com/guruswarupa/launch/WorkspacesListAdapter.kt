package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WorkspacesListAdapter(
    private val workspaces: List<Pair<String, Set<String>>>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<WorkspacesListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val workspaceName: TextView = view.findViewById(R.id.workspace_name)
        val appCount: TextView = view.findViewById(R.id.app_count)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workspace_onboarding, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workspace = workspaces[position]
        holder.workspaceName.text = workspace.first
        holder.appCount.text = "${workspace.second.size} apps"
        
        holder.deleteButton.setOnClickListener {
            onDelete(position)
        }
    }

    override fun getItemCount(): Int = workspaces.size
}
