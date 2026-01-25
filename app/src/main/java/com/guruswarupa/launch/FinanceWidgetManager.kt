package com.guruswarupa.launch

import android.content.SharedPreferences
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Manages the finance widget: setup, transactions, and display updates
 */
class FinanceWidgetManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val financeManager: FinanceManager,
    private val balanceText: TextView,
    private val monthlySpentText: TextView,
    private val amountInput: EditText,
    private val descriptionInput: EditText
) {
    
    fun setup() {
        activity.findViewById<Button>(R.id.add_income_btn).setOnClickListener {
            addTransaction(true)
        }

        activity.findViewById<Button>(R.id.add_expense_btn).setOnClickListener {
            addTransaction(false)
        }

        // Click on balance text or card to show transaction history
        balanceText.setOnClickListener {
            showTransactionHistory()
        }
        
        // Also make the balance card clickable
        activity.findViewById<LinearLayout>(R.id.balance_card)?.setOnClickListener {
            showTransactionHistory()
        }
    }
    
    fun updateDisplay() {
        val currencySymbol = financeManager.getCurrency()
        val balance = financeManager.getBalance()
        val monthlyExpenses = financeManager.getMonthlyExpenses()
        val monthlyIncome = financeManager.getMonthlyIncome()
        val netSavings = monthlyIncome - monthlyExpenses
        
        // Format balance with 2 decimal places
        balanceText.text = String.format("%s%.2f", currencySymbol, balance)
        
        // Show net savings for the month (income - expenses) with neutral color
        val netText = if (netSavings >= 0) {
            "This Month: +$currencySymbol${String.format("%.2f", netSavings)}"
        } else {
            "This Month: -$currencySymbol${String.format("%.2f", kotlin.math.abs(netSavings))}"
        }
        monthlySpentText.text = netText
        monthlySpentText.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
    }
    
    private fun addTransaction(isIncome: Boolean) {
        val amountText = amountInput.text.toString()
        val description = descriptionInput.text.toString().trim()

        if (amountText.isNotEmpty()) {
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {
                // Use addIncome or addExpense instead of addTransaction
                if (isIncome) {
                    financeManager.addIncome(amount, description)
                } else {
                    financeManager.addExpense(amount, description)
                }

                // Clear inputs after adding transaction
                amountInput.text.clear()
                descriptionInput.text.clear()

                updateDisplay()

                val currencySymbol = financeManager.getCurrency()
                val action = if (isIncome) "Income" else "Expense"
                val message = if (description.isNotEmpty()) {
                    "$action of $currencySymbol$amount added: $description"
                } else {
                    "$action of $currencySymbol$amount added"
                }
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, "Please enter an amount", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showTransactionHistory() {
        val allPrefs = sharedPreferences.all
        val currencySymbol = financeManager.getCurrency()
        
        // Parse transactions with timestamps from SharedPreferences
        val transactionList = mutableListOf<Transaction>()
        allPrefs.keys.filter { it.startsWith("transaction_") }.forEach { key ->
            val transactionData = sharedPreferences.getString(key, "") ?: ""
            val parts = transactionData.split(":")
            if (parts.size >= 3) {
                val type = parts[0]
                val amount = parts[1].toDoubleOrNull() ?: 0.0
                val timestamp = key.substringAfter("transaction_").toLongOrNull() ?: 0L
                val description = if (parts.size > 3) parts[3] else ""
                transactionList.add(Transaction(type, amount, description, timestamp))
            }
        }
        
        // Sort by timestamp descending (newest first)
        val sortedTransactions = transactionList.sortedByDescending { it.timestamp }

        if (sortedTransactions.isEmpty()) {
            Toast.makeText(activity, "No transactions found", Toast.LENGTH_SHORT).show()
            return
        }

        // Create custom dialog
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_transaction_history, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.transaction_recycler_view)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)
        
        recyclerView.layoutManager = LinearLayoutManager(activity)
        val adapter = TransactionAdapter(sortedTransactions, currencySymbol)
        recyclerView.adapter = adapter

        val dialog = android.app.AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
