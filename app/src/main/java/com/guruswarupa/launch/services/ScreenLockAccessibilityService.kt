package com.guruswarupa.launch.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.ScreenRecordPermissionActivity
import kotlin.math.abs

class ScreenLockAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var shortcutMenu: View? = null
    private var isMenuVisible = false

    private val wifiManager by lazy { getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val bluetoothAdapter by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

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

    @SuppressLint("ClickableViewAccessibility")
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

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0.0f
            private var initialTouchY: Float = 0.0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            toggleMenu()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager?.updateViewLayout(floatingView, params)
                            } catch (_: Exception) {}
                        }
                        return true
                    }
                }
                return false
            }
        })

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
        val menuContainer = view.findViewById<View>(R.id.menu_container)
        menuContainer?.setOnClickListener { /* Prevents closing when clicking menu itself */ }

        // WiFi Toggle
        val btnWifi = view.findViewById<View>(R.id.btn_wifi)
        val imgWifi = view.findViewById<ImageView>(R.id.img_wifi)
        updateWifiIcon(imgWifi)
        btnWifi?.setOnClickListener {
            toggleWifi()
            updateWifiIcon(imgWifi)
            hideMenu()
        }

        // Bluetooth Toggle
        val btnBluetooth = view.findViewById<View>(R.id.btn_bluetooth)
        val imgBluetooth = view.findViewById<ImageView>(R.id.img_bluetooth)
        updateBluetoothIcon(imgBluetooth)
        btnBluetooth?.setOnClickListener {
            toggleBluetooth()
            hideMenu()
        }

        // Airplane Mode Toggle
        val btnAirplane = view.findViewById<View>(R.id.btn_airplane)
        val imgAirplane = view.findViewById<ImageView>(R.id.img_airplane)
        updateAirplaneIcon(imgAirplane)
        btnAirplane?.setOnClickListener {
            toggleAirplaneMode()
            hideMenu()
        }

        // Brightness Control
        val brightnessSeekBar = view.findViewById<SeekBar>(R.id.brightness_seekbar)
        if (Settings.System.canWrite(this)) {
            try {
                val currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                brightnessSeekBar?.progress = currentBrightness
            } catch (_: Exception) {
                brightnessSeekBar?.progress = 125
            }
            
            brightnessSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                        } catch (_: Exception) {
                            requestWriteSettingsPermission()
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else {
            brightnessSeekBar?.alpha = 0.5f
            brightnessSeekBar?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    requestWriteSettingsPermission()
                }
                true
            }
        }

        // Volume Control
        val volumeSeekBar = view.findViewById<SeekBar>(R.id.volume_seekbar)
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeSeekBar?.max = maxVolume
            volumeSeekBar?.progress = currentVolume
            
            volumeSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (_: Exception) {}

        // Sound Toggle
        val btnSound = view.findViewById<View>(R.id.btn_sound)
        val imgSound = view.findViewById<ImageView>(R.id.img_sound)
        val txtSound = view.findViewById<TextView>(R.id.txt_sound)
        updateSoundIcon(imgSound, txtSound)
        btnSound?.setOnClickListener {
            val nextMode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            audioManager.ringerMode = nextMode
            updateSoundIcon(imgSound, txtSound)
            // Update volume seekbar in case it changed due to ringer mode
            volumeSeekBar?.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        // DND Toggle
        val btnDnd = view.findViewById<View>(R.id.btn_dnd)
        val imgDnd = view.findViewById<ImageView>(R.id.img_dnd)
        val txtDnd = view.findViewById<TextView>(R.id.txt_dnd)
        updateDndIcon(imgDnd, txtDnd)
        btnDnd?.setOnClickListener {
            toggleDnd()
            updateDndIcon(imgDnd, txtDnd)
        }

        // Lock Screen
        view.findViewById<View>(R.id.btn_lock)?.setOnClickListener {
            lockScreen()
            hideMenu()
        }

        // Screenshot
        view.findViewById<View>(R.id.btn_screenshot)?.setOnClickListener {
            takeScreenshot()
            hideMenu()
        }

        // Record Screen (Custom)
        val btnRecord = view.findViewById<View>(R.id.btn_record)
        val txtRecord = view.findViewById<TextView>(R.id.txt_record)
        val imgRecord = view.findViewById<ImageView>(R.id.img_record)
        
        if (ScreenRecordingService.isRunning) {
            imgRecord?.setColorFilter(0xFFF7768E.toInt())
            txtRecord?.text = "Stop"
        }

        btnRecord?.setOnClickListener {
            if (ScreenRecordingService.isRunning) {
                ScreenRecordingService.stopService(this)
                imgRecord?.clearColorFilter()
                txtRecord?.text = "Record"
            } else {
                val intent = Intent(this, ScreenRecordPermissionActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            hideMenu()
        }

        // Power Menu
        view.findViewById<View>(R.id.btn_power)?.setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            hideMenu()
        }
    }

    private fun toggleWifi() {
        try {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun updateWifiIcon(imageView: ImageView?) {
        val enabled = wifiManager.isWifiEnabled
        imageView?.setImageResource(R.drawable.ic_wifi_stat)
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }

    @SuppressLint("MissingPermission")
    private fun toggleBluetooth() {
        try {
            if (bluetoothAdapter.isEnabled) bluetoothAdapter.disable() else bluetoothAdapter.enable()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun updateBluetoothIcon(imageView: ImageView?) {
        val enabled = bluetoothAdapter.isEnabled
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun toggleAirplaneMode() {
        startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(this, "Toggle Airplane Mode in settings", Toast.LENGTH_SHORT).show()
    }

    private fun updateAirplaneIcon(imageView: ImageView?) {
        val enabled = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        imageView?.setImageResource(R.drawable.ic_airplane_stat)
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun updateSoundIcon(imageView: ImageView?, textView: TextView?) {
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                imageView?.setImageResource(R.drawable.ic_volume_up_stat)
                imageView?.alpha = 1.0f
                textView?.text = "Sound"
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                imageView?.setImageResource(R.drawable.ic_vibrate_stat)
                imageView?.alpha = 1.0f
                textView?.text = "Vibrate"
            }
            AudioManager.RINGER_MODE_SILENT -> {
                imageView?.setImageResource(R.drawable.ic_muted_stat)
                imageView?.alpha = 0.5f
                textView?.text = "Muted"
            }
        }
    }

    private fun toggleDnd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val currentFilter = notificationManager.currentInterruptionFilter
                val newFilter = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(newFilter)
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(this, "Please grant DND access", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateDndIcon(imageView: ImageView?, textView: TextView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val enabled = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            imageView?.setImageResource(R.drawable.ic_focus_mode_icon)
            imageView?.alpha = if (enabled) 1.0f else 0.4f
            textView?.text = if (enabled) "DND On" else "DND Off"
        }
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        Toast.makeText(this, "Please allow 'Modify system settings' for brightness control", Toast.LENGTH_LONG).show()
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
