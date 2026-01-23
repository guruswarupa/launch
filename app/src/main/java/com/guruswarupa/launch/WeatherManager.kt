package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
class WeatherManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private fun getApiKey(): String? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        return prefs.getString("weather_api_key", null)
    }

    private fun saveApiKey(apiKey: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit().putString("weather_api_key", apiKey).apply()
    }

    private fun hasUserRejectedApiKey(): Boolean {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        return prefs.getBoolean("weather_api_key_rejected", false)
    }

    private fun setUserRejectedApiKey(rejected: Boolean) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("weather_api_key_rejected", rejected).apply()
    }

    private fun getStoredCityName(): String? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        return prefs.getString("weather_stored_city_name", null)
    }

    private fun saveCityName(cityName: String) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("weather_stored_city_name", cityName)
            .apply()
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
        prefs.edit()
            .putInt("weather_cached_temperature", temperature)
            .putString("weather_cached_description", description)
            .putInt("weather_cached_weather_id", weatherId)
            .putLong("weather_cached_timestamp", currentTime)
            .apply()
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

    private var isPromptingForApiKey = false
    private var isPromptingForCityName = false

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, forcePrompt: Boolean = false) {
        val apiKey = getApiKey()

        if (apiKey == null) {
            if (hasUserRejectedApiKey() && !forcePrompt) {
                handler.post {
                    weatherText.text = "Tap to set weather API key"
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)

                    val clickListener = View.OnClickListener {
                        if (!isPromptingForApiKey) {
                            isPromptingForApiKey = true
                            promptForApiKey { key ->
                                isPromptingForApiKey = false
                                if (key != null) {
                                    saveApiKey(key)
                                    setUserRejectedApiKey(false)
                                    // After API key is set, prompt for city name
                                    promptForCityNameAndFetchWeather(weatherIcon, weatherText, key)
                                }
                            }
                        }
                    }
                    weatherIcon.setOnClickListener(clickListener)
                    weatherText.setOnClickListener(clickListener)
                }
                return
            }

            // Only prompt if not already prompting
            if (!isPromptingForApiKey) {
                isPromptingForApiKey = true
                promptForApiKey { key ->
                    isPromptingForApiKey = false
                    if (key != null) {
                        saveApiKey(key)
                        setUserRejectedApiKey(false)
                        // After API key is set, prompt for city name
                        promptForCityNameAndFetchWeather(weatherIcon, weatherText, key)
                    } else {
                        setUserRejectedApiKey(true)
                        handler.post {
                            weatherText.text = "Tap to set weather API key"
                            weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)

                            val clickListener = View.OnClickListener {
                                if (!isPromptingForApiKey) {
                                    isPromptingForApiKey = true
                                    promptForApiKey { key ->
                                        isPromptingForApiKey = false
                                        if (key != null) {
                                            saveApiKey(key)
                                            setUserRejectedApiKey(false)
                                            // After API key is set, prompt for city name
                                            promptForCityNameAndFetchWeather(weatherIcon, weatherText, key)
                                        }
                                    }
                                }
                            }
                            weatherIcon.setOnClickListener(clickListener)
                            weatherText.setOnClickListener(clickListener)
                        }
                    }
                }
            }
        } else {
            // API key exists, check for cached weather first
            val cachedWeather = getCachedWeather()
            if (cachedWeather != null) {
                if (isCacheValid(cachedWeather)) {
                    // Show cached weather immediately (cache is still fresh)
                    handler.post {
                        weatherText.text = "${cachedWeather.temperature}°C ${cachedWeather.description}"
                        setWeatherIcon(weatherIcon, cachedWeather.weatherId)
                        setupRefreshListeners(weatherIcon, weatherText)
                    }
                    return
                } else {
                    // Cache expired, show cached data but indicate it needs refresh
                    handler.post {
                        weatherText.text = "${cachedWeather.temperature}°C ${cachedWeather.description} (tap to refresh)"
                        setWeatherIcon(weatherIcon, cachedWeather.weatherId)
                        setupRefreshListeners(weatherIcon, weatherText)
                    }
                    return
                }
            }
            
            // No cache at all, check for city name
            val storedCityName = getStoredCityName()
            if (storedCityName != null && storedCityName.isNotEmpty()) {
                // Show placeholder and allow user to tap to fetch
                handler.post {
                    weatherText.text = "Tap to load weather"
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                    setupRefreshListeners(weatherIcon, weatherText)
                }
            } else {
                // No city name stored, prompt for it
                promptForCityNameAndFetchWeather(weatherIcon, weatherText, apiKey)
            }
        }
    }

    private fun promptForCityNameAndFetchWeather(weatherIcon: ImageView, weatherText: TextView, apiKey: String) {
        handler.post {
            if (isPromptingForCityName) return@post
            isPromptingForCityName = true
            
            val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            val input = EditText(context)
            input.hint = "Enter city name (e.g., London, New York)"
            
            // Pre-fill with stored city name if available
            val storedCityName = getStoredCityName()
            if (storedCityName != null && storedCityName.isNotEmpty()) {
                input.setText(storedCityName)
            }

            builder.setTitle("Enter Location")
                .setMessage("Enter the name of the city for weather information")
                .setView(input)
                .setPositiveButton("Get Weather") { _, _ ->
                    val cityName = input.text.toString().trim()
                    if (cityName.isNotEmpty()) {
                        saveCityName(cityName)
                        isPromptingForCityName = false
                        fetchWeatherData(weatherIcon, weatherText, cityName, apiKey)
                    } else {
                        isPromptingForCityName = false
                        handler.post {
                            weatherText.text = "Tap to enter location"
                            weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                            setupRefreshListeners(weatherIcon, weatherText)
                        }
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> 
                    isPromptingForCityName = false
                    handler.post {
                        weatherText.text = "Tap to enter location"
                        weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                        setupRefreshListeners(weatherIcon, weatherText)
                    }
                }
                .setOnDismissListener {
                    isPromptingForCityName = false
                }
                .show()
        }
    }

    private fun promptForApiKey(callback: (String?) -> Unit) {
        handler.post {
            val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            val input = EditText(context)
            input.hint = "Enter your OpenWeatherMap API key"

            builder.setTitle("Weather API Key Required")
                .setMessage("To display weather information, please enter your OpenWeatherMap API key.\n\nGet one free at: openweathermap.org/api")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val apiKey = input.text.toString().trim()
                    if (apiKey.isNotEmpty()) {
                        callback(apiKey)
                    } else {
                        callback(null)
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> callback(null) }
                .show()
        }
    }

    private fun fetchWeatherData(weatherIcon: ImageView, weatherText: TextView, cityName: String, apiKey: String) {
        // URL encode the city name to handle spaces and special characters
        val encodedCityName = java.net.URLEncoder.encode(cityName, "UTF-8")
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
                val cityName = jsonObject.getString("name")

                // Save to cache
                saveCachedWeather(temperature, description, weatherId)
                
                handler.post {
                    weatherText.text = "$temperature°C $description"
                    setWeatherIcon(weatherIcon, weatherId)
                    // Set up refresh click listeners after successfully displaying weather
                    setupRefreshListeners(weatherIcon, weatherText)
                }

            } catch (e: Exception) {
                Log.e("WeatherManager", "Error fetching weather", e)
                handler.post {
                    weatherText.text = "Weather unavailable"
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                    // Set up click listeners even when weather is unavailable so user can tap to enter location
                    setupRefreshListeners(weatherIcon, weatherText)
                }
            }
        }
    }

    private fun setWeatherIcon(weatherIcon: ImageView, weatherId: Int) {
        val iconResource = when (weatherId) {
            in 200..232 -> R.drawable.ic_weather_rainy // Thunderstorm
            in 300..321 -> R.drawable.ic_weather_rainy // Drizzle
            in 500..531 -> R.drawable.ic_weather_rainy // Rain
            in 600..622 -> R.drawable.ic_weather_snowy // Snow
            in 701..781 -> R.drawable.ic_weather_cloudy // Atmosphere (mist, smoke, etc.)
            800 -> R.drawable.ic_weather_sunny // Clear sky
            in 801..804 -> R.drawable.ic_weather_cloudy // Clouds
            else -> R.drawable.ic_weather_cloudy // Default
        }
        weatherIcon.setImageResource(iconResource)
    }

    private fun setupRefreshListeners(weatherIcon: ImageView, weatherText: TextView) {
        val refreshClickListener = View.OnClickListener {
            val apiKey = getApiKey()
            if (apiKey == null) {
                // No API key, prompt for it
                updateWeather(weatherIcon, weatherText)
            } else {
                val storedCityName = getStoredCityName()
                if (storedCityName != null && storedCityName.isNotEmpty()) {
                    // Clear expired cache and fetch fresh weather
                    val cachedWeather = getCachedWeather()
                    if (cachedWeather != null && !isCacheValid(cachedWeather)) {
                        // Clear expired cache
                        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
                        prefs.edit()
                            .remove("weather_cached_temperature")
                            .remove("weather_cached_description")
                            .remove("weather_cached_weather_id")
                            .remove("weather_cached_timestamp")
                            .apply()
                    }
                    // Fetch fresh weather with stored city name
                    fetchWeatherData(weatherIcon, weatherText, storedCityName, apiKey)
                } else {
                    // No city name, prompt for it
                    promptForCityNameAndFetchWeather(weatherIcon, weatherText, apiKey)
                }
            }
        }
        weatherIcon.setOnClickListener(refreshClickListener)
        weatherText.setOnClickListener(refreshClickListener)
    }
}