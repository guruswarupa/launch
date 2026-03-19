package com.guruswarupa.launch.managers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import com.guruswarupa.launch.R

/**
 * Helper class for managing wallpaper display with memory optimizations
 */
class WallpaperManagerHelper(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val wallpaperBackground: ImageView,
    private val drawerWallpaperBackground: ImageView?,
    private val backgroundExecutor: java.util.concurrent.ExecutorService
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentWallpaperBitmap: Bitmap? = null
    private var lastWallpaperId: Int = -1
    
    /**
     * Set wallpaper background, with optional force reload
     */
    fun setWallpaperBackground(forceReload: Boolean = false) {
        // Apply blur removed - always set no blur effect
        applyBlurToViews()

        val wallpaperManager = WallpaperManager.getInstance(activity)
        val wallpaperId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        } else {
            -1
        }

        // Check if wallpaper has actually changed or if we need a force reload
        val needsReload = forceReload || 
                         currentWallpaperBitmap == null || 
                         currentWallpaperBitmap!!.isRecycled ||
                         (wallpaperId != -1 && wallpaperId != lastWallpaperId)

        if (!needsReload) {
            return
        }
        
        lastWallpaperId = wallpaperId
        
        // Try to load wallpaper in background to avoid UI jank and large memory spikes on UI thread
        backgroundExecutor.execute {
            try {
                val drawable = wallpaperManager.drawable
                
                if (drawable == null) {
                    handler.post { setDefaultWallpaper() }
                    return@execute
                }
                
                // Optimized bitmap creation
                val bitmap = if (drawable is BitmapDrawable) {
                    val sourceBitmap = drawable.bitmap
                    if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                        // Use a smaller configuration if possible to save RAM
                        sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        null
                    }
                } else {
                    // Fallback for non-bitmap drawables, scale down to screen size
                    val metrics = activity.resources.displayMetrics
                    val width = metrics.widthPixels / 2 // Reduced resolution for background
                    val height = metrics.heightPixels / 2
                    
                    val bm = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    val canvas = android.graphics.Canvas(bm)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bm
                }
                
                if (bitmap != null && !bitmap.isRecycled) {
                    handler.post {
                        updateWallpaperViews(bitmap)
                    }
                } else {
                    handler.post { setDefaultWallpaper() }
                }
            } catch (e: SecurityException) {
                Log.w("WallpaperManagerHelper", "No permission to read wallpaper", e)
                handler.post { setDefaultWallpaper() }
            } catch (e: Exception) {
                Log.e("WallpaperManagerHelper", "Error loading wallpaper", e)
                handler.post { setDefaultWallpaper() }
            }
        }
    }

    private fun updateWallpaperViews(newBitmap: Bitmap) {
        val oldBitmap = currentWallpaperBitmap
        
        // Set new bitmap FIRST before clearing reference to old one
        // This ensures there's never a moment when the ImageView has no drawable
        currentWallpaperBitmap = newBitmap
        if (!newBitmap.isRecycled) {
            wallpaperBackground.setImageBitmap(newBitmap)
            drawerWallpaperBackground?.setImageBitmap(newBitmap)
        }
        
        // DO NOT recycle old bitmap - let Android's garbage collector handle it
        // Recycling while ImageView might still reference it causes crashes
        // The old BitmapDrawable will be garbage collected when no longer referenced
        if (oldBitmap != null && oldBitmap != newBitmap) {
            // Don't recycle - just clear reference
            // oldBitmap.recycle()  // REMOVED - unsafe!
        }
    }

    private fun setDefaultWallpaper() {
        // Clear reference first
        currentWallpaperBitmap = null
        
        // Set drawable resources - this automatically clears bitmap drawables
        wallpaperBackground.setImageResource(R.drawable.wallpaper_background)
        drawerWallpaperBackground?.setImageResource(R.drawable.wallpaper_background)
        
        // DO NOT recycle - unsafe to recycle bitmaps that might still be referenced by views
        // Let Android's garbage collector handle it
    }

    /**
     * Applies or removes blur effect based on user preference
     */
    fun applyBlurToViews() {
        wallpaperBackground.setRenderEffect(null)
        drawerWallpaperBackground?.setRenderEffect(null)
        activity.findViewById<ImageView>(R.id.right_drawer_wallpaper)?.setRenderEffect(null)
    }
    
    /**
     * Clear cached wallpaper bitmap
     */
    fun clearCache() {
        // Just clear reference - don't recycle
        // Recycling bitmaps that might be in use by ImageViews causes crashes
        currentWallpaperBitmap = null
        lastWallpaperId = -1
        
        // DO NOT recycle - unsafe!
        // Let Android's garbage collector handle bitmap cleanup
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        wallpaperBackground.setImageDrawable(null)
        drawerWallpaperBackground?.setImageDrawable(null)
        
        // Just clear reference - don't recycle during cleanup either
        // The activity is being destroyed, so bitmaps will be GC'd naturally
        currentWallpaperBitmap = null
        lastWallpaperId = -1
    }
}
