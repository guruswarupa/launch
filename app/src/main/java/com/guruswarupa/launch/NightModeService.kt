package com.guruswarupa.launch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class NightModeService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val SERVICE_NAME = "Night Mode"
        private const val ACTION_UPDATE_NIGHT_MODE = "com.guruswarupa.launch.UPDATE_NIGHT_MODE"
        private const val EXTRA_INTENSITY = "extra_intensity"

        fun startService(context: Context, intensity: Int) {
            val intent = Intent(context, NightModeService::class.java).apply {
                putExtra(EXTRA_INTENSITY, intensity)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, NightModeService::class.java))
        }

        fun updateIntensity(context: Context, intensity: Int) {
            val intent = Intent(context, NightModeService::class.java).apply {
                action = ACTION_UPDATE_NIGHT_MODE
                putExtra(EXTRA_INTENSITY, intensity)
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
        startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intensity = intent?.getIntExtra(EXTRA_INTENSITY, 50) ?: 50
        
        if (intent?.action == ACTION_UPDATE_NIGHT_MODE) {
            updateOverlay(intensity)
        } else {
            showOverlay(intensity)
        }
        
        return START_STICKY
    }

    private fun showOverlay(intensity: Int) {
        if (overlayView != null) {
            updateOverlay(intensity)
            return
        }

        overlayView = View(this)
        
        // Create orange tint with adjustable intensity
        // Orange color: #FFA500 (RGB: 255, 165, 0)
        val alpha = (intensity * 255 / 100).coerceIn(0, 255)
        val orangeColor = Color.argb(alpha, 255, 165, 0) // ARGB: Alpha, Red, Green, Blue
        overlayView?.setBackgroundColor(orangeColor)
        
        // Use system UI flags to hide standard boundaries
        @Suppress("DEPRECATION")
        overlayView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        // Get real screen dimensions including navigation bar
        val metrics = android.util.DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            windowMetrics?.bounds?.let {
                metrics.widthPixels = it.width()
                metrics.heightPixels = it.height()
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getRealMetrics(metrics)
        }

        params = WindowManager.LayoutParams(
            metrics.widthPixels,
            metrics.heightPixels,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateOverlay(intensity: Int) {
        // Update the orange tint with new intensity
        val alpha = (intensity * 255 / 100).coerceIn(0, 255)
        val orangeColor = Color.argb(alpha, 255, 165, 0)
        overlayView?.setBackgroundColor(orangeColor)
        
        params?.let {
            try {
                windowManager?.updateViewLayout(overlayView, it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
    }
}