package com.guruswarupa.launch.managers

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import java.util.concurrent.TimeUnit

class ReviewPromptManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences
) {
    private val reviewPromptIntervalMillis = TimeUnit.DAYS.toMillis(7)
    private val maxPromptCount = 3
    private val reviewManager = ReviewManagerFactory.create(activity)

    fun recordFirstUseIfNeeded() {
        if (sharedPreferences.getLong(Constants.Prefs.REVIEW_FIRST_USE_AT, 0L) != 0L) {
            return
        }
        val now = System.currentTimeMillis()
        sharedPreferences.edit {
            putLong(Constants.Prefs.REVIEW_FIRST_USE_AT, now)
            putLong(Constants.Prefs.REVIEW_NEXT_PROMPT_AT, now + reviewPromptIntervalMillis)
        }
    }

    fun promptIfEligible(): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        if (sharedPreferences.getBoolean(Constants.Prefs.REVIEW_CTA_USED, false)) {
            return false
        }
        val firstUseAt = sharedPreferences.getLong(Constants.Prefs.REVIEW_FIRST_USE_AT, 0L)
        if (firstUseAt == 0L) {
            recordFirstUseIfNeeded()
            return false
        }
        val promptCount = sharedPreferences.getInt(Constants.Prefs.REVIEW_PROMPT_COUNT, 0)
        if (promptCount >= maxPromptCount) {
            return false
        }
        val now = System.currentTimeMillis()
        val nextPromptAt = sharedPreferences.getLong(
            Constants.Prefs.REVIEW_NEXT_PROMPT_AT,
            firstUseAt + reviewPromptIntervalMillis
        )
        if (now < nextPromptAt) {
            return false
        }
        AlertDialog.Builder(activity, com.guruswarupa.launch.R.style.CustomDialogTheme)
            .setTitle(R.string.review_prompt_title)
            .setMessage(R.string.review_prompt_message)
            .setPositiveButton(R.string.review_prompt_positive) { _, _ ->
                sharedPreferences.edit {
                    putBoolean(Constants.Prefs.REVIEW_CTA_USED, true)
                }
                launchReviewFlow()
            }
            .setNegativeButton(R.string.review_prompt_negative) { _, _ ->
                scheduleNextPrompt()
            }
            .setOnCancelListener {
                scheduleNextPrompt()
            }
            .show()
        return true
    }

    private fun launchReviewFlow() {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (!activity.isFinishing && !activity.isDestroyed && task.isSuccessful) {
                val reviewInfo = task.result
                reviewManager.launchReviewFlow(activity, reviewInfo)
            } else {
                openPlayStoreListing()
            }
        }
    }

    private fun openPlayStoreListing() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${activity.packageName}")
        )
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")
        )
        val intent = if (marketIntent.resolveActivity(activity.packageManager) != null) {
            marketIntent
        } else {
            webIntent
        }
        activity.startActivity(intent)
    }

    private fun scheduleNextPrompt() {
        val now = System.currentTimeMillis()
        val updatedPromptCount = sharedPreferences.getInt(Constants.Prefs.REVIEW_PROMPT_COUNT, 0) + 1
        sharedPreferences.edit {
            putInt(Constants.Prefs.REVIEW_PROMPT_COUNT, updatedPromptCount)
            putLong(Constants.Prefs.REVIEW_NEXT_PROMPT_AT, now + reviewPromptIntervalMillis)
        }
    }
}
