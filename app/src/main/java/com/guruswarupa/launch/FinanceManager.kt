
package com.guruswarupa.launch

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class FinanceManager(private val context: Context, private val sharedPreferences: SharedPreferences) {

    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val currentMonth = dateFormat.format(Date())

    fun addIncome(amount: Double) {
        val currentBalance = getBalance()
        val newBalance = currentBalance + amount
        sharedPreferences.edit().putFloat("finance_balance", newBalance.toFloat()).apply()

        // Track monthly income
        val monthlyIncome = getMonthlyIncome()
        sharedPreferences.edit().putFloat("finance_income_$currentMonth", (monthlyIncome + amount).toFloat()).apply()

        // Add to transaction history
        addTransaction(amount, "income")
    }

    fun addExpense(amount: Double) {
        val currentBalance = getBalance()
        val newBalance = currentBalance - amount
        sharedPreferences.edit().putFloat("finance_balance", newBalance.toFloat()).apply()

        // Track monthly expenses
        val monthlyExpenses = getMonthlyExpenses()
        sharedPreferences.edit().putFloat("finance_expenses_$currentMonth", (monthlyExpenses + amount).toFloat()).apply()

        // Add to transaction history
        addTransaction(-amount, "expense")
    }

    fun getBalance(): Double {
        return sharedPreferences.getFloat("finance_balance", 0.0f).toDouble()
    }

    fun getMonthlyExpenses(): Double {
        return sharedPreferences.getFloat("finance_expenses_$currentMonth", 0.0f).toDouble()
    }

    fun getMonthlyIncome(): Double {
        return sharedPreferences.getFloat("finance_income_$currentMonth", 0.0f).toDouble()
    }

    fun getMonthlyNet(): Double {
        return getMonthlyIncome() - getMonthlyExpenses()
    }

     fun addTransaction(amount: Double, type: String) {
        val timestamp = System.currentTimeMillis()
        val transactionKey = "transaction_$timestamp"
        val transactionData = "$type:$amount:$timestamp"
        sharedPreferences.edit().putString(transactionKey, transactionData).apply()

        // Keep only last 100 transactions to avoid storage bloat
        cleanupOldTransactions()
    }

    private fun cleanupOldTransactions() {
        val allPrefs = sharedPreferences.all
        val transactionKeys = allPrefs.keys.filter { it.startsWith("transaction_") }

        if (transactionKeys.size > 100) {
            val sortedKeys = transactionKeys.sortedByDescending {
                it.substringAfter("transaction_").toLongOrNull() ?: 0L
            }

            // Remove oldest transactions
            sortedKeys.drop(100).forEach { key ->
                sharedPreferences.edit().remove(key).apply()
            }
        }
    }
}
