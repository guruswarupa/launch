package com.guruswarupa.launch.utils

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import androidx.core.content.edit
import com.guruswarupa.launch.R

class WeatherManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private fun getApiKey(): String? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        return prefs.getString("weather_api_key", null)
    }

    private fun saveApiKey(apiKey: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit { putString("weather_api_key", apiKey) }
    }

    private fun hasUserRejectedApiKey(): Boolean {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        return prefs.getBoolean("weather_api_key_rejected", false)
    }

    private fun setUserRejectedApiKey(rejected: Boolean) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("weather_api_key_rejected", rejected) }
    }

    private fun getStoredCityName(): String? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        return prefs.getString("weather_stored_city_name", null)
    }

    private fun saveCityName(cityName: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit {
            putString("weather_stored_city_name", cityName)
        }
    }

    private fun getCachedWeather(): CachedWeather? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("weather_cached_timestamp", 0)
        val temperature = prefs.getInt("weather_cached_temperature", Int.MIN_VALUE)
        val description = prefs.getString("weather_cached_description", null)
        val weatherId = prefs.getInt("weather_cached_weather_id", -1)
        
        if (timestamp == 0L || temperature == Int.MIN_VALUE || description == null || weatherId == -1) {
            return null
        }
        
        return CachedWeather(temperature, description, weatherId, timestamp)
    }

    private fun saveCachedWeather(temperature: Int, description: String, weatherId: Int) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit {
            putInt("weather_cached_temperature", temperature)
                .putString("weather_cached_description", description)
                .putInt("weather_cached_weather_id", weatherId)
                .putLong("weather_cached_timestamp", currentTime)
        }
    }

    private fun isCacheValid(cachedWeather: CachedWeather, maxAgeMinutes: Long = 45): Boolean {
        val currentTime = System.currentTimeMillis()
        val ageMinutes = (currentTime - cachedWeather.timestamp) / (1000 * 60)
        return ageMinutes < maxAgeMinutes
    }

    private data class CachedWeather(
        val temperature: Int,
        val description: String,
        val weatherId: Int,
        val timestamp: Long
    )

    private var isPrompting = false

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, forcePrompt: Boolean = false) {
        val apiKey = getApiKey()

        if (apiKey == null) {
            if (hasUserRejectedApiKey() && !forcePrompt) {
                handler.post {
                    weatherText.text = context.getString(R.string.tap_to_set_api_key)
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)

                    val clickListener = View.OnClickListener {
                        showWeatherSettings(weatherIcon, weatherText)
                    }
                    weatherIcon.setOnClickListener(clickListener)
                    weatherText.setOnClickListener(clickListener)
                }
                return
            }

            if (!isPrompting) {
                showWeatherSettings(weatherIcon, weatherText)
            }
        } else {
            val cachedWeather = getCachedWeather()
            if (cachedWeather != null && isCacheValid(cachedWeather)) {
                handler.post {
                    weatherText.text = context.getString(R.string.weather_temp_format, cachedWeather.temperature, cachedWeather.description)
                    setWeatherIcon(weatherIcon, cachedWeather.weatherId)
                    setupRefreshListeners(weatherIcon, weatherText)
                }
                return
            }
            
            val storedCityName = getStoredCityName()
            if (storedCityName != null && storedCityName.isNotEmpty()) {
                fetchWeatherData(weatherIcon, weatherText, storedCityName, apiKey)
            } else {
                handler.post {
                    weatherText.text = context.getString(R.string.tap_to_enter_location)
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                    setupRefreshListeners(weatherIcon, weatherText)
                }
            }
        }
    }

    private fun fetchWeatherData(weatherIcon: ImageView, weatherText: TextView, cityName: String, apiKey: String) {
        val encodedCityName = try {
            java.net.URLEncoder.encode(cityName, "UTF-8")
        } catch (_: Exception) {
            cityName
        }
        val url = "https://api.openweathermap.org/data/2.5/weather?q=$encodedCityName&appid=$apiKey&units=metric"

        executor.execute {
            try {
                val response = URL(url).readText()
                val jsonObject = JSONObject(response)

                val main = jsonObject.getJSONObject("main")
                val weather = jsonObject.getJSONArray("weather").getJSONObject(0)
                val temperature = main.getInt("temp")
                val description = weather.getString("main")
                val weatherId = weather.getInt("id")
                val actualCityName = jsonObject.getString("name")

                saveCachedWeather(temperature, description, weatherId)
                saveCityName(actualCityName)
                
                handler.post {
                    weatherText.text = context.getString(R.string.weather_temp_format, temperature, description)
                    setWeatherIcon(weatherIcon, weatherId)
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

    private fun setWeatherIcon(weatherIcon: ImageView, weatherId: Int) {
        val iconResource = when (weatherId) {
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
            val apiKey = getApiKey()
            val storedCityName = getStoredCityName()
            if (apiKey == null || storedCityName == null) {
                showWeatherSettings(weatherIcon, weatherText)
            } else {
                fetchWeatherData(weatherIcon, weatherText, storedCityName, apiKey)
            }
        }
        weatherIcon.setOnClickListener(refreshClickListener)
        weatherText.setOnClickListener(refreshClickListener)
    }
    
    fun showWeatherSettings(
        weatherIcon: ImageView? = null,
        weatherText: TextView? = null,
        onApiKeyUpdated: (() -> Unit)? = null
    ) {
        handler.post {
            isPrompting = true
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_weather_settings, null)
            val apiKeyInput = dialogView.findViewById<EditText>(R.id.weather_api_key_input)
            val locationInput = dialogView.findViewById<EditText>(R.id.weather_location_input)
            
            apiKeyInput.setText(getApiKey() ?: "")
            locationInput.setText(getStoredCityName() ?: "")
            
            val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            builder.setTitle("Weather Settings")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val apiKey = apiKeyInput.text.toString().trim()
                    val location = locationInput.text.toString().trim()
                    
                    if (apiKey.isNotEmpty()) {
                        saveApiKey(apiKey)
                        setUserRejectedApiKey(false)
                        if (location.isNotEmpty()) {
                            saveCityName(location)
                            if (weatherIcon != null && weatherText != null) {
                                fetchWeatherData(weatherIcon, weatherText, location, apiKey)
                            }
                        }
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                        onApiKeyUpdated?.invoke()
                    } else {
                        saveApiKey("") // Effectively remove it
                        setUserRejectedApiKey(true)
                        Toast.makeText(context, "API key removed", Toast.LENGTH_SHORT).show()
                    }
                    isPrompting = false
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    isPrompting = false
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    isPrompting = false
                }
                .show()
        }
    }
}
