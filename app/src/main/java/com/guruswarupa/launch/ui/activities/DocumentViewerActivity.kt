package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.ui.adapters.PdfPageAdapterLazy
import com.guruswarupa.launch.ui.adapters.SlidePreviewAdapter
import com.guruswarupa.launch.ui.document.DocumentContentExtractor
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

class DocumentViewerActivity : VaultBaseActivity() {

    private lateinit var wallpaperBackground: ImageView
    private lateinit var wallpaperOverlay: View
    private lateinit var documentTitle: TextView
    private lateinit var documentPageInfo: TextView
    private lateinit var pdfRecyclerView: RecyclerView
    private lateinit var documentWebView: WebView
    private lateinit var loadingContainer: View
    private lateinit var loadingText: TextView
    private lateinit var errorContainer: View
    private lateinit var errorText: TextView
    private lateinit var openExternalButton: TextView
    private lateinit var pdfNavBar: LinearLayout
    private lateinit var pageIndicator: TextView
    private lateinit var btnPrevPage: ImageView
    private lateinit var btnNextPage: ImageView
    private lateinit var shareButton: ImageView
    
    // PowerPoint-specific views
    private lateinit var slideScrollView: HorizontalScrollView
    private lateinit var slideContainer: LinearLayout
    private lateinit var slidePreviewContainer: LinearLayout
    private lateinit var slidePreviewRecyclerView: RecyclerView
    private lateinit var slidePageIndicator: TextView
    private lateinit var slidePreviewAdapter: SlidePreviewAdapter

    private var vaultManager: EncryptedFolderManager? = null
    private var tempFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var totalPages = 0
    private var currentPageIndex = 0
    
    // PowerPoint slides
    private var pptSlides: List<String> = emptyList()
    private var pptCurrentSlideIndex = 0

    // Zoom and Pan variables
    private var scaleFactor = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    // Source modes
    private var isVaultFile = false
    private var vaultFileName: String? = null
    private var externalUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_document_viewer)

        initViews()
        setupWallpaper()
        setupZoomAndPan()

        // Determine source
        vaultFileName = intent.getStringExtra(EXTRA_VAULT_FILE_NAME)
        val uriString = intent.getStringExtra(EXTRA_FILE_URI)

        if (!vaultFileName.isNullOrEmpty()) {
            isVaultFile = true
            vaultManager = EncryptedFolderManager(this)
            if (!vaultManager!!.isUnlocked()) {
                Toast.makeText(this, "Vault is locked", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            documentTitle.text = vaultFileName
            loadVaultDocument(vaultFileName!!)
        } else if (uriString != null) {
            externalUri = Uri.parse(uriString)
            val displayName = intent.getStringExtra(EXTRA_FILE_NAME) ?: getFileNameFromUri(externalUri!!) ?: "Document"
            documentTitle.text = displayName
            loadExternalDocument(externalUri!!, displayName)
        } else if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            externalUri = intent.data
            val displayName = getFileNameFromUri(externalUri!!) ?: "Document"
            documentTitle.text = displayName
            loadExternalDocument(externalUri!!, displayName)
        } else {
            showError("No document provided")
        }

        setupClickListeners()
    }

    private fun initViews() {
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        wallpaperOverlay = findViewById(R.id.document_viewer_overlay)
        documentTitle = findViewById(R.id.document_title)
        documentPageInfo = findViewById(R.id.document_page_info)
        pdfRecyclerView = findViewById(R.id.pdf_recycler_view)
        documentWebView = findViewById(R.id.document_webview)
        loadingContainer = findViewById(R.id.loading_container)
        loadingText = findViewById(R.id.loading_text)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_text)
        openExternalButton = findViewById(R.id.open_external_button)
        pdfNavBar = findViewById(R.id.pdf_nav_bar)
        pageIndicator = findViewById(R.id.page_indicator)
        btnPrevPage = findViewById(R.id.btn_prev_page)
        btnNextPage = findViewById(R.id.btn_next_page)
        shareButton = findViewById(R.id.share_button)
        
        // PowerPoint-specific views
        slideScrollView = findViewById(R.id.slide_scroll_view)
        slideContainer = findViewById(R.id.slide_container)
        slidePreviewContainer = findViewById(R.id.slide_preview_container)
        slidePreviewRecyclerView = findViewById(R.id.slide_preview_recycler_view)
        slidePageIndicator = findViewById(R.id.slide_page_indicator)
    }

    private fun setupWallpaper() {
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground)
        wallpaperOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.note_editor_overlay))
    }

    private fun setupZoomAndPan() {
        // Use top-left pivot with explicit focus-point math so zoom follows finger position.
        pdfRecyclerView.post {
            pdfRecyclerView.pivotX = 0f
            pdfRecyclerView.pivotY = 0f
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentScale = scaleFactor
                val requestedScale = currentScale * detector.scaleFactor
                val nextScale = requestedScale.coerceIn(1.0f, 6.0f)
                val relativeScale = nextScale / currentScale

                val focusX = detector.focusX
                val focusY = detector.focusY

                // Keep the content point under the pinch focus stationary.
                pdfRecyclerView.translationX =
                    pdfRecyclerView.translationX * relativeScale + focusX * (1 - relativeScale)
                pdfRecyclerView.translationY =
                    pdfRecyclerView.translationY * relativeScale + focusY * (1 - relativeScale)

                scaleFactor = nextScale
                pdfRecyclerView.scaleX = scaleFactor
                pdfRecyclerView.scaleY = scaleFactor

                applyBounds()
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scaleFactor > 1.0f) {
                    scaleFactor = 1.0f
                    pdfRecyclerView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(300)
                        .start()
                } else {
                    val targetScale = 2.5f
                    val relativeScale = targetScale / scaleFactor
                    val targetX = pdfRecyclerView.translationX * relativeScale + e.x * (1 - relativeScale)
                    val targetY = pdfRecyclerView.translationY * relativeScale + e.y * (1 - relativeScale)

                    scaleFactor = targetScale
                    pdfRecyclerView.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .translationX(targetX)
                        .translationY(targetY)
                        .setDuration(300)
                        .start()
                }
                return true
            }
        })

        pdfRecyclerView.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            val isPinching = scaleGestureDetector.isInProgress
            val isMultiTouch = event.pointerCount > 1
            val shouldHandleZoomGesture = scaleFactor > 1.0f || isPinching || isMultiTouch

            if (shouldHandleZoomGesture) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(scaleFactor > 1.0f)
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        val (focusX, focusY) = getTouchFocus(event)
                        lastTouchX = focusX
                        lastTouchY = focusY
                        pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPinching) {
                            // Pinch updates are handled in onScale(). Avoid extra pan here to prevent flicker.
                            val (focusX, focusY) = getTouchFocus(event)
                            lastTouchX = focusX
                            lastTouchY = focusY
                            return@setOnTouchListener true
                        } else if (isMultiTouch && scaleFactor > 1.0f) {
                            val (focusX, focusY) = getTouchFocus(event)
                            val dx = focusX - lastTouchX
                            val dy = focusY - lastTouchY
                            pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                            pdfRecyclerView.translationX += dx
                            pdfRecyclerView.translationY += dy
                            applyBounds()
                            lastTouchX = focusX
                            lastTouchY = focusY
                            return@setOnTouchListener true
                        } else {
                            if (scaleFactor <= 1.0f) return@setOnTouchListener false
                            // In zoom mode, single-finger drag should pan in both axes so edges stay reachable.
                            val dx = event.x - lastTouchX
                            val dy = event.y - lastTouchY
                            pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                            pdfRecyclerView.translationX += dx
                            pdfRecyclerView.translationY += dy
                            applyBounds()
                            lastTouchX = event.x
                            lastTouchY = event.y
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pdfRecyclerView.parent?.requestDisallowInterceptTouchEvent(scaleFactor > 1.0f)
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun getTouchFocus(event: MotionEvent): Pair<Float, Float> {
        var sumX = 0f
        var sumY = 0f
        val count = event.pointerCount.coerceAtLeast(1)
        for (i in 0 until count) {
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        return Pair(sumX / count, sumY / count)
    }

    private fun applyBounds() {
        if (scaleFactor <= 1.0f) {
            pdfRecyclerView.translationX = 0f
            pdfRecyclerView.translationY = 0f
            return
        }

        val width = pdfRecyclerView.width.toFloat()
        val height = pdfRecyclerView.height.toFloat()
        val minX = width - (width * scaleFactor)
        val minY = height - (height * scaleFactor)

        pdfRecyclerView.translationX = max(minX, min(pdfRecyclerView.translationX, 0f))
        pdfRecyclerView.translationY = max(minY, min(pdfRecyclerView.translationY, 0f))
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }

        shareButton.setOnClickListener { shareDocument() }

        openExternalButton.setOnClickListener { openWithExternalApp() }

        btnPrevPage.setOnClickListener {
            val layoutManager = pdfRecyclerView.layoutManager as? LinearLayoutManager ?: return@setOnClickListener
            val currentPos = getCurrentPageIndex(layoutManager)
            if (currentPos > 0) {
                val target = currentPos - 1
                currentPageIndex = target
                updatePageIndicator(currentPageIndex + 1)
                pdfRecyclerView.smoothScrollToPosition(target)
            }
        }

        btnNextPage.setOnClickListener {
            val layoutManager = pdfRecyclerView.layoutManager as? LinearLayoutManager ?: return@setOnClickListener
            val currentPos = getCurrentPageIndex(layoutManager)
            if (currentPos < totalPages - 1) {
                val target = currentPos + 1
                currentPageIndex = target
                updatePageIndicator(currentPageIndex + 1)
                pdfRecyclerView.smoothScrollToPosition(target)
            }
        }
    }

    private fun loadVaultDocument(fileName: String) {
        showLoading("Decrypting document…")
        lifecycleScope.launch {
            try {
                val decryptedFile = withContext(Dispatchers.IO) {
                    vaultManager!!.decryptToCache(fileName)
                }
                tempFile = decryptedFile
                openDocument(decryptedFile, fileName)
            } catch (e: Exception) {
                showError("Failed to decrypt: ${e.message}")
            }
        }
    }

    private fun loadExternalDocument(uri: Uri, displayName: String) {
        showLoading("Loading document…")
        lifecycleScope.launch {
            try {
                val cachedFile = withContext(Dispatchers.IO) {
                    copyUriToCache(uri, displayName)
                }
                tempFile = cachedFile
                openDocument(cachedFile, displayName)
            } catch (e: Exception) {
                showError("Failed to load: ${e.message}")
            }
        }
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File {
        val cacheDir = File(cacheDir, "doc_viewer")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val destFile = File(cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open file")
        return destFile
    }

    private fun openDocument(file: File, fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""

        when {
            extension == "pdf" || mimeType == "application/pdf" -> {
                openPdf(file)
            }
            extension in listOf("doc", "docx") || mimeType.contains("msword") || mimeType.contains("wordprocessingml") -> {
                openOfficeDocument(file, extension)
            }
            extension in listOf("ppt", "pptx") || mimeType.contains("powerpoint") || mimeType.contains("presentationml") -> {
                openOfficeDocument(file, extension)
            }
            extension in listOf("xls", "xlsx") || mimeType.contains("excel") || mimeType.contains("spreadsheetml") -> {
                openOfficeDocument(file, extension)
            }
            extension in listOf("txt", "md", "json", "xml", "csv", "log", "html", "htm", "css", "js", "kt", "java", "py", "c", "cpp", "h", "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bat", "ps1", "rb", "go", "rs", "swift", "dart", "ts", "tsx", "jsx", "vue", "sql") -> {
                openTextFile(file)
            }
            mimeType.startsWith("text/") -> {
                openTextFile(file)
            }
            extension in listOf("rtf") || mimeType.contains("rtf") -> {
                openOfficeDocument(file, extension)
            }
            else -> {
                // Try to open as text first, fallback to error
                try {
                    val content = file.readText(Charsets.UTF_8)
                    if (content.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
                        showError("Unsupported document format: .$extension")
                    } else {
                        openTextFile(file)
                    }
                } catch (e: Exception) {
                    showError("Unsupported document format: .$extension")
                }
            }
        }
    }

    // ─── PDF Rendering ───────────────────────────────────────────────────────────

    private fun openPdf(file: File) {
        lifecycleScope.launch {
            try {
                // Close any existing renderer first
                pdfRenderer?.close()
                fileDescriptor?.close()
                
                withContext(Dispatchers.IO) {
                    fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    totalPages = pdfRenderer!!.pageCount
                }

                hideLoading()
                pdfRecyclerView.visibility = View.VISIBLE
                pdfNavBar.visibility = View.VISIBLE

                documentPageInfo.visibility = View.VISIBLE
                documentPageInfo.text = "$totalPages page${if (totalPages != 1) "s" else ""}"

                val layoutManager = LinearLayoutManager(this@DocumentViewerActivity)
                pdfRecyclerView.layoutManager = layoutManager
                pdfRecyclerView.adapter = PdfPageAdapterLazy(pdfRenderer!!, totalPages)

                currentPageIndex = 0
                updatePageIndicator(currentPageIndex + 1)

                pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val page = getCurrentPageIndex(layoutManager)
                        if (page != currentPageIndex) {
                            currentPageIndex = page
                            updatePageIndicator(currentPageIndex + 1)
                        }
                    }
                })

            } catch (e: OutOfMemoryError) {
                showError("PDF is too large to preview. Please use 'Open with' to view in another app.")
            } catch (e: Exception) {
                showError("Failed to render PDF: ${e.message}")
            }
        }
    }

    private fun updatePageIndicator(currentPage: Int) {
        pageIndicator.text = "Page $currentPage of $totalPages"
        btnPrevPage.alpha = if (currentPage <= 1) 0.3f else 1.0f
        btnNextPage.alpha = if (currentPage >= totalPages) 0.3f else 1.0f
    }

    private fun getCurrentPageIndex(layoutManager: LinearLayoutManager): Int {
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return 0

        val viewportTop = pdfRecyclerView.paddingTop
        val viewportBottom = pdfRecyclerView.height - pdfRecyclerView.paddingBottom
        var bestIndex = first
        var bestVisibleHeight = -1
        var bestDistanceToCenter = Int.MAX_VALUE
        val centerY = (viewportTop + viewportBottom) / 2

        for (pos in first..last) {
            val child = layoutManager.findViewByPosition(pos) ?: continue
            val visibleTop = max(child.top, viewportTop)
            val visibleBottom = min(child.bottom, viewportBottom)
            val visibleHeight = visibleBottom - visibleTop
            if (visibleHeight <= 0) continue

            val childCenterY = (child.top + child.bottom) / 2
            val distanceToCenter = kotlin.math.abs(childCenterY - centerY)

            if (visibleHeight > bestVisibleHeight ||
                (visibleHeight == bestVisibleHeight && distanceToCenter < bestDistanceToCenter) ||
                (visibleHeight == bestVisibleHeight && distanceToCenter == bestDistanceToCenter && pos > bestIndex)
            ) {
                bestVisibleHeight = visibleHeight
                bestDistanceToCenter = distanceToCenter
                bestIndex = pos
            }
        }

        return bestIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    }

    // ─── Office Document Rendering ──────────────────────────────────────────────

    private fun openOfficeDocument(file: File, extension: String) {
        lifecycleScope.launch {
            try {
                val isPowerPoint = extension in listOf("ppt", "pptx")
                
                if (isPowerPoint) {
                    // Handle PowerPoint with slide viewer
                    val htmlContent = withContext(Dispatchers.IO) {
                        when (extension) {
                            "ppt", "pptx" -> DocumentContentExtractor.extractPptContent(file)
                            else -> null
                        }
                    }
                    
                    if (htmlContent != null) {
                        hideLoading()
                        showPowerPointContent(htmlContent)
                    } else {
                        showError("Cannot preview this document format in-app")
                    }
                } else {
                    // Handle Word, Excel, etc. with WebView
                    val htmlContent = withContext(Dispatchers.IO) {
                        when (extension) {
                            "doc", "docx" -> DocumentContentExtractor.extractWordContent(file)
                            "xls", "xlsx" -> DocumentContentExtractor.extractExcelContent(file)
                            "rtf" -> DocumentContentExtractor.extractRtfContent(file)
                            else -> null
                        }
                    }

                    if (htmlContent != null) {
                        hideLoading()
                        showWebViewContent(htmlContent)
                    } else {
                        showError("Cannot preview this document format in-app")
                    }
                }
            } catch (e: OutOfMemoryError) {
                showError("File is too large to preview. Please use 'Open with' to view in another app.")
            } catch (e: Exception) {
                showError("Failed to open document: ${e.message}")
            }
        }
    }

    // ─── Text File Rendering ────────────────────────────────────────────────────

    private fun openTextFile(file: File) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    file.readText(Charsets.UTF_8)
                }

                val extension = file.extension.lowercase()
                val html = DocumentContentExtractor.buildTextFileHtml(content, extension)

                hideLoading()
                showWebViewContent(html)
            } catch (e: Exception) {
                showError("Failed to read file: ${e.message}")
            }
        }
    }

    // ─── WebView Display ────────────────────────────────────────────────────────

    private fun showWebViewContent(html: String) {
        // Clear previous content to prevent memory leaks
        documentWebView.clearHistory()
        documentWebView.clearCache(true)
        documentWebView.loadUrl("about:blank")
        
        documentWebView.visibility = View.VISIBLE
        documentWebView.setBackgroundColor(Color.TRANSPARENT)
        documentWebView.settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // Force dark mode off for better document viewing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
            }
        }
        documentWebView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                @Suppress("DEPRECATION")
                super.onReceivedError(view, errorCode, description, failingUrl)
                showError("Failed to load document content")
            }
        }
        documentWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    
    // ─── PowerPoint Display ────────────────────────────────────────────────────────
    
    private fun showPowerPointContent(html: String) {
        // Parse slides from HTML
        pptSlides = parseSlidesFromHtml(html)
        pptCurrentSlideIndex = 0

        if (pptSlides.isEmpty()) {
            showError("No slides found in this presentation")
            return
        }
        
        // Hide other views
        pdfRecyclerView.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
        
        // Show slide-specific views
        slideScrollView.visibility = View.VISIBLE
        slideContainer.visibility = View.VISIBLE
        slidePreviewRecyclerView.visibility = View.VISIBLE
        slidePreviewContainer.visibility = View.VISIBLE
        
        slideContainer.removeAllViews()
        
        // Create WebViews for each slide with spacing between them
        for ((index, slideHtml) in pptSlides.withIndex()) {
            val webView = createSlideWebView(slideHtml, index)
            slideContainer.addView(webView)
            
            // Add spacing after each slide except the last one
            if (index < pptSlides.size - 1) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(48, LinearLayout.LayoutParams.MATCH_PARENT)
                }
                slideContainer.addView(spacer)
            }
        }
        
        // Setup preview RecyclerView
        slidePreviewRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        slidePreviewAdapter = SlidePreviewAdapter(pptSlides, pptCurrentSlideIndex) { position ->
            navigateToSlide(position)
        }
        slidePreviewRecyclerView.adapter = slidePreviewAdapter
        
        // Update indicator
        updateSlidePageIndicator(1)
        
        // Scroll listener to update indicator and sync with preview
        slideScrollView.setOnScrollChangeListener { v, scrollX, _, _, _ ->
            val newIndex = findCenteredSlideIndex(scrollX)
            
            if (newIndex != pptCurrentSlideIndex) {
                pptCurrentSlideIndex = newIndex
                updateSlidePageIndicator(pptCurrentSlideIndex + 1)
                centerPreviewOnSlide(pptCurrentSlideIndex)
                slidePreviewAdapter.updateCurrentSlide(pptCurrentSlideIndex)
            }
        }
        
        // Setup navigation buttons
        btnPrevPage.alpha = 0.3f
        btnNextPage.alpha = if (pptSlides.size > 1) 1.0f else 0.3f
        
        btnPrevPage.setOnClickListener {
            if (pptCurrentSlideIndex > 0) {
                navigateToSlide(pptCurrentSlideIndex - 1)
            }
        }
        
        btnNextPage.setOnClickListener {
            if (pptCurrentSlideIndex < pptSlides.size - 1) {
                navigateToSlide(pptCurrentSlideIndex + 1)
            }
        }

        slideContainer.post {
            navigateToSlide(0, smoothScroll = false)
            slideContainer.requestLayout()
            slideContainer.invalidate()
        }
    }
    
    private fun parseSlidesFromHtml(html: String): List<String> {
        val slides = mutableListOf<String>()
        val slidePattern = Regex("<div class='slide'>(.*?)</div></div>")
        val matches = slidePattern.findAll(html)
        
        for (match in matches) {
            slides.add(match.groupValues[1])
        }
        
        return if (slides.isEmpty()) listOf(html) else slides
    }
    
    private fun createSlideWebView(slideContent: String, index: Int): WebView {
        val webView = WebView(this)
        
        // Calculate height based on standard 16:9 aspect ratio
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = (screenWidth * 9 / 16).coerceAtMost(resources.displayMetrics.heightPixels)
        
        // Calculate scale factor relative to desktop PowerPoint (assuming 1920x1080 as reference)
        val referenceWidth = 1920f
        val scaleFactor = screenWidth.toFloat() / referenceWidth
        
        webView.apply {
            layoutParams = LinearLayout.LayoutParams(
                screenWidth,
                screenHeight
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                // Set text zoom to match scale factor
                textZoom = (scaleFactor * 100).toInt().coerceIn(50, 150)
            }
            alpha = 0f
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    this@apply.alpha = 1f
                    this@apply.postInvalidateOnAnimation()
                }
            }
            
            val slideHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        html, body {
                            width: 100%;
                            height: 100%;
                            overflow: hidden;
                        }
                        body {
                            font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                            background: #FFFFFF;
                            color: #333;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                        }
                        .slide-container {
                            width: 100%;
                            height: 100%;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 16px;
                        }
                        .slide {
                            background: #FFFFFF;
                            width: 100%;
                            height: 100%;
                            display: flex;
                            flex-direction: column;
                            justify-content: flex-start;
                            transform-origin: center center;
                            overflow-y: auto;
                            overflow-x: hidden;
                            padding-right: 4px;
                        }
                        .slide h3 {
                            color: #1a1a1a;
                            margin: 0 0 ${Math.round(20 * scaleFactor)}px 0;
                            font-size: ${Math.round(36 * scaleFactor)}px;
                            border-bottom: ${Math.round(2 * scaleFactor)}px solid #f0f0f0;
                            padding-bottom: ${Math.round(12 * scaleFactor)}px;
                            font-weight: 600;
                            text-align: center;
                            line-height: 1.2;
                            overflow-wrap: anywhere;
                            word-break: break-word;
                        }
                        .slide p {
                            margin: ${Math.round(8 * scaleFactor)}px 0;
                            line-height: 1.4;
                            color: #333;
                            font-size: ${Math.round(20 * scaleFactor)}px;
                            text-align: left;
                            overflow-wrap: anywhere;
                            word-break: break-word;
                            white-space: pre-wrap;
                        }
                        .empty { opacity: 0.5; font-style: italic; color: #666; font-size: ${Math.round(20 * scaleFactor)}px; }
                    </style>
                </head>
                <body>
                    <div class="slide-container">
                        <div class="slide">
                            $slideContent
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            loadDataWithBaseURL(null, slideHtml, "text/html", "UTF-8", null)
        }
        
        return webView
    }
    
    private fun navigateToSlide(index: Int, smoothScroll: Boolean = true) {
        if (index in 0 until pptSlides.size) {
            pptCurrentSlideIndex = index
            
            val viewportWidth = slideScrollView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val targetSlideView = slideContainer.getChildAt(index * 2)
            val targetLeft = targetSlideView?.left ?: 0
            val targetWidth = targetSlideView?.width ?: resources.displayMetrics.widthPixels
            val maxScroll = (slideContainer.width - viewportWidth).coerceAtLeast(0)
            val scrollPosition = (targetLeft - ((viewportWidth - targetWidth) / 2)).coerceIn(0, maxScroll)
            
            if (smoothScroll) {
                slideScrollView.smoothScrollTo(scrollPosition, 0)
            } else {
                slideScrollView.scrollTo(scrollPosition, 0)
            }
            updateSlidePageIndicator(index + 1)
            
            // Update adapter to refresh border colors and current slide indicator
            slidePreviewAdapter.updateCurrentSlide(index)
            centerPreviewOnSlide(index, smoothScroll)
        }
    }
    
    private fun updateSlidePageIndicator(currentPage: Int) {
        slidePageIndicator.text = "Slide $currentPage of ${pptSlides.size}"
        btnPrevPage.alpha = if (currentPage <= 1) 0.3f else 1.0f
        btnNextPage.alpha = if (currentPage >= pptSlides.size) 0.3f else 1.0f
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun centerPreviewOnSlide(index: Int, smoothScroll: Boolean = true) {
        val layoutManager = slidePreviewRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val itemWidth = dpToPx(160)
        val viewportWidth = slidePreviewRecyclerView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val offset = ((viewportWidth - itemWidth) / 2).coerceAtLeast(0)
        if (smoothScroll) {
            slidePreviewRecyclerView.post {
                layoutManager.scrollToPositionWithOffset(index, offset)
            }
        } else {
            layoutManager.scrollToPositionWithOffset(index, offset)
        }
    }

    private fun findCenteredSlideIndex(scrollX: Int): Int {
        if (pptSlides.isEmpty()) return 0

        val viewportWidth = slideScrollView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val viewportCenterX = scrollX + (viewportWidth / 2)
        var closestSlideIndex = 0
        var closestDistance = Int.MAX_VALUE

        for (slideIndex in pptSlides.indices) {
            val slideView = slideContainer.getChildAt(slideIndex * 2) ?: continue
            val slideCenterX = slideView.left + (slideView.width / 2)
            val distance = kotlin.math.abs(slideCenterX - viewportCenterX)
            if (distance < closestDistance) {
                closestDistance = distance
                closestSlideIndex = slideIndex
            }
        }

        return closestSlideIndex
    }

    private fun showLoading(message: String) {
        // Reset all views first
        pdfRecyclerView.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
        slideScrollView.visibility = View.GONE
        slideContainer.visibility = View.GONE
        slidePreviewContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        
        // Clear previous content to free memory
        pdfRecyclerView.adapter = null
        documentWebView.clearHistory()
        documentWebView.clearCache(true)
        documentWebView.loadUrl("about:blank")
        
        loadingText.text = message
        loadingContainer.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingContainer.visibility = View.GONE
        pdfRecyclerView.visibility = View.GONE
        documentWebView.visibility = View.GONE
        pdfNavBar.visibility = View.GONE
        slideScrollView.visibility = View.GONE
        slideContainer.visibility = View.GONE
        slidePreviewContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun shareDocument() {
        val file = tempFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "No document to share", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share document"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWithExternalApp() {
        val file = tempFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "No document available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open with…"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up PDF renderer
        pdfRenderer?.close()
        fileDescriptor?.close()
        
        // Clean up WebView to prevent memory leaks
        documentWebView.clearHistory()
        documentWebView.clearCache(true)
        documentWebView.loadUrl("about:blank")
        documentWebView.destroy()
        
        // Clean up slide container WebViews
        for (i in 0 until slideContainer.childCount) {
            val child = slideContainer.getChildAt(i)
            if (child is WebView) {
                child.clearHistory()
                child.clearCache(true)
                child.loadUrl("about:blank")
                child.destroy()
            }
        }
        slideContainer.removeAllViews()
        
        // Clean up preview RecyclerView
        slidePreviewRecyclerView.adapter = null
        
        // Clean up temp files
        tempFile?.let { file ->
            val parentDir = file.parentFile
            if (parentDir?.name == "doc_viewer") {
                file.delete()
            }
        }
    }

    companion object {
        const val EXTRA_VAULT_FILE_NAME = "vault_file_name"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_FILE_NAME = "file_name"

        /** Supported document extensions */
        val SUPPORTED_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "rtf",
            "txt", "md", "json", "xml", "csv", "log", "html", "htm",
            "css", "js", "kt", "java", "py", "c", "cpp", "h",
            "yaml", "yml", "toml", "ini", "cfg", "conf", "sh", "bat",
            "rb", "go", "rs", "swift", "dart", "ts", "tsx", "jsx", 
            "vue", "sql", "ps1"
        )

        fun isSupported(fileName: String): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_EXTENSIONS
        }

        fun createVaultIntent(context: Context, fileName: String): Intent {
            return Intent(context, DocumentViewerActivity::class.java).apply {
                putExtra(EXTRA_VAULT_FILE_NAME, fileName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
        }

        fun createFileIntent(context: Context, uri: Uri, fileName: String? = null): Intent {
            return Intent(context, DocumentViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, uri.toString())
                if (fileName != null) putExtra(EXTRA_FILE_NAME, fileName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}
