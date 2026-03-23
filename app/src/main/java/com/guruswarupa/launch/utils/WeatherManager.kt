package com.guruswarupa.launch.utils

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.guruswarupa.launch.R
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WeatherManager(private val context: android.content.Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private data class GeocodingResult(
        val latitude: Double,
        val longitude: Double,
        val displayName: String
    )

    private data class CachedWeather(
        val temperature: Int,
        val description: String,
        val weatherCode: Int,
        val timestamp: Long,
        val location: String
    )

    fun getTemperatureUnit(): String {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        return prefs.getString("weather_temperature_unit", "celsius") ?: "celsius"
    }

    private fun saveTemperatureUnit(unit: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        prefs.edit { putString("weather_temperature_unit", unit) }
    }

    private fun getStoredLocation(): String? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        return prefs.getString("weather_stored_location", null)
    }

    private fun saveLocation(location: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        prefs.edit {
            putString("weather_stored_location", location)
            putString("weather_stored_city_name", location)
        }
    }

    private fun getCachedWeather(location: String?): CachedWeather? {
        if (location.isNullOrBlank()) return null
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("weather_cached_timestamp", 0)
        val temperature = prefs.getInt("weather_cached_temperature", Int.MIN_VALUE)
        val description = prefs.getString("weather_cached_description", null)
        val weatherCode = prefs.getInt("weather_cached_weather_code", Int.MIN_VALUE)
        val cachedLocation = prefs.getString("weather_cached_location", null)
        if (timestamp == 0L || temperature == Int.MIN_VALUE || description == null || weatherCode == Int.MIN_VALUE || cachedLocation.isNullOrBlank()) {
            return null
        }
        if (cachedLocation != location) return null
        return CachedWeather(temperature, description, weatherCode, timestamp, cachedLocation)
    }

    private fun saveCachedWeather(temperature: Int, description: String, weatherCode: Int, location: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", android.content.Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit {
            putInt("weather_cached_temperature", temperature)
                .putString("weather_cached_description", description)
                .putInt("weather_cached_weather_code", weatherCode)
                .putLong("weather_cached_timestamp", currentTime)
                .putString("weather_cached_location", location)
        }
    }

    private fun isCacheValid(cachedWeather: CachedWeather, maxAgeMinutes: Long = 45): Boolean {
        val currentTime = System.currentTimeMillis()
        val ageMinutes = (currentTime - cachedWeather.timestamp) / (1000 * 60)
        return ageMinutes < maxAgeMinutes
    }

    private fun convertTemperatureToSelectedUnit(temperature: Int): Int = temperature

    private fun getTemperatureFormatString(): String {
        val unit = getTemperatureUnit()
        return if (unit == "fahrenheit") {
            context.getString(R.string.weather_temp_format_fahrenheit)
        } else {
            context.getString(R.string.weather_temp_format_celsius)
        }
    }

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, forcePrompt: Boolean = false) {
        val storedLocation = getStoredLocation()
        val cachedWeather = getCachedWeather(storedLocation)
        if (cachedWeather != null && isCacheValid(cachedWeather)) {
            handler.post {
                val convertedTemp = convertTemperatureToSelectedUnit(cachedWeather.temperature)
                val formatString = getTemperatureFormatString()
                weatherText.text = String.format(formatString, convertedTemp, cachedWeather.description)
                setWeatherIcon(weatherIcon, cachedWeather.weatherCode)
                setupRefreshListeners(weatherIcon, weatherText)
            }
            return
        }

        if (storedLocation.isNullOrBlank()) {
            handler.post {
                weatherText.text = context.getString(R.string.tap_to_enter_location)
                weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                setupRefreshListeners(weatherIcon, weatherText)
            }
            if (forcePrompt && !isSettingDialogVisible) {
                showWeatherSettings(weatherIcon, weatherText)
            }
            return
        }
        fetchWeatherFromOpenMeteo(weatherIcon, weatherText, storedLocation)
    }

    private fun fetchWeatherFromOpenMeteo(weatherIcon: ImageView, weatherText: TextView, location: String) {
        executor.execute {
            try {
                val geocodingResult = geocodeLocation(location) ?: throw Exception("Location not found")
                val unitParam = if (getTemperatureUnit() == "fahrenheit") "fahrenheit" else "celsius"
                val lat = String.format(Locale.US, "%.5f", geocodingResult.latitude)
                val lon = String.format(Locale.US, "%.5f", geocodingResult.longitude)
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&temperature_unit=$unitParam&timezone=auto"
                val response = URL(url).readText()
                val jsonObject = JSONObject(response)
                val currentWeather = jsonObject.getJSONObject("current_weather")
                val temperature = currentWeather.getDouble("temperature").roundToInt()
                val weatherCode = currentWeather.getInt("weathercode")
                val description = describeOpenMeteoWeatherCode(weatherCode)
                val displayName = if (geocodingResult.displayName.isNotBlank()) geocodingResult.displayName else location

                saveLocation(displayName)
                saveCachedWeather(temperature, description, weatherCode, displayName)

                handler.post {
                    val convertedTemp = convertTemperatureToSelectedUnit(temperature)
                    val formatString = getTemperatureFormatString()
                    weatherText.text = String.format(formatString, convertedTemp, description)
                    setWeatherIcon(weatherIcon, weatherCode)
                    setupRefreshListeners(weatherIcon, weatherText)
                }
            } catch (_: Exception) {
                handler.post {
                    weatherText.text = context.getString(R.string.weather_unavailable)
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                    setupRefreshListeners(weatherIcon, weatherText)
                }
            }
        }
    }

    private fun geocodeLocation(location: String): GeocodingResult? {
        val encodedLocation = try {
            java.net.URLEncoder.encode(location, "UTF-8")
        } catch (_: Exception) {
            location
        }
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedLocation&count=1"
        val response = URL(url).readText()
        val jsonObject = JSONObject(response)
        val results = jsonObject.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val firstResult = results.getJSONObject(0)
        val latitude = firstResult.optDouble("latitude", Double.NaN)
        val longitude = firstResult.optDouble("longitude", Double.NaN)
        val name = firstResult.optString("name", location)
        val country = firstResult.optString("country", "")
        if (latitude.isNaN() || longitude.isNaN()) {
            return null
        }
        val displayName = if (country.isNotBlank()) "$name, $country" else name
        return GeocodingResult(latitude, longitude, displayName)
    }

    private fun describeOpenMeteoWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear"
            in 1..3 -> "Cloudy"
            45, 48 -> "Fog"
            in 51..67, in 80..82 -> "Rain"
            in 71..77, in 85..86 -> "Snow"
            in 95..99 -> "Thunderstorm"
            else -> "Cloudy"
        }
    }

    private fun setWeatherIcon(weatherIcon: ImageView, weatherCode: Int) {
        val iconResource = when (weatherCode) {
            in 200..232 -> R.drawable.ic_weather_rainy
            in 300..321 -> R.drawable.ic_weather_rainy
            in 500..531 -> R.drawable.ic_weather_rainy
            in 600..622 -> R.drawable.ic_weather_snowy
            in 701..781 -> R.drawable.ic_weather_cloudy
            800 -> R.drawable.ic_weather_sunny
            in 801..804 -> R.drawable.ic_weather_cloudy
            else -> R.drawable.ic_weather_cloudy
        }
        weatherIcon.setImageResource(iconResource)
    }

    private fun setupRefreshListeners(weatherIcon: ImageView, weatherText: TextView) {
        val refreshClickListener = View.OnClickListener {
            val storedLocation = getStoredLocation()
            if (storedLocation.isNullOrBlank()) {
                showWeatherSettings(weatherIcon, weatherText)
            } else {
                fetchWeatherFromOpenMeteo(weatherIcon, weatherText, storedLocation)
            }
        }
        weatherIcon.setOnClickListener(refreshClickListener)
        weatherText.setOnClickListener(refreshClickListener)
    }

    private var isSettingDialogVisible = false

    fun showWeatherSettings(weatherIcon: ImageView? = null, weatherText: TextView? = null) {
        handler.post {
            if (isSettingDialogVisible) return@post
            isSettingDialogVisible = true
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_weather_settings, null)
            val locationInput = dialogView.findViewById<EditText>(R.id.weather_location_input)
            val celsiusRadio = dialogView.findViewById<android.widget.RadioButton>(R.id.unit_celsius)
            val fahrenheitRadio = dialogView.findViewById<android.widget.RadioButton>(R.id.unit_fahrenheit)

            locationInput.setText(getStoredLocation() ?: "")

            val currentUnit = getTemperatureUnit()
            if (currentUnit == "fahrenheit") {
                fahrenheitRadio.isChecked = true
            } else {
                celsiusRadio.isChecked = true
            }

            val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            builder.setTitle(R.string.weather_settings_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val location = locationInput.text.toString().trim()
                    val selectedUnit = if (fahrenheitRadio.isChecked) "fahrenheit" else "celsius"

                    saveTemperatureUnit(selectedUnit)
                    if (location.isNotEmpty()) {
                        saveLocation(location)
                        if (weatherIcon != null && weatherText != null) {
                            fetchWeatherFromOpenMeteo(weatherIcon, weatherText, location)
                        }
                    }
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    isSettingDialogVisible = false
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    isSettingDialogVisible = false
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    isSettingDialogVisible = false
                }
                .show()
        }
    }
}
