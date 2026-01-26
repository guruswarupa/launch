package com.guruswarupa.launch

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * Helper class for managing wallpaper display
 */
class WallpaperManagerHelper(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val wallpaperBackground: ImageView,
    private val drawerWallpaperBackground: ImageView?,
    private val backgroundExecutor: java.util.concurrent.ExecutorService
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentWallpaperBitmap: Bitmap? = null
    
    /**
     * Set wallpaper background, with optional force reload
     */
    fun setWallpaperBackground(forceReload: Boolean = false) {
        // Check if we have a cached wallpaper bitmap
        if (!forceReload && currentWallpaperBitmap != null && !currentWallpaperBitmap!!.isRecycled) {
            wallpaperBackground.setImageBitmap(currentWallpaperBitmap)
            drawerWallpaperBackground?.setImageBitmap(currentWallpaperBitmap)
            return
        }
        
        // Try to load wallpaper synchronously first (for BitmapDrawable, this is very fast)
        try {
            val wallpaperManager = WallpaperManager.getInstance(activity)
            val drawable = wallpaperManager.drawable
            
            if (drawable != null && drawable is BitmapDrawable) {
                val sourceBitmap = drawable.bitmap
                if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                    val bitmap = sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    if (bitmap != null) {
                        val oldBitmap = currentWallpaperBitmap
                        wallpaperBackground.setImageDrawable(null)
                        drawerWallpaperBackground?.setImageDrawable(null)
                        oldBitmap?.recycle()
                        currentWallpaperBitmap = bitmap
                        wallpaperBackground.setImageBitmap(bitmap)
                        drawerWallpaperBackground?.setImageBitmap(bitmap)
                        return
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("WallpaperManagerHelper", "No permission to read wallpaper, showing default", e)
            wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
            drawerWallpaperBackground?.setImageResource(R.drawable.default_wallpaper)
            return
        } catch (e: Exception) {
            // Synchronous wallpaper load failed, trying async
        }
        
        // For non-BitmapDrawable or if sync load failed, load in background
        backgroundExecutor.execute {
            try {
                val wallpaperManager = WallpaperManager.getInstance(activity)
                val drawable = wallpaperManager.drawable
                
                if (drawable == null) {
                    handler.post {
                        wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                        drawerWallpaperBackground?.setImageResource(R.drawable.default_wallpaper)
                    }
                    return@execute
                }
                
                val bitmap = if (drawable is BitmapDrawable) {
                    val sourceBitmap = drawable.bitmap
                    if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                        sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    } else {
                        null
                    }
                } else {
                    val screenWidth = activity.resources.displayMetrics.widthPixels
                    val screenHeight = activity.resources.displayMetrics.heightPixels
                    
                    val bm = Bitmap.createBitmap(
                        screenWidth.takeIf { it > 0 } ?: 1080,
                        screenHeight.takeIf { it > 0 } ?: 1920,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bm)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bm
                }
                
                if (bitmap != null && !bitmap.isRecycled) {
                    val oldBitmap = currentWallpaperBitmap
                    currentWallpaperBitmap = bitmap
                    
                    handler.post {
                        wallpaperBackground.setImageDrawable(null)
                        drawerWallpaperBackground?.setImageDrawable(null)
                        oldBitmap?.recycle()
                        wallpaperBackground.setImageBitmap(bitmap)
                        drawerWallpaperBackground?.setImageBitmap(bitmap)
                    }
                } else {
                    handler.post {
                        wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                        drawerWallpaperBackground?.setImageResource(R.drawable.default_wallpaper)
                    }
                }
            } catch (e: SecurityException) {
                Log.w("WallpaperManagerHelper", "No permission to read wallpaper, showing default", e)
                handler.post {
                    wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                    drawerWallpaperBackground?.setImageResource(R.drawable.default_wallpaper)
                }
            } catch (e: Exception) {
                handler.post {
                    wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                    drawerWallpaperBackground?.setImageResource(R.drawable.default_wallpaper)
                }
            }
        }
    }
    
    /**
     * Clear cached wallpaper bitmap
     */
    fun clearCache() {
        currentWallpaperBitmap?.recycle()
        currentWallpaperBitmap = null
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        wallpaperBackground.setImageDrawable(null)
        drawerWallpaperBackground?.setImageDrawable(null)
        currentWallpaperBitmap?.recycle()
        currentWallpaperBitmap = null
    }
}
