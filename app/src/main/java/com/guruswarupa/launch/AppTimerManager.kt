package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.widget.EditText
import android.widget.Toast

class AppTimerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_timer_prefs", Context.MODE_PRIVATE)
    private var currentTimer: CountDownTimer? = null
    private var currentPackageName: String? = null

    companion object {
        const val TIMER_1_MIN = 60000L
        const val TIMER_5_MIN = 300000L
        const val TIMER_10_MIN = 600000L
        const val NO_TIMER = 0L
    }

    fun showTimerDialog(packageName: String, appName: String, onTimerSet: (Long) -> Unit) {
        val options = arrayOf("1 minute", "5 minutes", "10 minutes", "Custom", "No timer")

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Set timer for $appName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onTimerSet(TIMER_1_MIN)
                    1 -> onTimerSet(TIMER_5_MIN)
                    2 -> onTimerSet(TIMER_10_MIN)
                    3 -> showCustomTimerDialog(onTimerSet)
                    4 -> onTimerSet(NO_TIMER)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showCustomTimerDialog(onTimerSet: (Long) -> Unit) {
        val input = EditText(context)
        input.hint = "Enter minutes"

        AlertDialog.Builder(context)
            .setTitle("Custom Timer")
            .setMessage("Enter time in minutes:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutes = input.text.toString().toInt()
                    if (minutes > 0) {
                        onTimerSet(minutes * 60000L)
                    } else {
                        Toast.makeText(context, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun startTimer(packageName: String, duration: Long) {
        if (duration == NO_TIMER) {
            // No timer, just launch the app
            launchApp(packageName)
            return
        }

        currentPackageName = packageName
        prefs.edit().putLong("timer_remaining_$packageName", duration).apply()

        currentTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                prefs.edit().putLong("timer_remaining_$packageName", millisUntilFinished).apply()
            }

            override fun onFinish() {
                Toast.makeText(context, "Time's up! Returning to launcher", Toast.LENGTH_SHORT).show()
                prefs.edit().remove("timer_remaining_$packageName").apply()
                returnToLauncher()
                currentTimer = null
                currentPackageName = null
            }
        }.start()

        // Launch the app
        launchApp(packageName)
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun returnToLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun cancelTimer() {
        currentTimer?.cancel()
        currentTimer = null
        currentPackageName?.let { packageName ->
            prefs.edit().remove("timer_remaining_$packageName").apply()
        }
        currentPackageName = null
    }

    fun isTimerActive(): Boolean {
        return currentTimer != null
    }

    fun getCurrentTimerPackage(): String? {
        return currentPackageName
    }
}