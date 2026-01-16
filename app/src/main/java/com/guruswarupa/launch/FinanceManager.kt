package com.guruswarupa.launch

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class FinanceManager(private val sharedPreferences: SharedPreferences) {

    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val currentMonth = dateFormat.format(Date())
    private val BALANCE_KEY = "finance_balance"
    private val CURRENCY_KEY = "finance_currency"
    
    companion object {
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
            sharedPreferences.edit().putString(CURRENCY_KEY, currencyCode).apply()
        }
    }

    fun addIncome(amount: Double, description: String = "") {
        val currentBalance = getBalance()
        val newBalance = currentBalance + amount
        sharedPreferences.edit().putFloat("finance_balance", newBalance.toFloat()).apply()

        val monthlyIncome = getMonthlyIncome()
        sharedPreferences.edit().putFloat("finance_income_$currentMonth", (monthlyIncome + amount).toFloat()).apply()

        addTransaction(amount, "income", description)
    }

    fun addExpense(amount: Double, description: String = "") {
        val currentBalance = getBalance()
        val newBalance = currentBalance - amount
        sharedPreferences.edit().putFloat("finance_balance", newBalance.toFloat()).apply()

        val monthlyExpenses = getMonthlyExpenses()
        sharedPreferences.edit().putFloat("finance_expenses_$currentMonth", (monthlyExpenses + amount).toFloat()).apply()

        addTransaction(-amount, "expense", description)
    }

    fun getBalance(): Double {
        return try {
            sharedPreferences.getFloat(BALANCE_KEY, 0.0f).toDouble()
        } catch (e: ClassCastException) {
            // Handle case where balance was stored as Integer
            val intBalance = sharedPreferences.getInt(BALANCE_KEY, 0)
            val floatBalance = intBalance.toFloat()
            // Update to Float for future use
            sharedPreferences.edit().putFloat(BALANCE_KEY, floatBalance).apply()
            floatBalance.toDouble()
        }
    }

    fun getMonthlyExpenses(): Double {
        return try {
            sharedPreferences.getFloat("finance_expenses_$currentMonth", 0.0f).toDouble()
        } catch (e: ClassCastException) {
            val intExpenses = sharedPreferences.getInt("finance_expenses_$currentMonth", 0)
            val floatExpenses = intExpenses.toFloat()
            sharedPreferences.edit().putFloat("finance_expenses_$currentMonth", floatExpenses).apply()
            floatExpenses.toDouble()
        }
    }

    fun getMonthlyIncome(): Double {
        return try {
            sharedPreferences.getFloat("finance_income_$currentMonth", 0.0f).toDouble()
        } catch (e: ClassCastException) {
            val intIncome = sharedPreferences.getInt("finance_income_$currentMonth", 0)
            val floatIncome = intIncome.toFloat()
            sharedPreferences.edit().putFloat("finance_income_$currentMonth", floatIncome).apply()
            floatIncome.toDouble()
        }
    }

    fun addTransaction(amount: Double, type: String, description: String = "") {
        val timestamp = System.currentTimeMillis()
        val transactionKey = "transaction_$timestamp"
        val transactionData = "$type:$amount:$timestamp:$description"
        sharedPreferences.edit().putString(transactionKey, transactionData).apply()

        // Keep only last 100 transactions to avoid storage bloat
        cleanupOldTransactions()
    }

    fun getTransactionHistory(): List<Triple<String, Double, String>> {
        val allPrefs = sharedPreferences.all
        val transactions = mutableListOf<Triple<String, Double, String>>()

        allPrefs.keys.filter { it.startsWith("transaction_") }.forEach { key ->
            val transactionData = sharedPreferences.getString(key, "") ?: ""
            val parts = transactionData.split(":")
            if (parts.size >= 3) {
                val type = parts[0]
                val amount = parts[1].toDoubleOrNull() ?: 0.0
                val description = if (parts.size > 3) parts[3] else ""
                transactions.add(Triple(type, amount, description))
            }
        }

        return transactions.sortedByDescending {
            allPrefs.keys.filter { it.startsWith("transaction_") }
                .find { key ->
                    val data = sharedPreferences.getString(key, "") ?: ""
                    data.split(":").let { parts ->
                        parts.size >= 3 && parts[0] == it.first && parts[1].toDoubleOrNull() == it.second
                    }
                }?.substringAfter("transaction_")?.toLongOrNull() ?: 0L
        }
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
            val editor = sharedPreferences.edit()
            sortedKeys.drop(100).forEach { key ->
                editor.remove(key)
            }
            editor.apply() // Single apply() call instead of multiple
        }
    }
}