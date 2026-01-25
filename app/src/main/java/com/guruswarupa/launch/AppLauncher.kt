package com.guruswarupa.launch

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

/**
 * Handles app launching logic with lock and timer checks.
 * Extracted from MainActivity to reduce complexity.
 */
class AppLauncher(
    private val activity: FragmentActivity,
    private val packageManager: PackageManager,
    private val appLockManager: AppLockManager,
    private val appTimerManager: AppTimerManager,
    private val appCategoryManager: AppCategoryManager
) {
    /**
     * Launches an app directly without any checks.
     */
    fun launchApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "$appName app is not installed.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Error opening $appName app.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches an app with lock check.
     */
    fun launchAppWithLockCheck(packageName: String, appName: String) {
        if (appLockManager.isAppLocked(packageName)) {
            appLockManager.verifyPin { isAuthenticated ->
                if (isAuthenticated) {
                    launchApp(packageName, appName)
                }
            }
        } else {
            launchApp(packageName, appName)
        }
    }

    /**
     * Launches an app with timer check and then lock check.
     */
    fun launchAppWithTimerCheck(packageName: String, onTimerSet: () -> Unit) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        // Only show timer dialog for social media and entertainment apps
        if (appCategoryManager.shouldShowTimer(packageName, appName)) {
            appTimerManager.showTimerDialog(packageName, appName) { timerDuration ->
                if (timerDuration == AppTimerManager.NO_TIMER) {
                    // No timer selected, proceed with normal launch (includes lock check)
                    onTimerSet()
                } else {
                    // Timer selected - handle lock check first, then start timer (which launches app)
                    if (appLockManager.isAppLocked(packageName)) {
                        appLockManager.verifyPin { isAuthenticated ->
                            if (isAuthenticated) {
                                appTimerManager.startTimer(packageName, timerDuration)
                            }
                        }
                    } else {
                        appTimerManager.startTimer(packageName, timerDuration)
                    }
                }
            }
        } else {
            // For productivity and other apps, launch directly without timer (includes lock check)
            onTimerSet()
        }
    }
}
