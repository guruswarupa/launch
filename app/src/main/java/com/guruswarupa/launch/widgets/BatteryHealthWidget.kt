package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.BatteryManager
import java.util.Locale

class BatteryHealthWidget(
    private val context: Context,
    private val container: LinearLayout
) {
    private var isInitialized = false
    private lateinit var widgetView: View

    private lateinit var batteryPercentageText: TextView
    private lateinit var timeRemainingText: TextView
    private lateinit var chargingSpeedText: TextView
    private lateinit var voltageText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var healthStatusText: TextView

    private val batteryManager = BatteryManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateDisplay()
                handler.postDelayed(this, 5000)
            }
        }
    }

    fun initialize() {
        if (isInitialized) return

        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_battery_health, container, false)
        container.addView(widgetView)

        batteryPercentageText = widgetView.findViewById(R.id.battery_percentage_text)
        timeRemainingText = widgetView.findViewById(R.id.time_remaining_text)
        chargingSpeedText = widgetView.findViewById(R.id.charging_speed_text)
        voltageText = widgetView.findViewById(R.id.voltage_text)
        temperatureText = widgetView.findViewById(R.id.temperature_text)
        healthStatusText = widgetView.findViewById(R.id.health_status_text)

        updateDisplay()

        handler.post(updateRunnable)

        isInitialized = true
    }

    private fun updateDisplay() {
        val batteryInfo = batteryManager.getBatteryHealthInfo()

        batteryPercentageText.text = String.format(Locale.getDefault(), "%d%%", batteryInfo.percentage)

        timeRemainingText.text = batteryInfo.timeRemaining ?: "--"

        if (batteryInfo.isCharging) {
            chargingSpeedText.text = String.format(Locale.getDefault(), "%d mA", batteryInfo.chargingSpeed)
            chargingSpeedText.setTextColor(context.getColor(R.color.nord10))
        } else {
            chargingSpeedText.text = context.getString(R.string.status_not_charging)
            chargingSpeedText.setTextColor(context.getColor(R.color.widget_text_secondary))
        }

        voltageText.text = String.format(Locale.getDefault(), "%d mV", batteryInfo.voltage)

        temperatureText.text = String.format(Locale.getDefault(), "%.1f°C", batteryInfo.temperature)

        healthStatusText.text = batteryInfo.health
        healthStatusText.setTextColor(
            when (batteryInfo.health) {
                "Good" -> context.getColor(R.color.nord14)
                "Overheat", "Dead", "Over Voltage" -> context.getColor(R.color.nord13)
                else -> context.getColor(R.color.widget_text)
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
