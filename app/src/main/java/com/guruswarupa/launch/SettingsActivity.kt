package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private val EXPORT_REQUEST_CODE = 1
    private val IMPORT_REQUEST_CODE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val weatherApiKeyInput = findViewById<EditText>(R.id.weather_api_key_input)
        val displayStyleGroup = findViewById<RadioGroup>(R.id.display_style_group)
        val gridOption = findViewById<RadioButton>(R.id.grid_option)
        val listOption = findViewById<RadioButton>(R.id.list_option)
        val saveButton = findViewById<Button>(R.id.save_settings_button)
        val setDefaultLauncherButton = findViewById<Button>(R.id.set_default_launcher_button)
        val exportButton = findViewById<Button>(R.id.export_settings_button)
        val importButton = findViewById<Button>(R.id.import_settings_button)
        // Add reset button
        val resetButton = findViewById<Button>(R.id.reset_usage_button)
        val resetFinanceButton = findViewById<Button>(R.id.reset_finance_button)
        val appLockButton = findViewById<Button>(R.id.app_lock_button)

        // Load current settings
        loadCurrentSettings(weatherApiKeyInput, displayStyleGroup, gridOption, listOption)

        saveButton.setOnClickListener {
            saveSettings(weatherApiKeyInput, displayStyleGroup)
        }

        setDefaultLauncherButton.setOnClickListener {
            openDefaultLauncherSettings()
        }

        exportButton.setOnClickListener {
            exportSettings()
        }

        importButton.setOnClickListener {
            importSettings()
        }

        resetButton.setOnClickListener {
            resetAppUsageCount()
        }

        resetFinanceButton.setOnClickListener {
            resetFinanceData()
        }
        appLockButton.setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }
    }

    private fun loadCurrentSettings(
        weatherApiKeyInput: EditText,
        displayStyleGroup: RadioGroup,
        gridOption: RadioButton,
        listOption: RadioButton
    ) {
        // Load weather API key
        val currentApiKey = prefs.getString("weather_api_key", "")
        weatherApiKeyInput.setText(currentApiKey)

        // Load display style
        val currentStyle = prefs.getString("view_preference", "list")
        if (currentStyle == "grid") {
            gridOption.isChecked = true
        } else {
            listOption.isChecked = true
        }
    }

    private fun saveSettings(weatherApiKeyInput: EditText, displayStyleGroup: RadioGroup) {
        val editor = prefs.edit()

        // Save weather API key
        val apiKey = weatherApiKeyInput.text.toString().trim()
        editor.putString("weather_api_key", apiKey)

        // Save display style
        val selectedStyleId = displayStyleGroup.checkedRadioButtonId
        val selectedStyle = when (selectedStyleId) {
            R.id.grid_option -> "grid"
            R.id.list_option -> "list"
            else -> "list"
        }
        editor.putString("view_preference", selectedStyle)

        editor.apply()

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()

        // Send broadcast to refresh main activity if needed
        val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
        sendBroadcast(intent)

        finish()
    }

    private fun openDefaultLauncherSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open launcher settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportSettings() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "launch_settings_backup.json")
        }
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    private fun importSettings() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                EXPORT_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        exportSettingsToFile(uri)
                    }
                }
                IMPORT_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        importSettingsFromFile(uri)
                    }
                }
            }
        }
    }

    private fun exportSettingsToFile(uri: Uri) {
        try {
            val allPrefs = prefs.all
            val settingsJson = JSONObject()

            for ((key, value) in allPrefs) {
                // Include all keys including todo_items and transaction data
                when (value) {
                    is String -> {
                        settingsJson.put(key, value)
                        // Special handling for todo_items to ensure proper format
                        if (key == "todo_items" && value.isNotEmpty()) {
                            // Validate todo items format before export
                            val todoArray = value.split("|")
                            var isValidFormat = true
                            for (todoString in todoArray) {
                                if (todoString.isNotEmpty()) {
                                    val parts = todoString.split(":")
                                    if (parts.size < 7) {
                                        isValidFormat = false
                                        break
                                    }
                                }
                            }
                            if (!isValidFormat) {
                                Toast.makeText(this, "Warning: Todo items may not export correctly due to format issues", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is Boolean -> settingsJson.put(key, value)
                    is Int -> settingsJson.put(key, value)
                    is Long -> settingsJson.put(key, value)
                    is Float -> settingsJson.put(key, value)
                    is Set<*> -> {
                        val jsonArray = JSONArray()
                        value.forEach { jsonArray.put(it) }
                        settingsJson.put(key, jsonArray)
                    }
                }
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(settingsJson.toString(2).toByteArray())
            }

            Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettingsFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val settingsJson = JSONObject(jsonString)
                val editor = prefs.edit()

                val keys = settingsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = settingsJson.get(key)

                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is Float -> editor.putFloat(key, value)
                        is JSONArray -> {
                            val stringSet = mutableSetOf<String>()
                            for (i in 0 until value.length()) {
                                stringSet.add(value.getString(i))
                            }
                            editor.putStringSet(key, stringSet)
                        }
                    }
                }

                editor.apply()

                // Reload current settings in UI
                val weatherApiKeyInput = findViewById<EditText>(R.id.weather_api_key_input)
                val displayStyleGroup = findViewById<RadioGroup>(R.id.display_style_group)
                val gridOption = findViewById<RadioButton>(R.id.grid_option)
                val listOption = findViewById<RadioButton>(R.id.list_option)
                loadCurrentSettings(weatherApiKeyInput, displayStyleGroup, gridOption, listOption)

                Toast.makeText(this, "Settings imported successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to import settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetAppUsageCount() {
        // Show confirmation dialog
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Reset App Usage Count")
            .setMessage("This will reset all app usage statistics and reorder apps alphabetically. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                // Clear app usage preferences
                val appUsagePrefs = getSharedPreferences("app_usage", MODE_PRIVATE)
                appUsagePrefs.edit().clear().apply()

                // Clear usage count from main preferences as well
                val mainPrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
                val editor = mainPrefs.edit()
                val allPrefs = mainPrefs.all
                for (key in allPrefs.keys) {
                    if (key.startsWith("usage_")) {
                        editor.remove(key)
                    }
                }
                editor.apply()

                Toast.makeText(this, "App usage count reset successfully", Toast.LENGTH_SHORT).show()

                // Send broadcast to refresh MainActivity
                val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                sendBroadcast(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetFinanceData() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Reset Finance Data")
            .setMessage("Are you sure you want to reset all finance data? This will clear your balance, transaction history, and monthly records. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                // Reset all finance-related data
                val editor = prefs.edit()
                val allPrefs = prefs.all
                for (key in allPrefs.keys) {
                    if (key.startsWith("finance_") || key.startsWith("transaction_")) {
                        editor.remove(key)
                    }
                }
                editor.apply()

                Toast.makeText(this, "Finance data reset successfully", Toast.LENGTH_SHORT).show()

                // Send broadcast to refresh MainActivity
                val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                sendBroadcast(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}