package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.views.WeatherHourlyForecastChartView
import com.guruswarupa.launch.utils.WeatherManager

class WeatherForecastWidget(
    private val context: Context,
    private val container: LinearLayout
) : InitializableWidget {
    private val weatherManager = WeatherManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private lateinit var widgetView: View
    private lateinit var weatherIcon: ImageView
    private lateinit var currentTemperatureText: TextView
    private lateinit var currentDescriptionText: TextView
    private lateinit var locationText: TextView
    private lateinit var alertsText: TextView
    private lateinit var dailyContainer: LinearLayout
    private lateinit var hourlyChartView: WeatherHourlyForecastChartView

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                refreshForecast()
                handler.postDelayed(this, 45 * 60 * 1000L)
            }
        }
    }

    override fun initialize() {
        if (isInitialized) return
        widgetView = LayoutInflater.from(context).inflate(R.layout.widget_weather_forecast, container, false)
        container.addView(widgetView)
        weatherIcon = widgetView.findViewById(R.id.forecast_weather_icon)
        currentTemperatureText = widgetView.findViewById(R.id.forecast_current_temperature)
        currentDescriptionText = widgetView.findViewById(R.id.forecast_current_description)
        locationText = widgetView.findViewById(R.id.forecast_location_text)
        alertsText = widgetView.findViewById(R.id.forecast_alerts_text)
        dailyContainer = widgetView.findViewById(R.id.daily_forecast_container)
        hourlyChartView = widgetView.findViewById(R.id.hourly_forecast_chart)
        val refreshListener = View.OnClickListener { refreshForecast() }
        widgetView.setOnClickListener(refreshListener)
        val settingsListener = View.OnLongClickListener {
            weatherManager.showWeatherSettings()
            true
        }
        widgetView.setOnLongClickListener(settingsListener)
        refreshForecast()
        isInitialized = true
        handler.post(refreshRunnable)
    }

    private fun refreshForecast() {
        val location = weatherManager.getConfiguredLocation()
        if (location.isNullOrBlank()) {
            showLocationPrompt()
            return
        }
        weatherManager.fetchWeatherForecast(
            onSuccess = { forecast ->
                bindForecast(forecast)
            },
            onError = {
                showUnavailableState()
            }
        )
    }

    private fun bindForecast(forecast: WeatherManager.WeatherForecast) {
        weatherIcon.setImageResource(weatherManager.getWeatherIconResource(forecast.currentWeatherCode))
        currentTemperatureText.text = formatTemperature(forecast.currentTemperature)
        currentDescriptionText.text = forecast.currentDescription
        locationText.text = forecast.location
        alertsText.text = if (forecast.alerts.isEmpty()) {
            context.getString(R.string.weather_forecast_alerts_clear)
        } else {
            forecast.alerts.joinToString(separator = "\n") { "${it.title}: ${it.detail}" }
        }
        bindDailyForecasts(forecast.dailyForecasts)
        hourlyChartView.setForecastEntries(
            forecast.hourlyForecasts,
            weatherManager.getTemperatureUnit() == "fahrenheit"
        )
    }

    private fun bindDailyForecasts(days: List<WeatherManager.ForecastDay>) {
        dailyContainer.removeAllViews()
        days.forEach { day ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_weather_forecast_day, dailyContainer, false)
            row.findViewById<TextView>(R.id.forecast_day_label).text = day.dayLabel
            row.findViewById<ImageView>(R.id.forecast_day_icon).setImageResource(
                weatherManager.getWeatherIconResource(day.weatherCode)
            )
            row.findViewById<TextView>(R.id.forecast_day_description).text =
                weatherManager.describeOpenMeteoWeatherCode(day.weatherCode)
            row.findViewById<TextView>(R.id.forecast_day_range).text =
                context.getString(
                    R.string.weather_forecast_day_range,
                    formatTemperature(day.highTemperature),
                    formatTemperature(day.lowTemperature)
                )
            dailyContainer.addView(row)
        }
    }

    private fun showLocationPrompt() {
        weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
        currentTemperatureText.text = "--"
        currentDescriptionText.text = context.getString(R.string.tap_to_enter_location)
        locationText.text = context.getString(R.string.weather_forecast_location_missing)
        alertsText.text = context.getString(R.string.weather_forecast_hold_to_set_location)
        dailyContainer.removeAllViews()
        hourlyChartView.setForecastEntries(emptyList(), weatherManager.getTemperatureUnit() == "fahrenheit")
    }

    private fun showUnavailableState() {
        weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
        currentTemperatureText.text = "--"
        currentDescriptionText.text = context.getString(R.string.weather_unavailable)
        locationText.text = weatherManager.getConfiguredLocation() ?: context.getString(R.string.weather_forecast_location_missing)
        alertsText.text = context.getString(R.string.weather_forecast_refresh_hint)
        dailyContainer.removeAllViews()
        hourlyChartView.setForecastEntries(emptyList(), weatherManager.getTemperatureUnit() == "fahrenheit")
    }

    private fun formatTemperature(temperature: Int): String {
        return if (weatherManager.getTemperatureUnit() == "fahrenheit") {
            context.getString(R.string.weather_temp_value_fahrenheit, temperature)
        } else {
            context.getString(R.string.weather_temp_value_celsius, temperature)
        }
    }

    fun onResume() {
        if (!isInitialized) return
        refreshForecast()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    fun onPause() {
        handler.removeCallbacks(refreshRunnable)
    }

    fun cleanup() {
        handler.removeCallbacks(refreshRunnable)
    }
}
