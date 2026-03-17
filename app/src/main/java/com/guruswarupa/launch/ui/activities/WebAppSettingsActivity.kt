package com.guruswarupa.launch.ui.activities

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.managers.WebAppIconFetcher
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.WebAppEntry
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class WebAppSettingsActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    private val webAppManager by lazy { WebAppManager(prefs) }

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_web_app_settings)

        val root = findViewById<View>(R.id.web_apps_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemInsets.top, 0, systemInsets.bottom)
            insets
        }

        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.web_apps_wallpaper))

        listContainer = findViewById(R.id.web_apps_list)
        emptyView = findViewById(R.id.web_apps_empty)

        findViewById<View>(R.id.web_apps_back_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.add_web_app_button).setOnClickListener { showEditorDialog() }

        renderWebApps()
    }

    private fun renderWebApps() {
        val webApps = webAppManager.getWebApps()
        listContainer.removeAllViews()
        emptyView.visibility = if (webApps.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        webApps.forEach { entry ->
            val itemView = inflater.inflate(R.layout.item_web_app, listContainer, false)
            val iconView = itemView.findViewById<android.widget.ImageView>(R.id.web_app_item_icon)
            itemView.findViewById<TextView>(R.id.web_app_item_name).text = entry.name
            itemView.findViewById<TextView>(R.id.web_app_item_url).text = entry.url
            WebAppIconFetcher.loadIcon(this, entry.url) { drawable ->
                if (drawable != null) {
                    iconView.setImageDrawable(drawable)
                }
            }
            itemView.findViewById<ImageButton>(R.id.web_app_item_open).setOnClickListener {
                startActivity(
                    Intent(this, WebAppActivity::class.java).apply {
                        putExtra(WebAppActivity.EXTRA_WEB_APP_NAME, entry.name)
                        putExtra(WebAppActivity.EXTRA_WEB_APP_URL, entry.url)
                    }
                )
            }
            itemView.findViewById<ImageButton>(R.id.web_app_item_edit).setOnClickListener {
                showEditorDialog(entry)
            }
            itemView.findViewById<ImageButton>(R.id.web_app_item_delete).setOnClickListener {
                confirmDelete(entry)
            }
            listContainer.addView(itemView)
        }
    }

    private fun showEditorDialog(existing: WebAppEntry? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_web_app_editor, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.web_app_name_input)
        val urlInput = dialogView.findViewById<EditText>(R.id.web_app_url_input)

        nameInput.setText(existing?.name.orEmpty())
        urlInput.setText(existing?.url.orEmpty())
        urlInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(if (existing == null) R.string.add_web_app else R.string.edit_web_app)
            .setView(dialogView)
            .setPositiveButton(if (existing == null) R.string.add_button else R.string.save_button, null)
            .setNegativeButton(R.string.cancel_button, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                when {
                    name.isBlank() -> Toast.makeText(this, R.string.web_app_name_required, Toast.LENGTH_SHORT).show()
                    url.isBlank() -> Toast.makeText(this, R.string.web_app_url_required, Toast.LENGTH_SHORT).show()
                    !isSupportedWebUrl(url) -> Toast.makeText(this, R.string.web_app_https_required, Toast.LENGTH_SHORT).show()
                    else -> {
                        if (existing == null) {
                            webAppManager.addWebApp(name, url)
                        } else {
                            webAppManager.updateWebApp(existing.id, name, url)
                        }
                        notifySettingsChanged()
                        renderWebApps()
                        dialog.dismiss()
                    }
                }
            }
    }

    private fun confirmDelete(entry: WebAppEntry) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.remove_web_app)
            .setMessage(getString(R.string.remove_web_app_message, entry.name))
            .setPositiveButton(R.string.delete_button) { _, _ ->
                webAppManager.removeWebApp(entry.id)
                notifySettingsChanged()
                renderWebApps()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun isSupportedWebUrl(rawUrl: String): Boolean {
        val normalized = webAppManager.normalizeUrl(rawUrl)
        return normalized.startsWith("https://", ignoreCase = true)
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply { setPackage(packageName) })
    }
}
