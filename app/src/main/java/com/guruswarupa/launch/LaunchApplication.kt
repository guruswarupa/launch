package com.guruswarupa.launch

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.BackTapService
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.ScreenDimmerService
import com.guruswarupa.launch.services.ShakeDetectionService
import java.io.PrintWriter
import java.io.StringWriter

class LaunchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        initServices()
    }

    private fun initServices() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        
        // Start Screen Dimmer if enabled
        val isDimmerEnabled = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
        if (isDimmerEnabled && Settings.canDrawOverlays(this)) {
            val dimLevel = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 50)
            ScreenDimmerService.startService(this, dimLevel)
        }
        
        // Start Night Mode if enabled
        val isNightModeEnabled = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
        if (isNightModeEnabled && Settings.canDrawOverlays(this)) {
            val intensity = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
            NightModeService.startService(this, intensity)
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                sendCrashReport(throwable)
            } catch (e: Exception) {
                Log.e("LaunchApplication", "Failed to send crash report", e)
            } finally {
                // Let the default handler deal with the crash (log it and terminate)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun sendCrashReport(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val deviceInfo = """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE}
            SDK: ${Build.VERSION.SDK_INT}
            App Version: ${getAppVersion()}
        """.trimIndent()

        val emailBody = "The app has crashed with the following exception:\n\n$deviceInfo\n\nStack Trace:\n$stackTrace"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Launch App Crash Report: ${throwable.javaClass.simpleName}")
            putExtra(Intent.EXTRA_TEXT, emailBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // We check if there's an app to handle the intent to avoid another crash
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            "${pInfo.versionName} ($versionCode)"
        } catch (_: Exception) {
            "Unknown"
        }
    }
}
