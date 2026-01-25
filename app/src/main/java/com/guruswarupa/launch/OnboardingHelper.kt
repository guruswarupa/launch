package com.guruswarupa.launch

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Helper class for onboarding checks.
 * Extracted from MainActivity to reduce complexity.
 */
class OnboardingHelper(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val sharedPreferences: SharedPreferences,
    private val packageManager: PackageManager,
    private val packageName: String
) {
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    /**
     * Checks if onboarding is needed and starts it if required.
     * @return true if onboarding was started (activity will finish), false otherwise
     */
    fun checkAndStartOnboarding(): Boolean {
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)
        val isFirstTime = sharedPreferences.getBoolean("isFirstTime", true)
        
        if (isFirstRun || isFirstTime) {
            if (isFirstRun) {
                sharedPreferences.edit().putBoolean("isFirstRun", false).apply()
            }
            
            // Start onboarding - if we're here because launcher was set as default, continue from default launcher step
            val intent = Intent(activity, OnboardingActivity::class.java)
            if (isDefaultLauncher() && !isFirstRun) {
                // Launcher is set as default but onboarding not complete - continue from default launcher step
                intent.putExtra("continueFromDefaultLauncher", true)
            }
            activity.startActivity(intent)
            activity.finish()
            return true
        }
        return false
    }
}
