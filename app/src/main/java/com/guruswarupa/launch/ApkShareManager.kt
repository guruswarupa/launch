
package com.guruswarupa.launch

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.util.Collections

class ApkShareManager(private val context: Context) {

    fun showApkSharingDialog() {
        val installedApps = getInstalledApps()
        val appNames = installedApps.map { "${it.first} (${it.second})" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Share APK")
            .setItems(appNames) { _, which ->
                val selectedApp = installedApps[which]
                showSharingMethodDialog(selectedApp.second, selectedApp.first)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getInstalledApps(): List<Pair<String, String>> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { app ->
                // Filter out system apps unless they're user-installed
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            .map { app ->
                val appName = packageManager.getApplicationLabel(app).toString()
                Pair(appName, app.packageName)
            }
            .sortedBy { it.first }
    }

    private fun showSharingMethodDialog(packageName: String, appName: String) {
        val methods = arrayOf("Wi-Fi Direct", "Bluetooth", "Other Apps")

        AlertDialog.Builder(context)
            .setTitle("Share $appName via")
            .setItems(methods) { _, which ->
                when (which) {
                    0 -> shareViaWifiDirect(packageName, appName)
                    1 -> shareViaBluetooth(packageName, appName)
                    2 -> shareViaOtherApps(packageName, appName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareViaWifiDirect(packageName: String, appName: String) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(context, "Please enable Wi-Fi first", Toast.LENGTH_SHORT).show()
            return
        }

        // For Wi-Fi Direct, we'll use the standard sharing mechanism with a custom intent
        val apkFile = extractApkFile(packageName, appName)
        if (apkFile != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, getFileUri(apkFile))
                putExtra(Intent.EXTRA_TEXT, "Sharing $appName APK via Wi-Fi")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share $appName APK via Wi-Fi")
            context.startActivity(chooser)
        }
    }

    private fun shareViaBluetooth(packageName: String, appName: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        val apkFile = extractApkFile(packageName, appName)
        if (apkFile != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, getFileUri(apkFile))
                putExtra(Intent.EXTRA_TEXT, "Sharing $appName APK via Bluetooth")
                setPackage("com.android.bluetooth")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general sharing if Bluetooth package not found
                shareViaOtherApps(packageName, appName)
            }
        }
    }

    private fun shareViaOtherApps(packageName: String, appName: String) {
        val apkFile = extractApkFile(packageName, appName)
        if (apkFile != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, getFileUri(apkFile))
                putExtra(Intent.EXTRA_TEXT, "Sharing $appName APK")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share $appName APK")
            context.startActivity(chooser)
        }
    }

    private fun extractApkFile(packageName: String, appName: String): File? {
        try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val sourceApk = File(applicationInfo.sourceDir)

            // Create a temporary file in the app's cache directory
            val cacheDir = File(context.cacheDir, "shared_apks")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val destinationFile = File(cacheDir, "${appName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}.apk")

            // Copy the APK file
            FileInputStream(sourceApk).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            return destinationFile
        } catch (e: Exception) {
            Toast.makeText(context, "Error extracting APK: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private fun getFileUri(file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
    }

    private fun getWifiIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
