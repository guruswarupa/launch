package com.guruswarupa.launch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class DeviceInfoWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    private var isInitialized = false
    private lateinit var widgetView: View
    private lateinit var cpuModelText: TextView
    private lateinit var cpuTempText: TextView
    private lateinit var ramUsageText: TextView
    private lateinit var ramProgressBar: ProgressBar
    private lateinit var storageUsageText: TextView
    private lateinit var storageProgressBar: ProgressBar
    
    private lateinit var androidVersionText: TextView
    private lateinit var uptimeText: TextView
    private lateinit var hardwareText: TextView
    private lateinit var kernelText: TextView
    
    private val deviceInfoManager = DeviceInfoManager(context)
    private val handler = Handler(Looper.getMainLooper())
    
    // Auto-update runnable
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateDisplay()
                handler.postDelayed(this, 5000) // Update every 5 seconds
            }
        }
    }

    fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_device_info, container, false)
        container.addView(widgetView)
        
        cpuModelText = widgetView.findViewById(R.id.cpu_model_text)
        cpuTempText = widgetView.findViewById(R.id.cpu_temp_text)
        ramUsageText = widgetView.findViewById(R.id.ram_usage_text)
        ramProgressBar = widgetView.findViewById(R.id.ram_progress_bar)
        storageUsageText = widgetView.findViewById(R.id.storage_usage_text)
        storageProgressBar = widgetView.findViewById(R.id.storage_progress_bar)
        
        androidVersionText = widgetView.findViewById(R.id.android_version_text)
        uptimeText = widgetView.findViewById(R.id.uptime_text)
        hardwareText = widgetView.findViewById(R.id.hardware_text)
        kernelText = widgetView.findViewById(R.id.kernel_text)

        // Initial update
        cpuModelText.text = deviceInfoManager.getCpuModel()
        androidVersionText.text = deviceInfoManager.getAndroidVersion()
        hardwareText.text = deviceInfoManager.getHardwareInfo()
        kernelText.text = deviceInfoManager.getKernelVersion()
        
        updateDisplay()
        
        // Start auto-updates
        handler.post(updateRunnable)

        isInitialized = true
    }
    
    private fun updateDisplay() {
        // ... RAM and Storage updates ...
        uptimeText.text = deviceInfoManager.getUptime()
        // RAM
        val (usedRam, totalRam) = deviceInfoManager.getRamUsage()
        val usedRamGb = deviceInfoManager.formatBytes(usedRam)
        val totalRamGb = deviceInfoManager.formatBytes(totalRam)
        ramUsageText.text = "$usedRamGb / $totalRamGb GB"
        ramProgressBar.progress = ((usedRam.toDouble() / totalRam.toDouble()) * 100).toInt()
        
        // Storage
        val (usedStorage, totalStorage) = deviceInfoManager.getStorageUsage()
        val usedStorageGb = deviceInfoManager.formatBytes(usedStorage)
        val totalStorageGb = deviceInfoManager.formatBytes(totalStorage)
        storageUsageText.text = "$usedStorageGb / $totalStorageGb GB"
        
        if (totalStorage > 0) {
            storageProgressBar.progress = ((usedStorage.toDouble() / totalStorage.toDouble()) * 100).toInt()
        }
        
        // CPU Temp
        val temp = deviceInfoManager.getCpuTemperature()
        if (temp > 0) {
            cpuTempText.text = "${String.format("%.1f", temp)}°C"
        } else {
            cpuTempText.text = "--"
        }
    }
    
    fun onResume() {
        if (isInitialized) {
            handler.post(updateRunnable)
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
    }
}
