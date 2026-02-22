package com.guruswarupa.launch

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.managers.AppLockManager

/**
 * Handles app launching logic with lock and timer checks.
 * Extracted from MainActivity to reduce complexity.
 */
class AppLauncher(
    private val activity: FragmentActivity,
    private val packageManager: PackageManager,
    private val appLockManager: AppLockManager,
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
        } catch (_: Exception) {
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
}
