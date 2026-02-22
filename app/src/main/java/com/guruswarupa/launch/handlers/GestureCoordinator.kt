package com.guruswarupa.launch.handlers

import android.os.SystemClock
import android.util.Log

/**
 * Coordinates gestures to prevent mutual interference between Shake and Back Tap.
 * Since both services run in the same process, we can use a singleton to track triggers.
 */
object GestureCoordinator {
    private const val TAG = "GestureCoordinator"
    private var lastTriggerTime = 0L
    private const val MUTUAL_EXCLUSION_WINDOW = 1200L // ms

    /**
     * Call this before executing a gesture action.
     * Returns true if the action should proceed, false if it should be ignored.
     */
    @Synchronized
    fun requestTrigger(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < MUTUAL_EXCLUSION_WINDOW) {
            Log.d(TAG, "Trigger blocked: too soon after last trigger (${now - lastTriggerTime}ms)")
            return false
        }
        lastTriggerTime = now
        return true
    }

    /**
     * Returns true if any gesture action was recently triggered.
     * Detectors can use this to skip processing during the cooldown period.
     */
    fun isInCooldown(): Boolean {
        return SystemClock.elapsedRealtime() - lastTriggerTime < MUTUAL_EXCLUSION_WINDOW
    }
}
