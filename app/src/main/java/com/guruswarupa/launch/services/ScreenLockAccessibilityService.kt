package com.guruswarupa.launch.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.GridLayout
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
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    
    private var isTorchOn = false
    private val torchCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                isTorchOn = enabled
            }
        }
    } else null

    companion object {
        var instance: ScreenLockAccessibilityService? = null
            private set
            
        const val DEFAULT_SHORTCUTS = "wifi,bluetooth,airplane,torch,data,rotation,sound,dnd,location,qr_scan,camera,screenshot,record,lock,power"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && torchCallback != null) {
            try {
                cameraManager.registerTorchCallback(torchCallback, null)
            } catch (_: Exception) {}
        }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)
                        
                        if (dx > 20 || dy > 20 || !isMoving) {
                            toggleMenu()
                        }
                        
                        snapToEdge(params)
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

        floatingView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                floatingView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                snapToEdge(params)
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            windowMetrics?.bounds?.let {
                displayMetrics.widthPixels = it.width()
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        }

        val screenWidth = displayMetrics.widthPixels
        val viewWidth = floatingView?.measuredWidth ?: 0
        if (viewWidth == 0) return

        val visibleEdgeWidth = (resources.displayMetrics.density * 24).toInt()

        if (params.x + viewWidth / 2 < screenWidth / 2) {
            params.x = -(viewWidth - visibleEdgeWidth)
        } else {
            params.x = screenWidth - visibleEdgeWidth
        }

        try {
            windowManager?.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
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
        val imgVolume = view.findViewById<ImageView>(R.id.img_volume_icon)
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeSeekBar?.max = maxVolume
            volumeSeekBar?.progress = currentVolume
            
            volumeSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                        if (progress == 0) {
                            imgVolume?.setImageResource(R.drawable.ic_muted_stat)
                        } else {
                            imgVolume?.setImageResource(R.drawable.ic_volume_up_stat)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (_: Exception) {}

        // Dynamic Shortcuts
        val grid = view.findViewById<GridLayout>(R.id.shortcuts_grid)
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val shortcutList = prefs.getString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, DEFAULT_SHORTCUTS)
            ?.split(",") ?: DEFAULT_SHORTCUTS.split(",")
            
        grid?.removeAllViews()
        
        for (id in shortcutList) {
            val shortcutView = LayoutInflater.from(view.context).inflate(R.layout.item_control_center_shortcut, grid, false)
            val icon = shortcutView.findViewById<ImageView>(R.id.shortcut_icon)
            val label = shortcutView.findViewById<TextView>(R.id.shortcut_label)
            
            when (id) {
                "wifi" -> {
                    icon.setImageResource(R.drawable.ic_wifi_stat)
                    label.text = "WiFi"
                    updateWifiIcon(icon)
                    shortcutView.setOnClickListener { toggleWifi(); updateWifiIcon(icon); hideMenu() }
                }
                "bluetooth" -> {
                    icon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
                    label.text = "Bluetooth"
                    updateBluetoothIcon(icon)
                    shortcutView.setOnClickListener { toggleBluetooth(); hideMenu() }
                }
                "airplane" -> {
                    icon.setImageResource(R.drawable.ic_airplane_stat)
                    label.text = "Airplane"
                    updateAirplaneIcon(icon)
                    shortcutView.setOnClickListener { toggleAirplaneMode(); hideMenu() }
                }
                "torch" -> {
                    icon.setImageResource(R.drawable.ic_torch_stat)
                    label.text = "Torch"
                    updateTorchIcon(icon)
                    shortcutView.setOnClickListener { toggleTorch(); updateTorchIcon(icon) }
                }
                "data" -> {
                    icon.setImageResource(R.drawable.ic_mobile_data_stat)
                    label.text = "Data"
                    shortcutView.setOnClickListener { 
                        startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        hideMenu()
                    }
                }
                "rotation" -> {
                    icon.setImageResource(R.drawable.ic_rotation_stat)
                    label.text = "Auto-Rot"
                    updateRotationIcon(icon)
                    shortcutView.setOnClickListener { toggleAutoRotation(); updateRotationIcon(icon) }
                }
                "sound" -> {
                    updateSoundIcon(icon, label)
                    shortcutView.setOnClickListener {
                        val nextMode = when (audioManager.ringerMode) {
                            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                            else -> AudioManager.RINGER_MODE_NORMAL
                        }
                        audioManager.ringerMode = nextMode
                        updateSoundIcon(icon, label)
                        volumeSeekBar?.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    }
                }
                "dnd" -> {
                    icon.setImageResource(R.drawable.ic_focus_mode_icon)
                    updateDndIcon(icon, label)
                    shortcutView.setOnClickListener { toggleDnd(); updateDndIcon(icon, label) }
                }
                "location" -> {
                    icon.setImageResource(android.R.drawable.ic_menu_mylocation)
                    label.text = "Location"
                    shortcutView.setOnClickListener { 
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        hideMenu()
                    }
                }
                "qr_scan" -> {
                    icon.setImageResource(R.drawable.ic_qr_scan_stat)
                    label.text = "QR Scan"
                    shortcutView.setOnClickListener { launchQrScanner(); hideMenu() }
                }
                "camera" -> {
                    icon.setImageResource(android.R.drawable.ic_menu_camera)
                    label.text = "Camera"
                    shortcutView.setOnClickListener { 
                        startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        hideMenu()
                    }
                }
                "screenshot" -> {
                    icon.setImageResource(R.drawable.ic_screenshot_stat)
                    label.text = "Screenshot"
                    shortcutView.setOnClickListener { takeScreenshot(); hideMenu() }
                }
                "record" -> {
                    icon.setImageResource(android.R.drawable.ic_menu_slideshow)
                    label.text = if (ScreenRecordingService.isRunning) "Stop" else "Record"
                    if (ScreenRecordingService.isRunning) icon.setColorFilter(0xFFF7768E.toInt())
                    shortcutView.setOnClickListener {
                        if (ScreenRecordingService.isRunning) {
                            ScreenRecordingService.stopService(this)
                        } else {
                            startActivity(Intent(this, ScreenRecordPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        hideMenu()
                    }
                }
                "lock" -> {
                    icon.setImageResource(android.R.drawable.ic_lock_idle_lock)
                    label.text = "Lock"
                    shortcutView.setOnClickListener { lockScreen(); hideMenu() }
                }
                "power" -> {
                    icon.setImageResource(android.R.drawable.ic_lock_power_off)
                    icon.setColorFilter(0xFFF7768E.toInt())
                    label.text = "Power"
                    label.setTextColor(0xFFF7768E.toInt())
                    shortcutView.setOnClickListener { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); hideMenu() }
                }
                else -> continue
            }
            grid?.addView(shortcutView)
        }
    }

    private fun launchQrScanner() {
        val qrIntents = mutableListOf<Intent>()
        
        // 1. Google Lens Specific Intent
        qrIntents.add(Intent("com.google.android.googlequicksearchbox.GOOGLE_LENS").apply {
            setPackage("com.google.android.googlequicksearchbox")
        })
        
        // 2. Google Lens Standalone
        val lensIntent = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
        if (lensIntent != null) qrIntents.add(lensIntent)
        
        // 3. Android 13+ System QR Scanner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            qrIntents.add(Intent("android.settings.QR_CODE_SCANNER"))
        }
        
        // 4. Google Lens via URI
        qrIntents.add(Intent(Intent.ACTION_VIEW, Uri.parse("googlelens://v1/scan")))
        
        // 5. ZXing
        qrIntents.add(Intent("com.google.zxing.client.android.SCAN"))
        
        // 6. Camera with QR mode
        qrIntents.add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            putExtra("android.intent.extra.USE_QR_CODE", true)
        })

        var started = false
        for (intent in qrIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                started = true
                break
            } catch (_: Exception) {}
        }
        if (!started) {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this, "No QR scanner or camera found", Toast.LENGTH_SHORT).show()
            }
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
    }

    private fun updateAirplaneIcon(imageView: ImageView?) {
        val enabled = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        imageView?.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun toggleTorch() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            Toast.makeText(this, "Torch not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTorchIcon(imageView: ImageView?) {
        imageView?.alpha = if (isTorchOn) 1.0f else 0.4f
    }

    private fun toggleAutoRotation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
                return
            }
        }
        try {
            val isAutoRotate = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            val newValue = if (isAutoRotate) 0 else 1
            val success = Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, newValue)
            if (success && newValue == 0) {
                Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            }
            Toast.makeText(this, if (newValue == 1) "Auto-rotate ON" else "Portrait Locked", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun updateRotationIcon(imageView: ImageView?) {
        try {
            val isAutoRotate = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            imageView?.alpha = if (isAutoRotate) 1.0f else 0.4f
        } catch (_: Exception) {}
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
                imageView?.setImageResource(android.R.drawable.ic_lock_silent_mode)
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
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && torchCallback != null) {
            cameraManager.unregisterTorchCallback(torchCallback)
        }
        super.onDestroy()
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
