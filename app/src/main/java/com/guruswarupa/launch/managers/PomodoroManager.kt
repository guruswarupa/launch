package com.guruswarupa.launch.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.widget.Toast
import androidx.core.content.edit
import com.guruswarupa.launch.R

class PomodoroManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val focusModeManager: FocusModeManager
) {
    companion object {
        const val PREF_POMODORO_STATE = "pomodoro_state"
        const val PREF_POMODORO_END_TIME = "pomodoro_end_time"
        const val PREF_POMODORO_CYCLE_COUNT = "pomodoro_cycle_count"
        
        const val STATE_INACTIVE = "inactive"
        const val STATE_WORK = "work"
        const val STATE_BREAK = "break"
        
        const val WORK_DURATION = 25 * 60 * 1000L 
        const val BREAK_DURATION = 5 * 60 * 1000L 
    }

    private var currentTimer: CountDownTimer? = null
    var onTimerTick: ((remainingMillis: Long, state: String) -> Unit)? = null
    var onStateChanged: ((state: String, isFocusMode: Boolean) -> Unit)? = null
    var onSessionEnded: (() -> Unit)? = null

    fun isPomodoroActive(): Boolean {
        return sharedPreferences.getString(PREF_POMODORO_STATE, STATE_INACTIVE) != STATE_INACTIVE
    }

    fun getCurrentState(): String {
        return sharedPreferences.getString(PREF_POMODORO_STATE, STATE_INACTIVE) ?: STATE_INACTIVE
    }

    fun startPomodoro() {
        startWorkSession()
    }

    private fun startWorkSession() {
        val endTime = System.currentTimeMillis() + WORK_DURATION
        sharedPreferences.edit {
            putString(PREF_POMODORO_STATE, STATE_WORK)
            putLong(PREF_POMODORO_END_TIME, endTime)
        }
        
        focusModeManager.setFocusModeEnabled(true)
        onStateChanged?.invoke(STATE_WORK, true)
        startTimer(WORK_DURATION, STATE_WORK)
        Toast.makeText(context, "${context.getString(R.string.pomodoro_work)} session started (25m)", Toast.LENGTH_SHORT).show()
    }

    private fun startBreakSession() {
        val endTime = System.currentTimeMillis() + BREAK_DURATION
        sharedPreferences.edit {
            putString(PREF_POMODORO_STATE, STATE_BREAK)
            putLong(PREF_POMODORO_END_TIME, endTime)
        }
        
        focusModeManager.setFocusModeEnabled(false)
        onStateChanged?.invoke(STATE_BREAK, false)
        startTimer(BREAK_DURATION, STATE_BREAK)
        Toast.makeText(context, "${context.getString(R.string.pomodoro_break)} session started (5m)", Toast.LENGTH_SHORT).show()
    }

    fun stopPomodoro() {
        currentTimer?.cancel()
        currentTimer = null
        
        sharedPreferences.edit {
            putString(PREF_POMODORO_STATE, STATE_INACTIVE)
            remove(PREF_POMODORO_END_TIME)
            remove(PREF_POMODORO_CYCLE_COUNT)
        }
        
        focusModeManager.setFocusModeEnabled(false)
        onSessionEnded?.invoke()
    }

    private fun startTimer(duration: Long, state: String) {
        currentTimer?.cancel()
        currentTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                onTimerTick?.invoke(millisUntilFinished, state)
            }

            override fun onFinish() {
                if (state == STATE_WORK) {
                    val cycles = sharedPreferences.getInt(PREF_POMODORO_CYCLE_COUNT, 0)
                    sharedPreferences.edit { putInt(PREF_POMODORO_CYCLE_COUNT, cycles + 1) }
                    startBreakSession()
                } else {
                    startWorkSession()
                }
            }
        }.start()
    }

    fun resumeIfNeeded() {
        val state = getCurrentState()
        if (state == STATE_INACTIVE) return
        
        val endTime = sharedPreferences.getLong(PREF_POMODORO_END_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        if (currentTime >= endTime) {
            
            if (state == STATE_WORK) {
                startBreakSession()
            } else {
                startWorkSession()
            }
        } else {
            
            startTimer(endTime - currentTime, state)
        }
    }
}
