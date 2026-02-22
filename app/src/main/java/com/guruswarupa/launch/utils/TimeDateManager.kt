package com.guruswarupa.launch.utils

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
    private val dateTextView: TextView,
    private val rightDrawerTime: TextView? = null,
    private val rightDrawerDate: TextView? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    
    // Artistic formats for right drawer
    private val artisticTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val artisticDateFormat = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault())
    
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
            handler.postDelayed(this, 30000) // Update every 30 seconds
        }
    }
    
    fun startUpdates(isPowerSaver: Boolean = false) {
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
        val now = Date()
        val currentTime = timeFormat.format(now)
        timeTextView.text = currentTime
        
        rightDrawerTime?.text = artisticTimeFormat.format(now)
    }
    
    fun updateDate() {
        val now = Date()
        val currentTime = dateFormat.format(now)
        dateTextView.text = currentTime
        
        rightDrawerDate?.text = artisticDateFormat.format(now).uppercase(Locale.getDefault())
    }
}
