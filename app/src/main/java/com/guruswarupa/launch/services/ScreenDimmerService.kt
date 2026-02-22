package com.guruswarupa.launch.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.guruswarupa.launch.managers.ServiceNotificationManager

class ScreenDimmerService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val SERVICE_NAME = "Screen Dimmer"
        private const val ACTION_UPDATE_DIMMER = "com.guruswarupa.launch.UPDATE_DIMMER"
        private const val EXTRA_DIM_LEVEL = "extra_dim_level"

        fun startService(context: Context, dimLevel: Int) {
            val intent = Intent(context, ScreenDimmerService::class.java).apply {
                putExtra(EXTRA_DIM_LEVEL, dimLevel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, ScreenDimmerService::class.java))
        }

        fun updateDimLevel(context: Context, dimLevel: Int) {
            val intent = Intent(context, ScreenDimmerService::class.java).apply {
                action = ACTION_UPDATE_DIMMER
                putExtra(EXTRA_DIM_LEVEL, dimLevel)
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
        val dimLevel = intent?.getIntExtra(EXTRA_DIM_LEVEL, 50) ?: 50
        
        if (intent?.action == ACTION_UPDATE_DIMMER) {
            updateOverlay(dimLevel)
        } else {
            showOverlay(dimLevel)
        }
        
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayView != null && params != null) {
            try {
                windowManager?.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showOverlay(dimLevel: Int) {
        if (overlayView != null) {
            updateOverlay(dimLevel)
            return
        }

        overlayView = View(this)
        overlayView?.setBackgroundColor(Color.BLACK)
        
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

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = (dimLevel / 100f).coerceIn(0f, 0.9f)
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

    private fun updateOverlay(dimLevel: Int) {
        params?.let {
            it.alpha = (dimLevel / 100f).coerceIn(0f, 0.9f)
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
