package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ApkShareManager(private val context: Context) {

    fun showApkSharingDialog() {
        AlertDialog.Builder(context)
            .setTitle("What would you like to share?")
            .setItems(arrayOf("Share an App (APK)", "Share a File")) { _, which ->
                when (which) {
                    0 -> showAppSharingDialog()
                    1 -> showFileSharingDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppSharingDialog() {
        val installedApps = getInstalledApps()
        if (installedApps.isEmpty()) {
            Toast.makeText(context, "No apps available to share", Toast.LENGTH_SHORT).show()
            return
        }

        val appNames = installedApps.map { it.first }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Select App to Share")
            .setItems(appNames) { _, which ->
                val selectedApp = installedApps[which]
                shareApk(selectedApp.second, selectedApp.first)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFileSharingDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        try {
            (context as MainActivity).startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(context, "No file manager available", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleFilePickerResult(uri: Uri?) {
        if (uri != null) {
            shareFile(uri)
        } else {
            Toast.makeText(context, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Sharing file")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share file")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val FILE_PICKER_REQUEST_CODE = 1001
    }

    private fun getInstalledApps(): List<Pair<String, String>> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { app ->
                // Include user-installed apps and updated system apps
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            .mapNotNull { app ->
                try {
                    val appName = packageManager.getApplicationLabel(app).toString()
                    Pair(appName, app.packageName)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.first }
    }

    private fun shareApk(packageName: String, appName: String) {
        try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val sourceApk = File(applicationInfo.sourceDir)

            if (!sourceApk.exists()) {
                Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Copy APK to cache directory
            val cacheDir = File(context.cacheDir, "shared_apks")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val sanitizedAppName = appName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val copiedApk = File(cacheDir, "${sanitizedAppName}.apk")

            // Copy the APK file
            FileInputStream(sourceApk).use { input ->
                FileOutputStream(copiedApk).use { output ->
                    input.copyTo(output)
                }
            }

            // Create URI for sharing
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    copiedApk
                )
            } else {
                Uri.fromFile(copiedApk)
            }

            // Create sharing intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_TEXT, "Sharing $appName APK")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Create chooser
            val chooser = Intent.createChooser(shareIntent, "Share $appName APK")
            context.startActivity(chooser)

        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}