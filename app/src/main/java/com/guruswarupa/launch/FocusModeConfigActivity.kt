
package com.guruswarupa.launch

import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FocusModeConfigActivity : ComponentActivity() {

    private lateinit var focusModeManager: FocusModeManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FocusModeAppAdapter
    private lateinit var appList: MutableList<ResolveInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focus_mode_config)

        focusModeManager = FocusModeManager(getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE))

        recyclerView = findViewById(R.id.focus_mode_app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadApps()

        adapter = FocusModeAppAdapter(appList, focusModeManager)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.save_focus_config).setOnClickListener {
            Toast.makeText(this, "Focus mode configuration saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.cancel_focus_config).setOnClickListener {
            finish()
        }
    }

    private fun loadApps() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        appList = apps.toMutableList()
    }
}
