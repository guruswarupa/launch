package com.guruswarupa.launch

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class AppTimerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_timer_prefs", Context.MODE_PRIVATE)
    private var currentTimer: CountDownTimer? = null
    private var currentPackageName: String? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val TIMER_1_MIN = 60000L
        const val TIMER_5_MIN = 300000L
        const val TIMER_10_MIN = 600000L
        const val NO_TIMER = 0L
    }

    fun showTimerDialog(packageName: String, appName: String, onTimerSet: (Long) -> Unit) {
        val options = arrayOf("1 minute", "5 minutes", "10 minutes", "Custom", "No timer")

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
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
        
        // Fix dialog items text color to white
        fixDialogItemsTextColor(dialog)
    }
    
    private fun fixDialogItemsTextColor(dialog: AlertDialog) {
        try {
            val whiteColor = ContextCompat.getColor(context, android.R.color.white)
            val listView = dialog.listView
            if (listView != null) {
                // Post on main thread after dialog is shown to ensure views are inflated
                listView.post {
                    try {
                        // Fix all existing items
                        for (i in 0 until listView.childCount) {
                            val itemView = listView.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(whiteColor)
                            } else if (itemView is ViewGroup) {
                                // Search for TextView in the view hierarchy
                                findTextViewsAndSetColor(itemView, whiteColor)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Also try after a small delay in case items load asynchronously
                mainHandler.postDelayed({
                    try {
                        for (i in 0 until listView.childCount) {
                            val itemView = listView.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(whiteColor)
                            } else if (itemView is ViewGroup) {
                                findTextViewsAndSetColor(itemView, whiteColor)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, 100)
            }
        } catch (e: Exception) {
            // If we can't fix it, that's okay - at least try
            e.printStackTrace()
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
        input.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        input.setHintTextColor(ContextCompat.getColor(context, android.R.color.white))

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
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
                // Write to SharedPreferences in background to prevent UI freezing
                backgroundExecutor.execute {
                    prefs.edit().putLong("timer_remaining_$packageName", millisUntilFinished).apply()
                }
            }

            override fun onFinish() {
                // Clean up timer state
                backgroundExecutor.execute {
                    prefs.edit().remove("timer_remaining_$packageName").apply()
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
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun returnToLauncher(packageName: String) {
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
                    }, 800)
                    return
                }
            } catch (e: Exception) {
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
            }, 800)
        } catch (e: Exception) {
            // Last resort: simpler HOME intent
            try {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                mainHandler.postDelayed({
                    forceCloseApp(packageName)
                }, 800)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    private fun forceCloseApp(packageName: String) {
        // Run in background thread to avoid blocking
        backgroundExecutor.execute {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (activityManager == null) {
                    return@execute
                }
                
                // Kill all background processes for this package
                // Since we've already brought launcher to foreground, the app should be in background
                activityManager.killBackgroundProcesses(packageName)
                
                // Try multiple times to ensure it's killed
                mainHandler.postDelayed({
                    try {
                        activityManager.killBackgroundProcesses(packageName)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }, 500)
                
                // Try to remove from recent tasks if possible
                // Note: This requires special permissions on newer Android versions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    try {
                        // Try using getRecentTasks (may require special permission)
                        @Suppress("DEPRECATION")
                        val recentTasks = activityManager.getRecentTasks(50, ActivityManager.RECENT_WITH_EXCLUDED)
                        for (taskInfo in recentTasks) {
                            val baseIntent = taskInfo.baseIntent
                            val component = baseIntent?.component
                            if (component != null && component.packageName == packageName) {
                                // Try to remove this task
                                try {
                                    // Kill processes first
                                    activityManager.killBackgroundProcesses(packageName)
                                } catch (e: Exception) {
                                    // Ignore - we don't have permission to manipulate tasks
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        // getRecentTasks requires special permission, that's okay
                        // killBackgroundProcesses should still work
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                
            } catch (e: SecurityException) {
                // If we don't have permission, at least the app is in background
                // Android will manage it
                try {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    activityManager?.killBackgroundProcesses(packageName)
                } catch (e2: Exception) {
                    // Ignore
                }
            } catch (e: Exception) {
                // Handle any other exceptions
                e.printStackTrace()
            }
        }
    }

    fun cancelTimer() {
        currentTimer?.cancel()
        currentTimer = null
        currentPackageName?.let { packageName ->
            // Clean up in background
            backgroundExecutor.execute {
                prefs.edit().remove("timer_remaining_$packageName").apply()
            }
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