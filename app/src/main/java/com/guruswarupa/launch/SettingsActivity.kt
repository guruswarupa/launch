
package com.guruswarupa.launch

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val weatherApiKeyInput = findViewById<EditText>(R.id.weather_api_key_input)
        val displayStyleGroup = findViewById<RadioGroup>(R.id.display_style_group)
        val gridOption = findViewById<RadioButton>(R.id.grid_option)
        val listOption = findViewById<RadioButton>(R.id.list_option)
        val saveButton = findViewById<Button>(R.id.save_settings_button)
        val setDefaultLauncherButton = findViewById<Button>(R.id.set_default_launcher_button)

        // Load current settings
        loadCurrentSettings(weatherApiKeyInput, displayStyleGroup, gridOption, listOption)

        saveButton.setOnClickListener {
            saveSettings(weatherApiKeyInput, displayStyleGroup)
        }

        setDefaultLauncherButton.setOnClickListener {
            openDefaultLauncherSettings()
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
}
