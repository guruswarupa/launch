package com.guruswarupa.launch.managers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.R

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
    private var lastWallpaperId: Int = -1
    private val prefs by lazy { activity.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE) }
    
    /**
     * Set wallpaper background, with optional force reload
     */
    fun setWallpaperBackground(forceReload: Boolean = false) {
        // Apply blur based on preference
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
        
        // Try to load wallpaper synchronously first (for BitmapDrawable, this is very fast)
        try {
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
        } catch (_: Exception) {
            // Synchronous wallpaper load failed, trying async
        }
        
        // For non-BitmapDrawable or if sync load failed, load in background
        backgroundExecutor.execute {
            try {
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
                    
                    val bm = createBitmap(screenWidth.takeIf { it > 0 } ?: 1080,
                        screenHeight.takeIf { it > 0 } ?: 1920)
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
            } catch (_: Exception) {
                handler.post {
                    wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                    drawerWallpaperBackground?.setImageResource(R.drawable.default_wallpaper)
                }
            }
        }
    }

    /**
     * Applies or removes blur effect based on user preference
     */
    fun applyBlurToViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurLevel = prefs.getInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, 50)
            val effect = if (blurLevel > 0) {
                val blurRadius = blurLevel.toFloat().coerceAtLeast(1f)
                RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
            } else {
                null
            }
            
            wallpaperBackground.setRenderEffect(effect)
            drawerWallpaperBackground?.setRenderEffect(effect)
            
            // Ensure right drawer wallpaper never has blur applied
            activity.findViewById<ImageView>(R.id.right_drawer_wallpaper)?.setRenderEffect(null)
        }
    }
    
    /**
     * Clear cached wallpaper bitmap
     */
    fun clearCache() {
        currentWallpaperBitmap?.recycle()
        currentWallpaperBitmap = null
        lastWallpaperId = -1
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        wallpaperBackground.setImageDrawable(null)
        drawerWallpaperBackground?.setImageDrawable(null)
        currentWallpaperBitmap?.recycle()
        currentWallpaperBitmap = null
        lastWallpaperId = -1
    }
}
