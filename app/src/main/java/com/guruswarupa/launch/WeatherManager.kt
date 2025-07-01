
package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, latitude: Double = 0.0, longitude: Double = 0.0, forcePrompt: Boolean = false) {
        val apiKey = getApiKey()

        if (apiKey == null) {
            if (hasUserRejectedApiKey() && !forcePrompt) {
                handler.post {
                    weatherText.text = "Tap to set weather API key"
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)

                    // Set click listener to prompt for API key when user taps weather
                    val clickListener = View.OnClickListener {
                        promptForApiKey { key ->
                            if (key != null) {
                                saveApiKey(key)
                                setUserRejectedApiKey(false)
                                fetchWeatherData(weatherIcon, weatherText, latitude, longitude, key)
                            }
                        }
                    }
                    weatherIcon.setOnClickListener(clickListener)
                    weatherText.setOnClickListener(clickListener)
                }
                return
            }

            promptForApiKey { key ->
                if (key != null) {
                    saveApiKey(key)
                    setUserRejectedApiKey(false)
                    fetchWeatherData(weatherIcon, weatherText, latitude, longitude, key)
                } else {
                    setUserRejectedApiKey(true)
                    handler.post {
                        weatherText.text = "Tap to set weather API key"
                        weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)

                        // Set click listener to prompt for API key when user taps weather
                        val clickListener = View.OnClickListener {
                            promptForApiKey { key ->
                                if (key != null) {
                                    saveApiKey(key)
                                    setUserRejectedApiKey(false)
                                    fetchWeatherData(weatherIcon, weatherText, latitude, longitude, key)
                                }
                            }
                        }
                        weatherIcon.setOnClickListener(clickListener)
                        weatherText.setOnClickListener(clickListener)
                    }
                }
            }
        } else {
            fetchWeatherData(weatherIcon, weatherText, latitude, longitude, apiKey)
        }
    }

    private fun promptForApiKey(callback: (String?) -> Unit) {
        handler.post {
            val builder = AlertDialog.Builder(context)
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

    private fun fetchWeatherData(weatherIcon: ImageView, weatherText: TextView, latitude: Double, longitude: Double, apiKey: String) {
        // If no coordinates provided, use a default city (you can change this)
        val url = if (latitude != 0.0 && longitude != 0.0) {
            "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric"
        } else {
            "https://api.openweathermap.org/data/2.5/weather?q=New York&appid=$apiKey&units=metric"
        }

        executor.execute {
            try {
                val response = URL(url).readText()
                val jsonObject = JSONObject(response)

                val main = jsonObject.getJSONObject("main")
                val weather = jsonObject.getJSONArray("weather").getJSONObject(0)

                val temperature = main.getInt("temp")
                val description = weather.getString("main")
                val weatherId = weather.getInt("id")

                handler.post {
                    weatherText.text = "$temperature°C $description"
                    setWeatherIcon(weatherIcon, weatherId)
                }

            } catch (e: Exception) {
                Log.e("WeatherManager", "Error fetching weather", e)
                handler.post {
                    weatherText.text = "Weather unavailable"
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
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
}
