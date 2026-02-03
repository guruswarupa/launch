package com.guruswarupa.launch

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class NetworkStatsManager(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    data class SpeedTestResult(
        val downloadSpeedMbps: Float,
        val uploadSpeedMbps: Float, // Simplified, maybe just mock or basic check
        val pingMs: Long,
        val jitterMs: Long
    )

    fun runSpeedTest(callback: (SpeedTestResult) -> Unit, onProgress: (String) -> Unit, onError: (String) -> Unit) {
        onProgress("Starting speedtest...")
        
        executor.execute {
            try {
                // 1. Ping Test
                onProgress("Measuring latency...")
                val (ping, jitter) = measurePing()
                
                // 2. Download Test
                onProgress("Measuring download speed...")
                val downloadSpeed = measureDownloadSpeed()
                
                // 3. Upload Test
                onProgress("Measuring upload speed...")
                val uploadSpeed = measureUploadSpeed()

                handler.post {
                    callback(SpeedTestResult(downloadSpeed, uploadSpeed, ping, jitter))
                }
            } catch (e: Exception) {
                Log.e("NetworkStatsManager", "Speedtest failed", e)
                handler.post {
                    onError("Failed: ${e.message}")
                }
            }
        }
    }

    private fun measurePing(): Pair<Long, Long> {
        val host = "8.8.8.8"
        val pings = mutableListOf<Long>()
        val count = 5
        
        for (i in 0 until count) {
            val start = System.currentTimeMillis()
            val isReachable = java.net.InetAddress.getByName(host).isReachable(1000)
            val end = System.currentTimeMillis()
            if (isReachable) {
                pings.add(end - start)
            }
        }

        if (pings.isEmpty()) return 0L to 0L

        val avgPing = pings.average().toLong()
        
        // Calculate Jitter (average deviation from the mean)
        var jitterSum = 0.0
        for (p in pings) {
            jitterSum += Math.abs(p - avgPing)
        }
        val jitter = (jitterSum / pings.size).toLong()

        return avgPing to jitter
    }

    private fun measureDownloadSpeed(): Float {
        val urls = listOf(
            "https://speed.cloudflare.com/__down?bytes=10485760", // 10MB Cloudflare
            "https://dl.google.com/android/repository/platform-tools-latest-linux.zip",
            "http://speedtest.tele2.net/10MB.zip" // Fallback HTTP
        )
        
        for (fileUrl in urls) {
            var totalBytesRead = 0L
            val maxDuration = 5000L // 5 seconds max
            
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 3000
                connection.readTimeout = 5000
                connection.connect()
                
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    continue
                }
                
                val inputStream: InputStream = connection.inputStream
                val buffer = ByteArray(64 * 1024) 
                var n: Int
                
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < maxDuration) {
                    n = inputStream.read(buffer)
                    if (n == -1) break
                    totalBytesRead += n
                }
                
                val endTime = System.currentTimeMillis()
                val timeSeconds = (endTime - startTime) / 1000.0
                
                inputStream.close()
                connection.disconnect()
                
                if (timeSeconds > 0.1 && totalBytesRead > 0) {
                    val bitsLoaded = totalBytesRead * 8
                    val mbps = (bitsLoaded / timeSeconds) / 1_000_000.0
                    return (mbps * 10).roundToInt() / 10.0f
                }
            } catch (e: Exception) {
                Log.e("NetworkStatsManager", "Download test failed for $fileUrl", e)
            }
        }
        return 0f
    }
    
    private fun measureUploadSpeed(): Float {
        val uploadUrl = "http://speedtest.tele2.net/upload.php"
        var totalBytesWritten = 0L
        val maxDuration = 5000L
        
        try {
            val url = URL(uploadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            // 10MB payload for upload test to ensure we have enough data for high speeds
            val payloadSize = 10 * 1024 * 1024 
            val payload = ByteArray(payloadSize)
            java.util.Random().nextBytes(payload)
            
            connection.setFixedLengthStreamingMode(payloadSize)
            val startTime = System.currentTimeMillis()
            val outputStream = connection.outputStream
            
            val bufferSize = 64 * 1024 // 64KB write buffer
            var offset = 0
            while (offset < payloadSize && (System.currentTimeMillis() - startTime < maxDuration)) {
                 val count = Math.min(bufferSize, payloadSize - offset)
                 outputStream.write(payload, offset, count)
                 offset += count
                 totalBytesWritten += count
            }
            
            outputStream.flush()
            outputStream.close()
            
            val endTime = System.currentTimeMillis()
            val responseCode = connection.responseCode // Wait for response to ensure upload completed
            connection.disconnect()
            
            val timeSeconds = (endTime - startTime) / 1000.0
            if (timeSeconds <= 0.1) return 0f
            
            val bitsUploaded = totalBytesWritten * 8
            val mbps = (bitsUploaded / timeSeconds) / 1_000_000.0
            return (mbps * 10).roundToInt() / 10.0f
            
        } catch (e: Exception) {
             e.printStackTrace()
             return 0f
        }
    }
    
    fun getNetworkUsage(): Pair<Long, Long> {
        // Returns Pair(MobileBytes, WifiBytes) since boot
        val mobileRx = android.net.TrafficStats.getMobileRxBytes()
        val mobileTx = android.net.TrafficStats.getMobileTxBytes()
        val totalRx = android.net.TrafficStats.getTotalRxBytes()
        val totalTx = android.net.TrafficStats.getTotalTxBytes()
        
        val mobileTotal = if (mobileRx != -1L && mobileTx != -1L) mobileRx + mobileTx else 0L
        val grandTotal = if (totalRx != -1L && totalTx != -1L) totalRx + totalTx else 0L
        
        val wifiTotal = if (grandTotal > mobileTotal) grandTotal - mobileTotal else 0L
        
        return mobileTotal to wifiTotal
    }
    
    fun getWifiIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.contains("wlan") || networkInterface.name.contains("eth")) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress ?: "Unknown"
                        }
                    }
                }
            }
            "Not Connected"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun formatDataUsage(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }
}
