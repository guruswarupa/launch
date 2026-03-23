package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppAdBlocker
import com.guruswarupa.launch.managers.WebAppIconFetcher

class WebAppActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_WEB_APP_NAME = "web_app_name"
        const val EXTRA_WEB_APP_URL = "web_app_url"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var addressView: TextView
    private lateinit var fullscreenContainer: FrameLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    
    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
            }
            fileUploadCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }
    
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        val appName = intent.getStringExtra(EXTRA_WEB_APP_NAME).orEmpty()
        val url = intent.getStringExtra(EXTRA_WEB_APP_URL).orEmpty()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_web_app)

        if (appName.isBlank() || url.isBlank()) {
            finish()
            return
        }
        
        title = appName
        
        WebAppIconFetcher.loadIcon(this, url) { drawable ->
            if (drawable != null) {
                try {
                    val bitmap = createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1)
                    ).applyCanvas {
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(this)
                    }
                    
                    @Suppress("DEPRECATION")
                    val taskDescription = ActivityManager.TaskDescription(
                        appName,
                        bitmap,
                        Color.TRANSPARENT
                    )
                    setTaskDescription(taskDescription)
                } catch (_: Exception) {
                }
            }
        }
        
        titleView = findViewById<TextView>(R.id.web_app_title).apply { text = appName }
        addressView = findViewById<TextView>(R.id.web_app_address).apply { text = url }
        progressBar = findViewById(R.id.web_app_progress)
        webView = findViewById(R.id.web_app_webview)
        fullscreenContainer = findViewById(R.id.web_app_fullscreen_container)

        findViewById<ImageButton>(R.id.web_app_back_button).setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        findViewById<ImageButton>(R.id.web_app_refresh_button).setOnClickListener {
            webView.reload()
        }
        findViewById<ImageButton>(R.id.web_app_browser_button).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, (webView.url ?: url).toUri()))
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
            
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                val isVideo = acceptTypes.any { it.startsWith("video/") }
                val isImage = acceptTypes.any { it.startsWith("image/") || it == "*/*" }
                try {
                    val mimeType = when {
                        isVideo -> "video/*"
                        isImage -> "image/*"
                        else -> "*/*"
                    }
                    mediaPickerLauncher.launch(mimeType)
                    return true
                } catch (_: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                }
            }
        }
        
        val root = findViewById<View>(R.id.web_app_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemInsets.top, 0, systemInsets.bottom)
            insets
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (WebAppAdBlocker.shouldBlock(request?.url)) {
                    return WebAppAdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                if (WebAppAdBlocker.shouldBlock(url?.toUri())) {
                    return WebAppAdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val targetUrl = targetUri.toString()
                
                if (WebAppAdBlocker.shouldBlock(targetUri)) {
                    return true
                }

                val scheme = targetUri.scheme.orEmpty()
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, targetUrl.toUri()))
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                addressView.text = url ?: intent.getStringExtra(EXTRA_WEB_APP_URL).orEmpty()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@WebAppActivity, R.string.web_app_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    exitFullscreen()
                    return
                }
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::webView.isInitialized) {
            webView.stopLoading()
        }
    }

    override fun onDestroy() {
        exitFullscreen()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        fileUploadCallback = null
        super.onDestroy()
    }

    private fun exitFullscreen() {
        customView?.let {
            fullscreenContainer.removeView(it)
            customView = null
        }
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }
}
