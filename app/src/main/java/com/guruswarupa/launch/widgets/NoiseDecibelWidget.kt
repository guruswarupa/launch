package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.NoiseDecibelManager
import java.text.DecimalFormat

class NoiseDecibelWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private lateinit var noiseManager: NoiseDecibelManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var decibelText: TextView
    private lateinit var noiseLevelText: TextView
    private lateinit var decibelIndicator: View
    private lateinit var toggleButton: Button
    private lateinit var widgetContainer: LinearLayout
    private lateinit var noMicrophoneText: TextView
    private lateinit var noPermissionText: TextView
    private lateinit var widgetView: View
    
    private val df = DecimalFormat("#.#")
    
    companion object {
        private const val PREF_NOISE_ENABLED = "noise_decibel_enabled"
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Periodic check to ensure recording is still active if it should be
            if (isInitialized && !noiseManager.isRecording()) {
                val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
                if (isEnabled && hasMicrophonePermission() && noiseManager.hasMicrophone()) {
                    noiseManager.startRecording()
                }
            }
            handler.postDelayed(this, 1000) // Check every second
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_noise_decibel, container, false)
        container.addView(widgetView)
        
        decibelText = widgetView.findViewById(R.id.decibel_text)
        noiseLevelText = widgetView.findViewById(R.id.noise_level_text)
        decibelIndicator = widgetView.findViewById(R.id.decibel_indicator)
        toggleButton = widgetView.findViewById(R.id.toggle_noise_button)
        widgetContainer = widgetView.findViewById(R.id.noise_container)
        noMicrophoneText = widgetView.findViewById(R.id.no_microphone_text)
        noPermissionText = widgetView.findViewById(R.id.no_permission_text)
        
        noiseManager = NoiseDecibelManager(context)
        
        noiseManager.setOnDecibelChangedListener { decibel ->
            handler.post {
                updateDecibelDisplay(decibel)
            }
        }
        
        toggleButton.setOnClickListener {
            toggleNoiseAnalyzer()
        }
        
        // Ensure widget is disabled by default - explicitly set to false if not already set
        if (!sharedPreferences.contains(PREF_NOISE_ENABLED)) {
            sharedPreferences.edit { putBoolean(PREF_NOISE_ENABLED, false) }
        }
        
        val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
        updateUI(isEnabled)
        
        isInitialized = true
    }
    
    private fun toggleNoiseAnalyzer() {
        val currentState = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
        val newState = !currentState
        sharedPreferences.edit { putBoolean(PREF_NOISE_ENABLED, newState) }
        updateUI(newState)
    }
    
    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {
            widgetContainer.visibility = View.VISIBLE
            toggleButton.setText(R.string.noise_button_disable)
            
            // Check permission first
            if (!hasMicrophonePermission()) {
                setupWithoutPermission()
                return
            }
            
            // Check if microphone is available
            if (noiseManager.hasMicrophone()) {
                setupWithMicrophone()
            } else {
                setupWithoutMicrophone()
            }
        } else {
            widgetContainer.visibility = View.GONE
            toggleButton.setText(R.string.noise_button_enable)
            handler.removeCallbacks(updateRunnable)
            noiseManager.stopRecording()
        }
    }
    
    private fun setupWithMicrophone() {
        noMicrophoneText.visibility = View.GONE
        noPermissionText.visibility = View.GONE
        decibelText.visibility = View.VISIBLE
        noiseLevelText.visibility = View.VISIBLE
        decibelIndicator.visibility = View.VISIBLE
        
        if (noiseManager.startRecording()) {
            handler.post(updateRunnable)
        } else {
            setupWithoutMicrophone()
        }
    }
    
    private fun setupWithoutMicrophone() {
        noMicrophoneText.visibility = View.VISIBLE
        noPermissionText.visibility = View.GONE
        decibelText.visibility = View.GONE
        noiseLevelText.visibility = View.GONE
        decibelIndicator.visibility = View.GONE
        noiseManager.stopRecording()
    }
    
    private fun setupWithoutPermission() {
        noMicrophoneText.visibility = View.GONE
        noPermissionText.visibility = View.VISIBLE
        decibelText.visibility = View.GONE
        noiseLevelText.visibility = View.GONE
        decibelIndicator.visibility = View.GONE
        noiseManager.stopRecording()
        
        // Make permission text clickable to open settings
        noPermissionText.setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }
    
    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun updateDecibelDisplay(decibel: Double) {
        decibelText.text = context.getString(R.string.decibel_format, df.format(decibel))
        
        // Update noise level text and color based on decibel value
        val levelRes = when {
            decibel < 30 -> R.string.noise_level_quiet
            decibel < 50 -> R.string.noise_level_moderate
            decibel < 70 -> R.string.noise_level_loud
            else -> R.string.noise_level_very_loud
        }
        
        val colorRes = when {
            decibel < 30 -> R.color.nord7
            decibel < 50 -> R.color.nord8
            decibel < 70 -> R.color.nord11
            else -> R.color.nord12
        }
        
        noiseLevelText.setText(levelRes)
        noiseLevelText.setTextColor(ContextCompat.getColor(context, colorRes))
        
        // Update indicator bar width (0-100% based on 0-120 dB range)
        val indicatorWidth = (decibel / 120.0).coerceIn(0.0, 1.0)
        val parentWidth = decibelIndicator.parent as? View
        parentWidth?.let { parent ->
            val layoutParams = decibelIndicator.layoutParams
            layoutParams.width = (parent.width * indicatorWidth).toInt()
            decibelIndicator.layoutParams = layoutParams
            
            // Update indicator color based on level
            decibelIndicator.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        }
    }
    
    fun onResume() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
            if (isEnabled && hasMicrophonePermission() && noiseManager.hasMicrophone()) {
                if (!noiseManager.isRecording()) {
                    noiseManager.startRecording()
                    handler.post(updateRunnable)
                }
            }
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            noiseManager.stopRecording()
        }
    }
    
    fun onPermissionGranted() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_NOISE_ENABLED, false)
            if (isEnabled) {
                updateUI(true)
            }
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        if (isInitialized) {
            noiseManager.stopRecording()
            noiseManager.cleanup()
        }
    }
}
