package com.guruswarupa.launch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.app.AlertDialog

fun isFirstTime(context: Context, sharedPreferencesName: String, firstTimeKey: String): Boolean {
    val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
    val isFirstTime = sharedPreferences.getBoolean(firstTimeKey, true)
    if (isFirstTime) {
        sharedPreferences.edit().putBoolean(firstTimeKey, false).apply()
    }
    return isFirstTime
}

fun checkAndAskSetAsDefault(context: Context, packageName: String) {
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val resolveInfo = context.packageManager.queryIntentActivities(homeIntent, 0)

    if (resolveInfo.isNotEmpty()) {
        val currentHomePackage = resolveInfo[0].activityInfo.packageName
        if (currentHomePackage != packageName) {
            showSetAsDefaultDialog(context)
        }
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


fun showSetAsDefaultDialog(context: Context) {
    val dialogBuilder = AlertDialog.Builder(context)
    dialogBuilder.setMessage("Do you want to set this app as the default home launcher?")
        .setCancelable(false)
        .setPositiveButton("Yes") { dialog, _ ->
            // Open the default apps settings page
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            context.startActivity(intent)
            dialog.dismiss()
            askForViewPreference(context)
        }
        .setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            askForViewPreference(context)
        }
    val alert = dialogBuilder.create()
    alert.show()
}
