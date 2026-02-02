package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class Transaction(
    val type: String,
    val amount: Double,
    val description: String,
    val timestamp: Long
)

class TransactionAdapter(
    private var transactions: MutableList<Transaction>,
    private val currencySymbol: String,
    private val onDeleteClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val typeText: TextView = itemView.findViewById(R.id.transaction_type)
        val descriptionText: TextView = itemView.findViewById(R.id.transaction_description)
        val dateText: TextView = itemView.findViewById(R.id.transaction_date)
        val amountText: TextView = itemView.findViewById(R.id.transaction_amount)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_transaction_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        
        // Set type
        val typeText = if (transaction.type == "income") "Income" else "Expense"
        holder.typeText.text = typeText
        
        // Set description or default text
        holder.descriptionText.text = if (transaction.description.isNotEmpty()) {
            transaction.description
        } else {
            if (transaction.type == "income") "Income" else "Expense"
        }
        
        // Set date and time
        val date = Date(transaction.timestamp)
        val dateStr = dateFormat.format(date)
        val timeStr = timeFormat.format(date)
        holder.dateText.text = "$dateStr • $timeStr"
        
        // Set amount with proper formatting
        val formattedAmount = String.format("%s%.2f", currencySymbol, kotlin.math.abs(transaction.amount))
        holder.amountText.text = if (transaction.type == "income") {
            "+$formattedAmount"
        } else {
            "-$formattedAmount"
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(transaction)
        }
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<Transaction>) {
        this.transactions = newTransactions.toMutableList()
        notifyDataSetChanged()
    }
}
