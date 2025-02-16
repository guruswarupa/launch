package com.guruswarupa.launch

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast

class OnboardingActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "com.guruswarupa.launch.PREFS"
    private val FIRSTTIMEKEY = "isFirstTime"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val permissionButton: Button = findViewById(R.id.permission_button)
        val continueButton: Button = findViewById(R.id.continue_button)
        val setDefaultLauncherButton: Button = findViewById(R.id.set_default_launcher_button)
        val setDisplayStyle: Button = findViewById(R.id.set_display_style)
        permissionButton.setOnClickListener {
            requestAllPermissions()
            requestReadExternalStoragePermission()
        }

        continueButton.setOnClickListener {
            sharedPreferences.edit().putBoolean(FIRSTTIMEKEY, false).apply()
            restartApp()
        }

        setDefaultLauncherButton.setOnClickListener {
            setAsDefaultLauncher()
        }

        setDisplayStyle.setOnClickListener {
            askForViewPreference(this)
        }
    }

    private fun requestAllPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, 100)
        } else {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    fun askForViewPreference(context: Context) {
        val sharedPreferences = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)

        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setTitle("Choose App Display Style")
            .setMessage("Do you want to display apps in a Grid or List?")
            .setPositiveButton("Grid") { _, _ ->
                sharedPreferences.edit().putString("view_preference", "grid").apply()
                if (context is MainActivity) {
                    context.loadApps() // Reload apps in grid mode
                }
            }
            .setNegativeButton("List") { _, _ ->
                sharedPreferences.edit().putString("view_preference", "list").apply()
                if (context is MainActivity) {
                    context.loadApps() // Reload apps in list mode
                }
            }
        val alert = dialogBuilder.create()
        alert.show()
    }

    private fun requestReadExternalStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Please enable file access manually in settings.", Toast.LENGTH_LONG).show()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 || requestCode == 101) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun setAsDefaultLauncher() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }
}
