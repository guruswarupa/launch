package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.*

class FinanceManager(private val sharedPreferences: SharedPreferences) {

    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val currentMonth = dateFormat.format(Date())
    
    companion object {
        private const val BALANCE_KEY = "finance_balance"
        private const val CURRENCY_KEY = "finance_currency"

        val SUPPORTED_CURRENCIES = mapOf(
            "USD" to "$",
            "EUR" to "€",
            "GBP" to "£",
            "JPY" to "¥",
            "INR" to "₹",
            "CNY" to "¥",
            "CAD" to "C$",
            "AUD" to "A$",
            "CHF" to "CHF",
            "SEK" to "kr",
            "NOK" to "kr",
            "DKK" to "kr",
            "PLN" to "zł",
            "RUB" to "₽",
            "BRL" to "R$",
            "KRW" to "₩",
            "MXN" to "$",
            "SGD" to "S$",
            "HKD" to "HK$",
            "NZD" to "NZ$"
        )
    }
    
    fun getCurrency(): String {
        val currencyCode = sharedPreferences.getString(CURRENCY_KEY, "USD") ?: "USD"
        return SUPPORTED_CURRENCIES[currencyCode] ?: "$"
    }
    
    fun getCurrencyCode(): String {
        return sharedPreferences.getString(CURRENCY_KEY, "USD") ?: "USD"
    }
    
    fun setCurrency(currencyCode: String) {
        if (SUPPORTED_CURRENCIES.containsKey(currencyCode)) {
            sharedPreferences.edit { putString(CURRENCY_KEY, currencyCode) }
        }
    }

    fun addIncome(amount: Double, description: String = "") {
        val currentBalance = getBalance()
        val newBalance = currentBalance + amount
        sharedPreferences.edit { putFloat(BALANCE_KEY, newBalance.toFloat()) }

        val monthlyIncome = getMonthlyIncome()
        sharedPreferences.edit { putFloat("finance_income_$currentMonth", (monthlyIncome + amount).toFloat()) }

        addTransaction(amount, "income", description)
    }

    fun addExpense(amount: Double, description: String = "") {
        val currentBalance = getBalance()
        val newBalance = currentBalance - amount
        sharedPreferences.edit { putFloat(BALANCE_KEY, newBalance.toFloat()) }

        val monthlyExpenses = getMonthlyExpenses()
        sharedPreferences.edit { putFloat("finance_expenses_$currentMonth", (monthlyExpenses + amount).toFloat()) }

        addTransaction(-amount, "expense", description)
    }

    fun getBalance(): Double {
        return try {
            sharedPreferences.getFloat(BALANCE_KEY, 0.0f).toDouble()
        } catch (_: ClassCastException) {
            // Handle case where balance was stored as Integer
            val intBalance = sharedPreferences.getInt(BALANCE_KEY, 0)
            val floatBalance = intBalance.toFloat()
            // Update to Float for future use
            sharedPreferences.edit { putFloat(BALANCE_KEY, floatBalance) }
            floatBalance.toDouble()
        }
    }

    fun getMonthlyExpenses(): Double {
        return try {
            sharedPreferences.getFloat("finance_expenses_$currentMonth", 0.0f).toDouble()
        } catch (_: ClassCastException) {
            val intExpenses = sharedPreferences.getInt("finance_expenses_$currentMonth", 0)
            val floatExpenses = intExpenses.toFloat()
            sharedPreferences.edit { putFloat("finance_expenses_$currentMonth", floatExpenses) }
            floatExpenses.toDouble()
        }
    }

    fun getMonthlyIncome(): Double {
        return try {
            sharedPreferences.getFloat("finance_income_$currentMonth", 0.0f).toDouble()
        } catch (_: ClassCastException) {
            val intIncome = sharedPreferences.getInt("finance_income_$currentMonth", 0)
            val floatIncome = intIncome.toFloat()
            sharedPreferences.edit { putFloat("finance_income_$currentMonth", floatIncome) }
            floatIncome.toDouble()
        }
    }

    fun addTransaction(amount: Double, type: String, description: String = "") {
        val timestamp = System.currentTimeMillis()
        val transactionKey = "transaction_$timestamp"
        val transactionData = "$type:$amount:$timestamp:$description"
        sharedPreferences.edit { putString(transactionKey, transactionData) }

        // Keep only last 100 transactions to avoid storage bloat
        cleanupOldTransactions()
    }

    fun deleteTransaction(timestamp: Long) {
        val key = "transaction_$timestamp"
        val transactionData = sharedPreferences.getString(key, "") ?: ""
        
        if (transactionData.isNotEmpty()) {
            val parts = transactionData.split(":")
            if (parts.size >= 2) {
                val type = parts[0]
                val amount = parts[1].toDoubleOrNull() ?: 0.0
                
                // Adjust balance
                val currentBalance = getBalance()
                sharedPreferences.edit { putFloat(BALANCE_KEY, (currentBalance - amount).toFloat()) }
                
                // Adjust monthly records
                val date = Date(timestamp)
                val monthStr = dateFormat.format(date)
                if (type == "income") {
                    val monthlyIncome = sharedPreferences.getFloat("finance_income_$monthStr", 0.0f).toDouble()
                    sharedPreferences.edit { putFloat("finance_income_$monthStr", (monthlyIncome - amount).toFloat()) }
                } else {
                    val monthlyExpenses = sharedPreferences.getFloat("finance_expenses_$monthStr", 0.0f).toDouble()
                    // amount is negative for expenses, so we subtract it (which adds the positive absolute value back)
                    sharedPreferences.edit { putFloat("finance_expenses_$monthStr", (monthlyExpenses - kotlin.math.abs(amount)).toFloat()) }
                }
                
                sharedPreferences.edit { remove(key) }
            }
        }
    }

    @Suppress("unused")
    fun getTransactionHistory(): List<Triple<String, Double, String>> {
        val allPrefs = sharedPreferences.all
        val transactionEntries = mutableListOf<Pair<Long, Triple<String, Double, String>>>()

        allPrefs.forEach { (key, value) ->
            if (key.startsWith("transaction_") && value is String) {
                val parts = value.split(":")
                if (parts.size >= 3) {
                    val type = parts[0]
                    val amount = parts[1].toDoubleOrNull() ?: 0.0
                    val timestamp = parts[2].toLongOrNull() ?: 0L
                    val description = if (parts.size > 3) parts[3] else ""
                    transactionEntries.add(timestamp to Triple(type, amount, description))
                }
            }
        }

        return transactionEntries
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun cleanupOldTransactions() {
        // Use getAll() only once and filter efficiently
        val allPrefs = sharedPreferences.all
        val transactionKeys = allPrefs.keys.filter { it.startsWith("transaction_") }

        if (transactionKeys.size > 100) {
            // Sort keys by timestamp (descending) - more efficient parsing
            val sortedKeys = transactionKeys.sortedByDescending { key ->
                key.substringAfter("transaction_").toLongOrNull() ?: 0L
            }

            // Batch remove operations for better performance
            sharedPreferences.edit {
                sortedKeys.drop(100).forEach { key ->
                    remove(key)
                }
            }
        }
    }

    fun resetData() {
        val allPrefs = sharedPreferences.all
        sharedPreferences.edit {
            for (key in allPrefs.keys) {
                if (key.startsWith("finance_") || key.startsWith("transaction_")) {
                    remove(key)
                }
            }
        }
    }
}
