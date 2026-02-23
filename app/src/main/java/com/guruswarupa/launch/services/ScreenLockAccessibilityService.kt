package com.guruswarupa.launch.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants

class ScreenLockAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var shortcutMenu: View? = null
    private var isMenuVisible = false

    companion object {
        var instance: ScreenLockAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.Prefs.ACCESSIBILITY_SHORTCUT_ENABLED, false)) {
            showFloatingButton()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "TOGGLE_SHORTCUT") {
            val enabled = intent.getBooleanExtra("enabled", false)
            if (enabled) {
                showFloatingButton()
            } else {
                removeFloatingButton()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showFloatingButton() {
        if (floatingView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_accessibility_shortcut_trigger, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 0
        params.y = 200

        floatingView?.setOnClickListener {
            toggleMenu()
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingButton() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            floatingView = null
        }
        hideMenu()
    }

    private fun toggleMenu() {
        if (isMenuVisible) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        if (shortcutMenu != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Launch)
        shortcutMenu = LayoutInflater.from(themedContext).inflate(R.layout.layout_accessibility_shortcut, null)
        setupMenuListeners(shortcutMenu!!)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER
        
        shortcutMenu?.setOnClickListener { hideMenu() }

        try {
            windowManager?.addView(shortcutMenu, params)
            isMenuVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideMenu() {
        shortcutMenu?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            shortcutMenu = null
        }
        isMenuVisible = false
    }

    private fun setupMenuListeners(view: View) {
        view.findViewById<View>(R.id.btn_wifi)?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                hideMenu()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open WiFi settings", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_bluetooth)?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                hideMenu()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_airplane)?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                hideMenu()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Airplane settings", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_brightness)?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                hideMenu()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Display settings", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btn_sound)?.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val nextMode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            audioManager.ringerMode = nextMode
            val modeName = when (nextMode) {
                AudioManager.RINGER_MODE_NORMAL -> "Normal"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                else -> "Silent"
            }
            Toast.makeText(this, "Sound: $modeName", Toast.LENGTH_SHORT).show()
            hideMenu()
        }

        view.findViewById<View>(R.id.btn_screenshot)?.setOnClickListener {
            takeScreenshot()
            hideMenu()
        }

        view.findViewById<View>(R.id.btn_record)?.setOnClickListener {
            Toast.makeText(this, "Use system screen recorder from Quick Settings", Toast.LENGTH_SHORT).show()
            hideMenu()
        }

        view.findViewById<View>(R.id.btn_sleep)?.setOnClickListener {
            lockScreen()
            hideMenu()
        }

        view.findViewById<View>(R.id.btn_power)?.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            hideMenu()
        }
        
        view.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            hideMenu()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeFloatingButton()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    fun toggleNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
}
