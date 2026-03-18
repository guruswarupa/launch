package com.guruswarupa.launch.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    private lateinit var scrollView: View
    private lateinit var overlayView: View
    
    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge with translucent status bar
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_web_app_settings)

        // Apply content insets like SettingsActivity does
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.web_apps_scroll_view).setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        // Apply wallpaper blur effect
        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.web_apps_wallpaper))

        listContainer = findViewById(R.id.web_apps_list)
        emptyView = findViewById(R.id.web_apps_empty)
        scrollView = findViewById(R.id.web_apps_scroll_view)
        overlayView = findViewById<View>(R.id.web_apps_overlay)

        // Apply dynamic background translucency to match settings page
        applyBackgroundTranslucency()

        // Setup back button with ripple effect
        findViewById<ImageButton>(R.id.web_apps_back_button).setOnClickListener { 
            animateFinish()
        }
        
        // Setup add button with scale animation
        findViewById<Button>(R.id.add_web_app_button).setOnClickListener { 
            animateButtonClick(it)
            showEditorDialog() 
        }

        renderWebApps()
    }

    private fun renderWebApps() {
        val webApps = webAppManager.getWebApps()
        listContainer.removeAllViews()
        
        // Animate empty state or list
        if (webApps.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.alpha = 0f
            ObjectAnimator.ofFloat(emptyView, "alpha", 0f, 1f).apply {
                duration = 300
                start()
            }
        } else {
            emptyView.visibility = View.GONE
        }

        val inflater = LayoutInflater.from(this)
        webApps.forEachIndexed { index, entry ->
            val itemView = inflater.inflate(R.layout.item_web_app, listContainer, false)
            val iconView = itemView.findViewById<android.widget.ImageView>(R.id.web_app_item_icon)
            itemView.findViewById<TextView>(R.id.web_app_item_name).text = entry.name
            itemView.findViewById<TextView>(R.id.web_app_item_url).text = entry.url
            
            // Load icon with fade-in animation
            WebAppIconFetcher.loadIcon(this, entry.url) { drawable ->
                if (drawable != null) {
                    iconView.setImageDrawable(drawable)
                    iconView.alpha = 0f
                    ObjectAnimator.ofFloat(iconView, "alpha", 0f, 1f).apply {
                        duration = 300
                        startDelay = index * 50L // Stagger animation
                        start()
                    }
                }
            }
            
            itemView.findViewById<ImageButton>(R.id.web_app_item_open).setOnClickListener {
                val intent = Intent(this, WebAppActivity::class.java).apply {
                    putExtra(WebAppActivity.EXTRA_WEB_APP_NAME, entry.name)
                    putExtra(WebAppActivity.EXTRA_WEB_APP_URL, entry.url)
                    // Always create new task for each web app
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            
            itemView.findViewById<ImageButton>(R.id.web_app_item_edit).setOnClickListener {
                showEditorDialog(entry)
            }
            
            itemView.findViewById<ImageButton>(R.id.web_app_item_delete).setOnClickListener {
                confirmDelete(entry)
            }
            
            listContainer.addView(itemView)
            
            // Animate item entrance
            if (!isAnimating) {
                itemView.alpha = 0f
                itemView.translationY = 50f
                ObjectAnimator.ofFloat(itemView, "alpha", 0f, 1f).apply {
                    duration = 300
                    startDelay = index * 80L
                    interpolator = OvershootInterpolator(1.2f)
                    start()
                }
                ObjectAnimator.ofFloat(itemView, "translationY", 50f, 0f).apply {
                    duration = 300
                    startDelay = index * 80L
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }
    
    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val color = android.graphics.Color.argb(alpha, 0, 0, 0)
        overlayView.setBackgroundColor(color)
    }
    
    private fun animateButtonClick(view: View) {
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
    
    private fun animateFinish() {
        if (isAnimating) return
        isAnimating = true
        
        val root = findViewById<View>(R.id.web_apps_root)
        ObjectAnimator.ofFloat(root, "alpha", 1f, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    isAnimating = false
                }
            })
            start()
        }
    }

    private fun showEditorDialog(existing: WebAppEntry? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_web_app_editor, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.web_app_name_input)
        val urlInput = dialogView.findViewById<EditText>(R.id.web_app_url_input)

        nameInput.setText(existing?.name.orEmpty())
        urlInput.setText(existing?.url.orEmpty())
        urlInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        
        // Add focus listeners for validation feedback
        nameInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && nameInput.text.toString().trim().isBlank()) {
                nameInput.error = getString(R.string.web_app_name_required)
            }
        }
        
        urlInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = urlInput.text.toString().trim()
                if (url.isBlank()) {
                    urlInput.error = getString(R.string.web_app_url_required)
                } else if (!isSupportedWebUrl(url)) {
                    urlInput.error = getString(R.string.web_app_https_required)
                }
            }
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(if (existing == null) R.string.add_web_app else R.string.edit_web_app)
            .setView(dialogView)
            .setPositiveButton(
                if (existing == null) R.string.add_button else R.string.save_button,
                null
            )
            .setNegativeButton(R.string.cancel_button, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val url = urlInput.text.toString().trim()
            when {
                name.isBlank() -> {
                    nameInput.requestFocus()
                    Toast.makeText(
                        this,
                        R.string.web_app_name_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                url.isBlank() -> {
                    urlInput.requestFocus()
                    Toast.makeText(
                        this,
                        R.string.web_app_url_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                !isSupportedWebUrl(url) -> {
                    urlInput.requestFocus()
                    Toast.makeText(
                        this,
                        R.string.web_app_https_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                else -> {
                    if (existing == null) {
                        webAppManager.addWebApp(name, url)
                    } else {
                        webAppManager.updateWebApp(existing.id, name, url)
                    }
                    notifySettingsChanged()
                    renderWebApps()
                    dialog.dismiss()
                    
                    // Show success feedback
                    Toast.makeText(
                        this,
                        if (existing == null) R.string.web_app_added else R.string.web_app_updated,
                        Toast.LENGTH_SHORT
                    ).show()
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
                
                // Show success feedback
                Toast.makeText(
                    this,
                    getString(R.string.web_app_removed, entry.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun isSupportedWebUrl(rawUrl: String): Boolean {
        val normalized = webAppManager.normalizeUrl(rawUrl)
        return normalized.startsWith("https://", ignoreCase = true)
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply {
            setPackage(
                packageName
            )
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear icon cache to free memory
        WebAppIconFetcher.clearCache()
    }
}
