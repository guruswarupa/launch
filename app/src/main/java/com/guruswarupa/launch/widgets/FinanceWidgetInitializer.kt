package com.guruswarupa.launch.widgets

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.widget.EditText
import android.widget.TextView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FinanceManager
import com.guruswarupa.launch.utils.FinanceWidgetManager

/**
 * Helper class to initialize the FinanceWidgetManager with a delay to avoid blocking the UI thread.
 */
class FinanceWidgetInitializer(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val delay: Long
) {
    private var onInitializedListener: ((FinanceWidgetManager) -> Unit)? = null

    /**
     * Sets a callback to be invoked when the FinanceWidgetManager is successfully initialized.
     */
    fun onInitialized(listener: (FinanceWidgetManager) -> Unit): FinanceWidgetInitializer {
        this.onInitializedListener = listener
        return this
    }

    /**
     * Initializes the FinanceWidgetManager after the specified delay.
     * Finds the required views, sets up the manager, and calls initial display update.
     */
    fun initialize(handler: Handler) {
        handler.postDelayed({
            val activity = context as? MainActivity ?: return@postDelayed
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed

            val financeManager = FinanceManager(sharedPreferences)
            val balanceText = activity.findViewById<TextView>(R.id.balance_text)
            val monthlySpentText = activity.findViewById<TextView>(R.id.monthly_spent_text)
            val amountInput = activity.findViewById<EditText>(R.id.amount_input)
            val descriptionInput = activity.findViewById<EditText>(R.id.description_input)

            if (balanceText != null && monthlySpentText != null &&
                amountInput != null && descriptionInput != null
            ) {
                val manager = FinanceWidgetManager(
                    activity, sharedPreferences, financeManager,
                    balanceText, monthlySpentText, amountInput, descriptionInput
                )
                manager.setup()
                manager.updateDisplay()
                onInitializedListener?.invoke(manager)
            }
        }, delay)
    }
}
