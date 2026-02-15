package com.guruswarupa.launch

import android.app.ActivityManager
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Calendar
import java.util.concurrent.Executors

class AppTimerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_timer_prefs", Context.MODE_PRIVATE)
    private var currentTimer: CountDownTimer? = null
    private var currentPackageName: String? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentDialog: AlertDialog? = null // Track if dialog is showing
    private val usageStatsManager = AppUsageStatsManager(context)

    companion object {
        const val TIMER_1_MIN = 60000L
        const val TIMER_5_MIN = 300000L
        const val TIMER_10_MIN = 600000L
        const val NO_TIMER = 0L
        
        const val PREF_DAILY_LIMIT_PREFIX = "daily_limit_"
    }

    fun isAppOverDailyLimit(packageName: String): Boolean {
        val limit = getDailyLimit(packageName)
        if (limit == NO_TIMER) return false
        
        val usage = usageStatsManager.getAppUsageTime(packageName)
        return usage >= limit
    }

    fun getDailyLimit(packageName: String): Long {
        return prefs.getLong(PREF_DAILY_LIMIT_PREFIX + packageName, NO_TIMER)
    }

    fun setDailyLimit(packageName: String, limit: Long) {
        prefs.edit { putLong(PREF_DAILY_LIMIT_PREFIX + packageName, limit) }
        usageStatsManager.invalidateCache() // Invalidate cache so UI reflects change immediately
    }

    fun applyGrayscaleIfOverLimit(packageName: String, imageView: ImageView) {
        if (isAppOverDailyLimit(packageName)) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(matrix)
            imageView.colorFilter = filter
            imageView.alpha = 0.5f
        } else {
            // Explicitly clear all filters and reset alpha
            imageView.colorFilter = null
            imageView.clearColorFilter()
            imageView.alpha = 1.0f
            // Some versions of Android might need the drawable's filter cleared too if it was mutated
            imageView.drawable?.clearColorFilter()
        }
    }

    fun showDailyLimitDialog(appName: String, packageName: String, onLimitSet: () -> Unit) {
        if (currentDialog?.isShowing == true) {
            return
        }

        val options = arrayOf("15 minutes", "30 minutes", "1 hour", "2 hours", "Custom", "No limit")
        val currentLimit = getDailyLimit(packageName)
        val currentLimitStr = if (currentLimit > 0) " (Current: ${usageStatsManager.formatUsageTime(currentLimit)})" else ""

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Daily limit for $appName$currentLimitStr")
            .setItems(options) { _, which ->
                currentDialog = null
                when (which) {
                    0 -> { setDailyLimit(packageName, 15 * 60000L); onLimitSet() }
                    1 -> { setDailyLimit(packageName, 30 * 60000L); onLimitSet() }
                    2 -> { setDailyLimit(packageName, 60 * 60000L); onLimitSet() }
                    3 -> { setDailyLimit(packageName, 120 * 60000L); onLimitSet() }
                    4 -> showCustomDailyLimitDialog(packageName, onLimitSet)
                    5 -> { setDailyLimit(packageName, NO_TIMER); onLimitSet() }
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                currentDialog = null
                d.dismiss()
            }
            .setOnDismissListener {
                currentDialog = null
            }
            .show()
        
        currentDialog = dialog
        fixDialogItemsTextColor(dialog)
    }

    private fun showCustomDailyLimitDialog(packageName: String, onLimitSet: () -> Unit) {
        val input = EditText(context)
        input.hint = "Enter minutes"
        val textColor = ContextCompat.getColor(context, R.color.text)
        input.setTextColor(textColor)
        input.setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Custom Daily Limit")
            .setMessage("Enter time in minutes:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutesString = input.text.toString()
                    if (minutesString.isNotEmpty()) {
                        val minutes = minutesString.toInt()
                        if (minutes > 0) {
                            setDailyLimit(packageName, minutes * 60000L)
                            onLimitSet()
                        } else {
                            Toast.makeText(context, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: NumberFormatException) {
                    Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun showTimerDialog(appName: String, onTimerSet: (Long) -> Unit) {
        // Prevent multiple dialogs from opening - if one is already showing, ignore
        if (currentDialog?.isShowing == true) {
            return
        }

        val options = arrayOf("1 minute", "5 minutes", "10 minutes", "Custom", "No timer")

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Set timer for $appName")
            .setItems(options) { _, which ->
                currentDialog = null // Clear reference when dialog is dismissed via item selection
                when (which) {
                    0 -> onTimerSet(TIMER_1_MIN)
                    1 -> onTimerSet(TIMER_5_MIN)
                    2 -> onTimerSet(TIMER_10_MIN)
                    3 -> showCustomTimerDialog(onTimerSet)
                    4 -> onTimerSet(NO_TIMER)
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                currentDialog = null // Clear reference when dialog is dismissed
                d.dismiss()
            }
            .setOnDismissListener {
                currentDialog = null // Clear reference when dialog is dismissed
            }
            .show()
        
        currentDialog = dialog // Store reference to track if showing
        
        // Fix dialog items text color to theme-aware color
        fixDialogItemsTextColor(dialog)
    }
    
    private fun fixDialogItemsTextColor(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(context, R.color.text)
            val listView = dialog.listView
            if (listView != null) {
                // Post on main thread after dialog is shown to ensure views are inflated
                listView.post {
                    try {
                        // Fix all existing items
                        for (i in 0 until listView.childCount) {
                            val itemView = listView.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(textColor)
                            } else if (itemView is ViewGroup) {
                                // Search for TextView in the view hierarchy
                                findTextViewsAndSetColor(itemView, textColor)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
                
                // Also try after a small delay in case items load asynchronously
                mainHandler.postDelayed({
                    try {
                        for (i in 0 until listView.childCount) {
                            val itemView = listView.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(textColor)
                            } else if (itemView is ViewGroup) {
                                findTextViewsAndSetColor(itemView, textColor)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
                }, 100)
            }
        } catch (_: Exception) {
            // If we can't fix it, that's okay - at least try
        }
    }
    
    private fun findTextViewsAndSetColor(viewGroup: ViewGroup, color: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(color)
            } else if (child is ViewGroup) {
                findTextViewsAndSetColor(child, color)
            }
        }
    }

    private fun showCustomTimerDialog(onTimerSet: (Long) -> Unit) {
        val input = EditText(context)
        input.hint = "Enter minutes"
        val textColor = ContextCompat.getColor(context, R.color.text)
        input.setTextColor(textColor)
        input.setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Custom Timer")
            .setMessage("Enter time in minutes:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutesString = input.text.toString()
                    if (minutesString.isNotEmpty()) {
                        val minutes = minutesString.toInt()
                        if (minutes > 0) {
                            onTimerSet(minutes * 60000L)
                        } else {
                            Toast.makeText(context, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: NumberFormatException) {
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
        prefs.edit { putLong("timer_remaining_$packageName", duration) }

        currentTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Write to SharedPreferences in background to prevent UI freezing
                backgroundExecutor.execute {
                    prefs.edit { putLong("timer_remaining_$packageName", millisUntilFinished) }
                }
            }

            override fun onFinish() {
                // Clean up timer state
                backgroundExecutor.execute {
                    prefs.edit { remove("timer_remaining_$packageName") }
                }
                
                // Return to launcher and close app on main thread
                mainHandler.post {
                    Toast.makeText(context, "Time's up! Closing app and returning to launcher", Toast.LENGTH_SHORT).show()
                    returnToLauncher(packageName)
                    currentTimer = null
                    currentPackageName = null
                }
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
        } catch (_: Exception) {
            Toast.makeText(context, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }

    fun returnToLauncher(packageName: String) {
        try {
            // Get the launcher's package name (this app)
            val launcherPackageName = context.packageName
            
            // Try to directly launch the launcher's MainActivity
            try {
                val launcherIntent = context.packageManager.getLaunchIntentForPackage(launcherPackageName)
                if (launcherIntent != null) {
                    launcherIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                          Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                                          Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                                          Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                          Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(launcherIntent)
                    
                    // Wait for launcher to come to foreground, then kill the app
                    mainHandler.postDelayed({
                        forceCloseApp(packageName)
                    }, 500)
                    return
                }
            } catch (_: Exception) {
                // Fall through to HOME intent
            }
            
            // Fallback: Use HOME intent
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                          Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                          Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                          Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
            
            // Wait for launcher to come to foreground, then kill the app
            mainHandler.postDelayed({
                forceCloseApp(packageName)
            }, 500)
        } catch (_: Exception) {
            // Last resort: simpler HOME intent
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                mainHandler.postDelayed({
                    forceCloseApp(packageName)
                }, 500)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
    
    private fun forceCloseApp(packageName: String) {
        // Run in background thread to avoid blocking
        backgroundExecutor.execute {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@execute
                
                // Kill all background processes for this package
                // Since we've already brought launcher to foreground, the app should be in background
                activityManager.killBackgroundProcesses(packageName)
                
                // Try multiple times to ensure it's killed
                mainHandler.postDelayed({
                    try {
                        activityManager.killBackgroundProcesses(packageName)
                    } catch (_: Exception) {
                        // Ignore
                    }
                }, 300)
                
                // Try to remove from recent tasks if possible
                try {
                    // Try using getRecentTasks (may require special permission)
                    @Suppress("DEPRECATION")
                    val recentTasks = activityManager.getRecentTasks(50, ActivityManager.RECENT_WITH_EXCLUDED)
                    for (taskInfo in recentTasks) {
                        val baseIntent = taskInfo.baseIntent
                        val component = baseIntent.component
                        if (component != null && component.packageName == packageName) {
                            // Try to remove this task
                            try {
                                // Kill processes first
                                activityManager.killBackgroundProcesses(packageName)
                            } catch (_: Exception) {
                                // Ignore - we don't have permission to manipulate tasks
                            }
                        }
                    }
                } catch (_: SecurityException) {
                    // getRecentTasks requires special permission, that's okay
                    // killBackgroundProcesses should still work
                } catch (_: Exception) {
                    // Ignore other exceptions
                }

            } catch (_: SecurityException) {
                // If we don't have permission, at least the app is in background
                // Android will manage it
                try {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    activityManager?.killBackgroundProcesses(packageName)
                } catch (_: Exception) {
                    // Ignore
                }
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    fun cancelTimer() {
        currentTimer?.cancel()
        currentTimer = null
        currentPackageName?.let { packageName ->
            // Clean up in background
            backgroundExecutor.execute {
                prefs.edit { remove("timer_remaining_$packageName") }
            }
        }
        currentPackageName = null
    }

    fun cleanup() {
        backgroundExecutor.shutdown()
    }
}
