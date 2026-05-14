package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
        const val EXTRA_BLOCK_REDIRECTS = "block_redirects"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var addressView: TextView
    private lateinit var fullscreenContainer: FrameLayout

    private var allowedDomain: String? = null
    private var blockRedirects: Boolean = true

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


        allowedDomain = extractDomain(url)


        blockRedirects = intent.getBooleanExtra(EXTRA_BLOCK_REDIRECTS, true)

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


                if (scheme != "http" && scheme != "https") {

                    val shouldOpen = try {
                        android.app.AlertDialog.Builder(this@WebAppActivity, R.style.CustomDialogTheme)
                            .setTitle("Open External App?")
                            .setMessage("This will open an external app: $scheme")
                            .setPositiveButton("Open") { _, _ ->
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, targetUrl.toUri()))
                                } catch (e: Exception) {
                                    Toast.makeText(this@WebAppActivity, "No app available", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        false
                    } catch (e: Exception) {
                        false
                    }
                    return shouldOpen
                }


                val targetDomain = extractDomain(targetUrl)
                val isSameDomain = targetDomain != null && isDomainAllowed(targetDomain)


                if (blockRedirects && !isSameDomain) {
                    Toast.makeText(
                        this@WebAppActivity,
                        "Navigation blocked: Redirecting to external domain is not allowed",
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }


                if (!blockRedirects && !isSameDomain) {
                    Toast.makeText(
                        this@WebAppActivity,
                        "Warning: Navigating to external domain: $targetDomain",
                        Toast.LENGTH_SHORT
                    ).show()
                }


                return false
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
            webView.pauseTimers()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.resumeTimers()
            webView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::webView.isInitialized) {
            webView.stopLoading()
        }
        if (isFinishing) {
            releaseWebView()
        }
    }

    override fun onDestroy() {
        exitFullscreen()
        releaseWebView()
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


    private fun extractDomain(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.host
        } catch (e: Exception) {
            null
        }
    }


    private fun isDomainAllowed(domain: String): Boolean {
        val allowed = allowedDomain ?: return false


        if (domain == allowed) return true


        val domainWithoutWww = domain.removePrefix("www.")
        val allowedWithoutWww = allowed.removePrefix("www.")

        if (domainWithoutWww == allowedWithoutWww) return true



        if (domainWithoutWww.endsWith(".$allowedWithoutWww")) return true
        if (allowedWithoutWww.endsWith(".$domainWithoutWww")) return true

        return false
    }

    private fun releaseWebView() {
        if (!::webView.isInitialized) {
            return
        }
        
        try {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            
            webView.apply {
                try {
                    stopLoading()
                    onPause()
                    pauseTimers()
                    loadUrl("about:blank")
                    clearHistory()
                    clearCache(false)
                } catch (e: Exception) {
                    android.util.Log.w("WebAppActivity", "Error during WebView cleanup operations", e)
                } finally {
                    // Always clear clients and views even if above fails
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    removeAllViews()
                    
                    val parentView = parent as? ViewGroup
                    if (parentView != null) {
                        try {
                            parentView.removeView(this)
                        } catch (e: Exception) {
                            android.util.Log.w("WebAppActivity", "WebView already removed from parent", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WebAppActivity", "Unexpected error during WebView release", e)
        } finally {
            // Ensure destroy is always called
            try {
                webView.destroy()
            } catch (e: Exception) {
                android.util.Log.w("WebAppActivity", "Error destroying WebView", e)
            }
        }
    }
}
