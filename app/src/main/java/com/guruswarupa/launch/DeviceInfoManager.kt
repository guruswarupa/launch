package com.guruswarupa.launch

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class DeviceInfoManager(private val context: Context) {

    fun getCpuModel(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String? = reader.readLine()
            var model = "Unknown"
            while (line != null) {
                if (line.contains("Hardware") || line.contains("model name")) {
                    model = line.split(":")[1].trim()
                    break
                }
                line = reader.readLine()
            }
            reader.close()
            // If we didn't find it in cpuinfo (common on some newer Android versions due to restrictions),
            // return Build.BOARD or Build.HARDWARE as fallback
            if (model == "Unknown") {
                return Build.BOARD
            }
            model
        } catch (_: Exception) {
            Build.BOARD
        }
    }

    fun getCpuTemperature(): Float {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/class/thermal/thermal_zone1/temp"
        )

        for (path in thermalPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val reader = BufferedReader(FileReader(file))
                    val line = reader.readLine()
                    reader.close()
                    if (line != null) {
                        var temp = line.toFloatOrNull() ?: continue
                        // Often values are in millidegrees
                        if (temp > 1000) {
                            temp /= 1000f
                        }
                        return temp
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }
        return -1f // Not valid
    }

    fun getRamUsage(): Pair<Long, Long> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRam = memoryInfo.totalMem
        val availableRam = memoryInfo.availMem
        val usedRam = totalRam - availableRam
        
        return usedRam to totalRam
    }

    fun getStorageUsage(): Pair<Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val totalStorage = totalBlocks * blockSize
            val availableStorage = availableBlocks * blockSize
            val usedStorage = totalStorage - availableStorage
            
            usedStorage to totalStorage
        } catch (_: Exception) {
            0L to 0L
        }
    }
    
    fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format("%.1f", gb)
    }

    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    fun getKernelVersion(): String {
        return try {
            System.getProperty("os.version") ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun getBuildNumber(): String {
        return Build.DISPLAY
    }

    fun getHardwareInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (${Build.HARDWARE})"
    }

    fun getUptime(): String {
        val uptimeMillis = android.os.SystemClock.elapsedRealtime()
        val seconds = (uptimeMillis / 1000) % 60
        val minutes = (uptimeMillis / (1000 * 60)) % 60
        val hours = (uptimeMillis / (1000 * 60 * 60)) % 24
        val days = (uptimeMillis / (1000 * 60 * 60 * 24))
        
        return if (days > 0) {
            String.format("%dd %dh %dm", days, hours, minutes)
        } else if (hours > 0) {
            String.format("%dh %dm", hours, minutes)
        } else {
            String.format("%dm %ds", minutes, seconds)
        }
    }
}
