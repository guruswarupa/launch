package com.guruswarupa.launch

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import java.io.PrintWriter
import java.io.StringWriter

class LaunchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
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
