package com.guruswarupa.launch

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
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
                Toast.makeText(context, "Time's up! Closing app and returning to launcher", Toast.LENGTH_SHORT).show()
                prefs.edit().remove("timer_remaining_$packageName").apply()
                returnToLauncher(packageName)
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

    private fun returnToLauncher(packageName: String) {
        try {
            // First, bring launcher to foreground with flags that will push other apps to background
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                          Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                          Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            context.startActivity(intent)
            
            // Small delay to ensure launcher is in foreground, then force-close the app
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                forceCloseApp(packageName)
            }, 500)
        } catch (e: Exception) {
            // Fallback: just try to open launcher
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
    
    private fun forceCloseApp(packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Kill all background processes for this package
            // This will terminate the app once it's moved to background
            activityManager.killBackgroundProcesses(packageName)
            
            // On Android 7.0+, we can also try to finish recent tasks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    // Try to remove the app from recent tasks
                    val recentTasks = activityManager.getAppTasks()
                    for (task in recentTasks) {
                        val taskInfo = task.taskInfo
                        if (taskInfo != null && taskInfo.baseActivity?.packageName == packageName) {
                            task.finishAndRemoveTask()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore if we can't access recent tasks
                }
            }
        } catch (e: SecurityException) {
            // If we don't have permission, the app will still be moved to background
            // when launcher comes to foreground, and Android will eventually kill it
        } catch (e: Exception) {
            // Handle any other exceptions
        }
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