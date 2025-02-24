package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<Button>(R.id.permission_button).setOnClickListener { requestPermissions() }
        findViewById<Button>(R.id.continue_button).setOnClickListener { continueSetup() }
        findViewById<Button>(R.id.set_default_launcher_button).setOnClickListener { setDefaultLauncher() }
        findViewById<Button>(R.id.set_display_style).setOnClickListener { chooseDisplayStyle() }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_MEDIA_IMAGES)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing, 100) else Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
        requestStoragePermission()
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (e: Exception) { Toast.makeText(this, "Enable file access in settings.", Toast.LENGTH_LONG).show() }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 || requestCode == 101) {
            val msg = if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) "Permissions granted!" else "Permissions denied."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun continueSetup() {
        prefs.edit().putBoolean("isFirstTime", false).apply()
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    private fun setDefaultLauncher() = startActivity(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun chooseDisplayStyle() {
        AlertDialog.Builder(this)
            .setTitle("Choose App Display Style")
            .setMessage("Grid or List?")
            .setPositiveButton("Grid") { _, _ -> setViewPreference("grid") }
            .setNegativeButton("List") { _, _ -> setViewPreference("list") }
            .show()
    }

    private fun setViewPreference(style: String) {
        prefs.edit().putString("view_preference", style).apply()
        (this as? MainActivity)?.loadApps()
    }
}