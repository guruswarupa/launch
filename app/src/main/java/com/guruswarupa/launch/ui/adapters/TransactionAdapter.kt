package com.guruswarupa.launch.ui.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import com.guruswarupa.launch.R

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
        val context = holder.itemView.context
        
        // Set type
        val isIncome = transaction.type == "income"
        val typeLabel = context.getString(if (isIncome) R.string.income else R.string.expense)
        holder.typeText.text = typeLabel
        
        // Set description or default text
        holder.descriptionText.text = transaction.description.ifEmpty { typeLabel }
        
        // Set date and time
        val date = Date(transaction.timestamp)
        val dateStr = dateFormat.format(date)
        val timeStr = timeFormat.format(date)
        holder.dateText.text = context.getString(R.string.date_time_divider_format, dateStr, timeStr)
        
        // Set amount with proper formatting
        val absAmount = kotlin.math.abs(transaction.amount)
        holder.amountText.text = if (isIncome) {
            context.getString(R.string.transaction_amount_income_format, currencySymbol, absAmount)
        } else {
            context.getString(R.string.transaction_amount_expense_format, currencySymbol, absAmount)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(transaction)
        }
    }

    override fun getItemCount(): Int = transactions.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newTransactions: List<Transaction>) {
        this.transactions = newTransactions.toMutableList()
        notifyDataSetChanged()
    }
}
