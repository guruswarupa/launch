package com.guruswarupa.launch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class NetworkStatsWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    private var isInitialized = false
    private lateinit var widgetView: View
    private lateinit var downloadText: TextView
    private lateinit var uploadText: TextView
    private lateinit var pingText: TextView
    private lateinit var jitterText: TextView
    private lateinit var statusText: TextView
    private lateinit var runButton: Button
    private lateinit var wifiIpText: TextView
    
    private val networkStatsManager = NetworkStatsManager(context)
    private val handler = Handler(Looper.getMainLooper())

    // Auto-update runnable for data usage
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateDataUsage()
                handler.postDelayed(this, 10000) // Update every 10 seconds
            }
        }
    }

    private lateinit var mobileUsageText: TextView
    private lateinit var wifiUsageText: TextView

    fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_network_stats, container, false)
        container.addView(widgetView)
        
        downloadText = widgetView.findViewById(R.id.download_speed_text)
        uploadText = widgetView.findViewById(R.id.upload_speed_text)
        pingText = widgetView.findViewById(R.id.ping_text)
        jitterText = widgetView.findViewById(R.id.jitter_text)
        mobileUsageText = widgetView.findViewById(R.id.mobile_usage_text)
        wifiUsageText = widgetView.findViewById(R.id.wifi_usage_text)
        statusText = widgetView.findViewById(R.id.network_status_text)
        runButton = widgetView.findViewById(R.id.run_speedtest_button)
        wifiIpText = widgetView.findViewById(R.id.wifi_ip_text)

        runButton.setOnClickListener {
            startSpeedTest()
        }

        updateDataUsage()
        isInitialized = true
        
        // Start auto-updates
        handler.post(updateRunnable)
    }
    
    // Periodically update data usage? For now, we update on init and when speedtest runs or just stick to init. 
    // Let's add a periodic update along with the speedtest or just a separate update.
    
    private fun updateDataUsage() {
        val (mobile, wifi) = networkStatsManager.getNetworkUsage()
        mobileUsageText.text = networkStatsManager.formatDataUsage(mobile)
        wifiUsageText.text = networkStatsManager.formatDataUsage(wifi)
        wifiIpText.text = networkStatsManager.getWifiIpAddress()
    }
    
    private fun startSpeedTest() {
        runButton.isEnabled = false
        runButton.text = "Running..."
        statusText.text = "Initializing..."
        updateDataUsage() // Update stats before test
        
        networkStatsManager.runSpeedTest(
            callback = { result ->
                handler.post {
                    downloadText.text = "${result.downloadSpeedMbps} Mbps"
                    uploadText.text = "${result.uploadSpeedMbps} Mbps"
                    pingText.text = "${result.pingMs} ms"
                    jitterText.text = "${result.jitterMs} ms"
                    
                    statusText.text = "Test Completed"
                    runButton.isEnabled = true
                    runButton.text = "Run Speedtest"
                }
            },
            onProgress = { message ->
                handler.post {
                    statusText.text = message
                }
            },
            onError = { error ->
                handler.post {
                    statusText.text = error
                    runButton.isEnabled = true
                    runButton.text = "Run Speedtest"
                }
            }
        )
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
