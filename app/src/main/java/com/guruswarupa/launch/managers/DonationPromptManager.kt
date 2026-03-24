package com.guruswarupa.launch.managers

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.SettingsActivity
import java.util.concurrent.TimeUnit

class DonationPromptManager(
    private val activity: Activity,
    private val sharedPreferences: SharedPreferences
) {
    private val donationPromptDelayMillis = TimeUnit.DAYS.toMillis(30)

    fun promptIfEligible(): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        if (sharedPreferences.getBoolean(Constants.Prefs.DONATION_PROMPT_SHOWN, false)) {
            return false
        }
        if (sharedPreferences.getBoolean(Constants.Prefs.SUPPORTER_BADGE_EARNED, false)) {
            return false
        }
        val firstUseAt = sharedPreferences.getLong(Constants.Prefs.REVIEW_FIRST_USE_AT, 0L)
        if (firstUseAt == 0L) {
            return false
        }
        if (System.currentTimeMillis() < firstUseAt + donationPromptDelayMillis) {
            return false
        }
        showPrompt(markAsShown = true) {
            val intent = Intent(activity, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_OPEN_SUPPORT_SECTION, true)
            }
            activity.startActivity(intent)
        }
        return true
    }

    fun showPrompt(markAsShown: Boolean = false, onSupport: (() -> Unit)? = null) {
        if (markAsShown) {
            sharedPreferences.edit {
                putBoolean(Constants.Prefs.DONATION_PROMPT_SHOWN, true)
            }
        }
        AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.donation_prompt_title))
            .setMessage(activity.getString(R.string.donation_prompt_message))
            .setPositiveButton(activity.getString(R.string.donation_prompt_support)) { _, _ ->
                onSupport?.invoke()
            }
            .setNegativeButton(activity.getString(R.string.donation_prompt_not_now), null)
            .show()
    }
}
