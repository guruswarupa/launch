package com.guruswarupa.launch

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages time and date display updates
 */
class TimeDateManager(
    private val timeTextView: TextView,
    private val dateTextView: TextView
) {
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTime()
            updateDate()
            handler.postDelayed(this, 1000)
        }
    }
    
    private val powerSaverUpdateRunnable = object : Runnable {
        override fun run() {
            updateTime()
            updateDate()
            handler.postDelayed(this, 30000) // Update every 30 seconds in power saver mode
        }
    }
    
    private var isPowerSaverMode = false
    
    fun startUpdates(isPowerSaver: Boolean = false) {
        isPowerSaverMode = isPowerSaver
        stopUpdates()
        if (isPowerSaver) {
            handler.post(powerSaverUpdateRunnable)
        } else {
            handler.post(updateRunnable)
        }
    }
    
    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(powerSaverUpdateRunnable)
    }
    
    fun updateTime() {
        val currentTime = timeFormat.format(Date())
        timeTextView.text = currentTime
    }
    
    fun updateDate() {
        val currentTime = dateFormat.format(Date())
        dateTextView.text = currentTime
    }
    
    fun setPowerSaverMode(enabled: Boolean) {
        isPowerSaverMode = enabled
        startUpdates(enabled)
    }
}
