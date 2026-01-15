package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CalculatorHistoryAdapter(
    private val historyItems: MutableList<CalculatorHistoryItem>,
    private val onItemClick: (CalculatorHistoryItem) -> Unit
) : RecyclerView.Adapter<CalculatorHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val expressionText: TextView = view.findViewById(R.id.history_expression)
        val resultText: TextView = view.findViewById(R.id.history_result)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calculator_history_item, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyItems[position]
        holder.expressionText.text = item.expression
        holder.resultText.text = "= ${item.result}"
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = historyItems.size

    fun addItem(item: CalculatorHistoryItem) {
        historyItems.add(0, item) // Add to beginning
        notifyItemInserted(0)
        // Limit history to 50 items
        if (historyItems.size > 50) {
            val lastIndex = historyItems.size - 1
            historyItems.removeAt(lastIndex)
            notifyItemRemoved(lastIndex)
        }
    }

    fun clearHistory() {
        val size = historyItems.size
        historyItems.clear()
        notifyItemRangeRemoved(0, size)
    }
}
