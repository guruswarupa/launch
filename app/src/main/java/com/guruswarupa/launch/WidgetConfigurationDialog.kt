package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WidgetConfigurationDialog(
    private val context: Context,
    private val widgetConfigManager: WidgetConfigurationManager,
    private val onConfigurationSaved: () -> Unit
) {
    
    private var dialog: AlertDialog? = null
    private var adapter: WidgetConfigAdapter? = null
    
    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_widget_configuration, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.widgets_recycler_view)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.close_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val saveButton = dialogView.findViewById<Button>(R.id.save_button)
        
        // Load current widget configuration
        val widgets = widgetConfigManager.getWidgetOrder().toMutableList()
        
        // Setup RecyclerView
        adapter = WidgetConfigAdapter(widgets) { position, isChecked ->
            widgets[position] = widgets[position].copy(enabled = isChecked)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // Setup drag to reorder
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }
                adapter?.moveItem(fromPos, toPos)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }
            
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        
        // Create dialog
        dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Make dialog window scrollable with proper sizing
        dialog?.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.75).toInt()
        )
        
        // Setup button listeners
        closeButton.setOnClickListener {
            dialog?.dismiss()
        }
        
        cancelButton.setOnClickListener {
            dialog?.dismiss()
        }
        
        saveButton.setOnClickListener {
            val updatedWidgets = adapter?.getWidgets() ?: widgets
            widgetConfigManager.saveWidgetOrder(updatedWidgets)
            onConfigurationSaved()
            dialog?.dismiss()
        }
        
        dialog?.show()
    }
    
    fun dismiss() {
        dialog?.dismiss()
    }
}
