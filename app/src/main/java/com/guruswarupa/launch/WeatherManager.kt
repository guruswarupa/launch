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
    var onLocationPermissionNeeded: (() -> Unit)? = null // Callback for requesting location permission

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

    private fun getStoredLocation(): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("weather_stored_latitude", 0f).toDouble()
        val lon = prefs.getFloat("weather_stored_longitude", 0f).toDouble()
        return if (lat != 0.0 && lon != 0.0) Pair(lat, lon) else null
    }

    private fun saveLocation(latitude: Double, longitude: Double) {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("weather_stored_latitude", latitude.toFloat())
            .putFloat("weather_stored_longitude", longitude.toFloat())
            .apply()
    }

    private fun clearStoredLocation() {
        val prefs = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("weather_stored_latitude")
            .remove("weather_stored_longitude")
            .apply()
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private var isPromptingForApiKey = false

    fun updateWeather(weatherIcon: ImageView, weatherText: TextView, latitude: Double = 0.0, longitude: Double = 0.0, forcePrompt: Boolean = false, forceRefreshLocation: Boolean = false) {
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
                                        getUserLocationAndFetchWeather(weatherIcon, weatherText, key, forceRefreshLocation)
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
                            getUserLocationAndFetchWeather(weatherIcon, weatherText, key, forceRefreshLocation)
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
                                                getUserLocationAndFetchWeather(weatherIcon, weatherText, key, forceRefreshLocation)
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
                getUserLocationAndFetchWeather(weatherIcon, weatherText, apiKey, forceRefreshLocation)
            }
        }
    }

    private fun getUserLocationAndFetchWeather(weatherIcon: ImageView, weatherText: TextView, apiKey: String, forceRefresh: Boolean = false) {
        // First, check if we have a stored location
        val storedLocation = getStoredLocation()
        
        // If we have a stored location and not forcing refresh, use it regardless of permissions
        if (storedLocation != null && !forceRefresh) {
            fetchWeatherData(weatherIcon, weatherText, storedLocation.first, storedLocation.second, apiKey)
            return
        }

        // If we don't have a stored location or forcing refresh, check permissions
        if (hasLocationPermission()) {
            // Need to get new location (either no stored location or forcing refresh)
            try {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        // Save the location for future use
                        saveLocation(location.latitude, location.longitude)
                        fetchWeatherData(weatherIcon, weatherText, location.latitude, location.longitude, apiKey)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                // Try to get last known location first
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (lastKnownLocation != null) {
                    // Save the location for future use
                    saveLocation(lastKnownLocation.latitude, lastKnownLocation.longitude)
                    fetchWeatherData(weatherIcon, weatherText, lastKnownLocation.latitude, lastKnownLocation.longitude, apiKey)
                } else {
                    // Request fresh location
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null)

                    // Fallback timeout - if no location after 5 seconds, use stored location if available, otherwise default
                    handler.postDelayed({
                        locationManager.removeUpdates(locationListener)
                        val fallbackLocation = getStoredLocation()
                        if (fallbackLocation != null) {
                            fetchWeatherData(weatherIcon, weatherText, fallbackLocation.first, fallbackLocation.second, apiKey)
                        } else {
                            fetchWeatherData(weatherIcon, weatherText, 0.0, 0.0, apiKey)
                        }
                    }, 5000)
                }
            } catch (e: SecurityException) {
                // Fallback to stored location if available, otherwise default
                val fallbackLocation = getStoredLocation()
                if (fallbackLocation != null) {
                    fetchWeatherData(weatherIcon, weatherText, fallbackLocation.first, fallbackLocation.second, apiKey)
                } else {
                    fetchWeatherData(weatherIcon, weatherText, 0.0, 0.0, apiKey)
                }
            }
        } else {
            // No location permissions and no stored location - show unavailable
            // Don't clear stored location, keep it for future use
            if (storedLocation != null) {
                // Use stored location even without permissions
                fetchWeatherData(weatherIcon, weatherText, storedLocation.first, storedLocation.second, apiKey)
            } else {
                // No stored location and no permissions - show unavailable
                fetchWeatherData(weatherIcon, weatherText, 0.0, 0.0, apiKey)
            }
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
                    // Set up refresh click listeners after successfully displaying weather
                    setupRefreshListeners(weatherIcon, weatherText)
                }

            } catch (e: Exception) {
                Log.e("WeatherManager", "Error fetching weather", e)
                handler.post {
                    weatherText.text = "Weather unavailable"
                    weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                    // Set up click listeners even when weather is unavailable so user can tap to get location
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
            val storedLocation = getStoredLocation()
            
            // If no stored location and no permissions, request permission
            if (storedLocation == null && !hasLocationPermission()) {
                // Request location permission through callback
                onLocationPermissionNeeded?.invoke()
            } else {
                // Refresh weather data (force refresh location if permissions are granted and we want new location)
                val forceRefreshLocation = hasLocationPermission() && storedLocation == null
                updateWeather(weatherIcon, weatherText, forceRefreshLocation = forceRefreshLocation)
            }
        }
        weatherIcon.setOnClickListener(refreshClickListener)
        weatherText.setOnClickListener(refreshClickListener)
    }
}