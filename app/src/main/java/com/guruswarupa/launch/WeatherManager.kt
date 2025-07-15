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
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class WeatherManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

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

    private var isPromptingForApiKey = false

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, latitude: Double = 0.0, longitude: Double = 0.0, forcePrompt: Boolean = false) {
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
                                    // Use the provided coordinates if available, otherwise get user location
                                    if (latitude != 0.0 && longitude != 0.0) {
                                        fetchWeatherData(weatherIcon, weatherText, latitude, longitude, key)
                                    } else {
                                        getUserLocationAndFetchWeather(weatherIcon, weatherText, key)
                                    }
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
                        // Use the provided coordinates if available, otherwise get user location
                        if (latitude != 0.0 && longitude != 0.0) {
                            fetchWeatherData(weatherIcon, weatherText, latitude, longitude, key)
                        } else {
                            getUserLocationAndFetchWeather(weatherIcon, weatherText, key)
                        }
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
                                            // Use the provided coordinates if available, otherwise get user location
                                            if (latitude != 0.0 && longitude != 0.0) {
                                                fetchWeatherData(weatherIcon, weatherText, latitude, longitude, key)
                                            } else {
                                                getUserLocationAndFetchWeather(weatherIcon, weatherText, key)
                                            }
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
            // Use the provided coordinates if available, otherwise get user location
            if (latitude != 0.0 && longitude != 0.0) {
                fetchWeatherData(weatherIcon, weatherText, latitude, longitude, apiKey)
            } else {
                getUserLocationAndFetchWeather(weatherIcon, weatherText, apiKey)
            }
        }
    }

    private fun getUserLocationAndFetchWeather(weatherIcon: ImageView, weatherText: TextView, apiKey: String) {
        // Check if we have location permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            try {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        fetchWeatherData(weatherIcon, weatherText, location.latitude, location.longitude, apiKey)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                // Try to get last known location first
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (lastKnownLocation != null) {
                    fetchWeatherData(weatherIcon, weatherText, lastKnownLocation.latitude, lastKnownLocation.longitude, apiKey)
                } else {
                    // Request fresh location
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null)

                    // Fallback timeout - if no location after 5 seconds, use default
                    handler.postDelayed({
                        locationManager.removeUpdates(locationListener)
                        fetchWeatherData(weatherIcon, weatherText, 0.0, 0.0, apiKey)
                    }, 5000)
                }
            } catch (e: SecurityException) {
                // Fallback to default location if security exception
                fetchWeatherData(weatherIcon, weatherText, 0.0, 0.0, apiKey)
            }
        } else {
            // No location permissions, use default location
            fetchWeatherData(weatherIcon, weatherText, 0.0, 0.0, apiKey)
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

    private fun fetchWeatherData(weatherIcon: ImageView, weatherText: TextView, latitude: Double, longitude: Double, apiKey: String) {
        val url = if (latitude != 0.0 && longitude != 0.0) {
            "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric"
        } else {
            // If no location available, try to get location by IP
            "https://api.openweathermap.org/data/2.5/weather?q=&appid=$apiKey&units=metric"
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
                val cityName = jsonObject.getString("name")

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