package com.guruswarupa.launch.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R

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
        val context = holder.itemView.context
        holder.workspaceName.text = workspace.first
        holder.appCount.text = context.getString(R.string.workspace_app_count_format, workspace.second.size)
        
        holder.deleteButton.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onDelete(currentPos)
            }
        }
    }

    override fun getItemCount(): Int = workspaces.size
}
