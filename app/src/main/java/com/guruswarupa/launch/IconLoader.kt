package com.guruswarupa.launch

import android.app.Activity
import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class IconLoader(
    private val activity: MainActivity,
    private val context: Context,
    private val separatorPackage: String,
    private val specialPackageNames: Set<String>
) {
    companion object {
        const val PRIORITY_HIGH = 100
        const val PRIORITY_MEDIUM = 50
        const val PRIORITY_LOW = 10
        const val PRIORITY_BACKGROUND = 0
    }

    private val maxCacheSize = 100
    private val iconCache = object : LruCache<String, Drawable>(maxCacheSize) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            // Don't recycle bitmaps on cache eviction - they may still be in use by views
            // Bitmap recycling is handled automatically by garbage collector when drawable is no longer referenced
        }
    }
    
    private val specialAppIconCache = object : LruCache<String, Drawable>(maxCacheSize / 2) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            // Don't recycle bitmaps on cache eviction - they may still be in use by views
        }
    }
    
    private val contactPhotoCache = object : LruCache<String, Drawable>(maxCacheSize / 2) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            // Don't recycle bitmaps on cache eviction - they may still be in use by views
        }
    }
    private val pendingIconTasks = ConcurrentHashMap<String, TrackedTask>()
    // Replace single-thread executor with thread pool for parallel icon loading
    private val iconLoadExecutor = ThreadPoolExecutor(
        3, 6, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )
    private val iconPreloadExecutor = ThreadPoolExecutor(
        2, 4, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()
    )
    private val iconLoadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()

    var currentIconStyle: String = "squircle"
        private set

    var currentIconSize: Int = 40
        private set

    private class PriorityRunnable(val priority: Int, val action: Runnable) : Runnable, Comparable<PriorityRunnable> {
        override fun run() = action.run()
        override fun compareTo(other: PriorityRunnable): Int = other.priority.compareTo(this.priority)
    }

    private class TrackedTask(
        private val priorityRunnable: PriorityRunnable
    ) : Runnable, Comparable<TrackedTask>, Future<Boolean> {
        @Volatile private var isDone = false
        @Volatile private var isCancelled = false

        override fun run() {
            try {
                priorityRunnable.run()
            } finally {
                isDone = true
            }
        }

        override fun compareTo(other: TrackedTask): Int = priorityRunnable.compareTo(other.priorityRunnable)

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            if (isDone || isCancelled) return false
            isCancelled = true
            return true
        }

        override fun isCancelled(): Boolean = isCancelled
        override fun isDone(): Boolean = isDone
        override fun get(): Boolean? = null
        override fun get(timeout: Long, unit: TimeUnit): Boolean? = null
    }

    fun updateIconStyle(style: String) {
        currentIconStyle = style
        clearIconCaches()
    }

    fun updateIconSize(size: Int) {
        currentIconSize = size
        clearIconCaches()
    }

    fun clearIconCaches() {
        iconCache.evictAll()
        specialAppIconCache.evictAll()
    }

    fun clearContactPhotoCache() {
        contactPhotoCache.evictAll()
    }

    fun getCachedIcon(cacheKey: String): Drawable? = iconCache.get(cacheKey)

    fun getShapeAppearanceModel(): ShapeAppearanceModel {
        val density = context.resources.displayMetrics.density
        val builder = ShapeAppearanceModel.builder()

        when (currentIconStyle) {
            "round" -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.5f) as CornerSize)
            }
            "squircle" -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.2f) as CornerSize)
            }
            "squared" -> builder.setAllCorners(CornerFamily.ROUNDED, 4f * density)
            "teardrop" -> {
                builder.setTopLeftCorner(CornerFamily.ROUNDED, 0f)
                builder.setTopLeftCornerSize(RelativeCornerSize(0.5f) as CornerSize)
                builder.setTopRightCorner(CornerFamily.ROUNDED, 0f)
                builder.setTopRightCornerSize(RelativeCornerSize(0.5f) as CornerSize)
                builder.setBottomLeftCorner(CornerFamily.ROUNDED, 0f)
                builder.setBottomLeftCornerSize(RelativeCornerSize(0.5f) as CornerSize)
                builder.setBottomRightCorner(CornerFamily.ROUNDED, 0f)
                builder.setBottomRightCornerSize(RelativeCornerSize(0.1f) as CornerSize)
            }
            "vortex" -> {
                builder.setAllCorners(CornerFamily.CUT, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.2f) as CornerSize)
            }
            "overlay" -> builder.setAllCorners(CornerFamily.ROUNDED, 12f * density)
            else -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.5f) as CornerSize)
            }
        }
        return builder.build()
    }

    fun applyShapeAppearance(imageView: ShapeableImageView?) {
        imageView?.shapeAppearanceModel = getShapeAppearanceModel()
    }

    fun updateIconSize(imageView: ImageView?) {
        val sizeInPx = (currentIconSize * context.resources.displayMetrics.density).toInt()
        val currentParams = imageView?.layoutParams ?: return
        if (currentParams.width != sizeInPx || currentParams.height != sizeInPx) {
            currentParams.width = sizeInPx
            currentParams.height = sizeInPx
            imageView.layoutParams = currentParams
            imageView.requestLayout()
        }
    }

    fun setIconDrawable(imageView: ImageView?, drawable: Drawable?, useLegacyIconPlate: Boolean = false) {
        imageView?.setImageDrawable(drawable?.let { shapeIconDrawable(it, useLegacyIconPlate) })
    }

    fun setIconResource(imageView: ImageView?, resId: Int, useLegacyIconPlate: Boolean = false) {
        setIconDrawable(imageView, ContextCompat.getDrawable(context, resId), useLegacyIconPlate)
    }

    fun preloadIcons(apps: List<ResolveInfo>) {
        val immediateLoad = apps.take(30)
        for (app in immediateLoad) {
            submitIconLoadTask(app, PRIORITY_LOW)
        }

        val remainingApps = apps.drop(30)
        iconLoadScope.launch {
            for (batch in remainingApps.chunked(20)) {
                for (app in batch) {
                    submitIconLoadTask(app, PRIORITY_BACKGROUND)
                }
                delay(50)
            }
        }
    }

    fun preloadNextIcons(appList: List<ResolveInfo>, startPosition: Int, endPosition: Int) {
        val size = appList.size
        if (startPosition >= size) return
        val appsToPreload = try {
            ArrayList(appList.subList(startPosition, minOf(endPosition, size)))
        } catch (_: Exception) {
            return
        }
        for (app in appsToPreload) {
            if (app.activityInfo.packageName != separatorPackage) {
                submitIconLoadTask(app, PRIORITY_MEDIUM)
            }
        }
    }

    fun submitIconLoadTask(
        app: ResolveInfo,
        priority: Int,
        holder: AppAdapter.ViewHolder? = null,
        position: Int = -1,
        onIconReady: ((String, AppAdapter.ViewHolder) -> Unit)? = null
    ) {
        val packageName = app.activityInfo.packageName
        if (packageName == separatorPackage) return

        val cacheKey = "${packageName}|${app.preferredOrder}"
        val cachedIcon = iconCache.get(cacheKey)
        if (packageName in specialPackageNames || cachedIcon != null) {
            if (cachedIcon != null && holder?.appIcon != null) {
                updateHolderIcon(holder, position, cacheKey, cachedIcon, onIconReady)
            }
            return
        }

        pendingIconTasks.remove(cacheKey)?.cancel(true)
        val priorityRunnable = PriorityRunnable(priority) {
            try {
                if (iconCache.get(cacheKey) == null) {
                    var icon = app.loadIcon(activity.packageManager)
                    if (app.preferredOrder != mainUserSerial) {
                        val userHandle = userManager.getUserForSerialNumber(app.preferredOrder.toLong())
                        if (userHandle != null) {
                            icon = activity.packageManager.getUserBadgedIcon(icon, userHandle)
                        }
                    }
                    val shapedIcon = shapeIconDrawable(icon, useLegacyIconPlate = true)
                    iconCache.put(cacheKey, shapedIcon)
                }

                val readyIcon = iconCache.get(cacheKey) ?: return@PriorityRunnable
                if (holder != null) {
                    updateHolderIcon(holder, position, cacheKey, readyIcon, onIconReady)
                }
            } catch (_: Exception) {
            }
        }

        val trackedTask = TrackedTask(priorityRunnable)
        iconPreloadExecutor.execute(trackedTask)
        pendingIconTasks[cacheKey] = trackedTask
        if (pendingIconTasks.size > 100) {
            pendingIconTasks.entries.removeIf { it.value.isDone }
        }
    }

    fun loadSpecialAppIcon(
        holder: AppAdapter.ViewHolder,
        position: Int,
        cacheKey: String,
        cacheId: String,
        fallbackResId: Int,
        candidatePackages: List<String>,
        onLoaded: (() -> Unit)? = null
    ) {
        val cachedIcon = specialAppIconCache.get(cacheId)
        if (cachedIcon != null) {
            // Safety check: ensure bitmap is not recycled
            if (!(cachedIcon is BitmapDrawable && cachedIcon.bitmap.isRecycled)) {
                holder.appIcon?.setImageDrawable(cachedIcon)
                onLoaded?.invoke()
            }
            return
        }

        setIconResource(holder.appIcon, fallbackResId)
        iconLoadExecutor.execute {
            for (candidatePackage in candidatePackages) {
                try {
                    val icon = shapeIconDrawable(activity.packageManager.getApplicationIcon(candidatePackage))
                    specialAppIconCache.put(cacheId, icon)
                    (context as? Activity)?.runOnUiThread {
                        if (holder.bindingAdapterPosition == position && holder.itemView.tag == cacheKey) {
                            // Safety check before setting
                            if (!(icon is BitmapDrawable && icon.bitmap.isRecycled)) {
                                holder.appIcon?.setImageDrawable(icon)
                                onLoaded?.invoke()
                            }
                        }
                    }
                    return@execute
                } catch (_: Exception) {
                }
            }
        }
    }

    fun loadContactPhoto(
        holder: AppAdapter.ViewHolder,
        position: Int,
        cacheKey: String,
        contactName: String,
        fallbackResId: Int,
        getPhotoUriForContact: (String) -> String?,
        onLoaded: (() -> Unit)? = null
    ) {
        val cachedPhoto = contactPhotoCache.get(contactName)
        if (cachedPhoto != null) {
            // Safety check: ensure bitmap is not recycled
            if (!(cachedPhoto is BitmapDrawable && cachedPhoto.bitmap.isRecycled)) {
                setIconDrawable(holder.appIcon, cachedPhoto)
                onLoaded?.invoke()
            }
            return
        }

        setIconResource(holder.appIcon, fallbackResId)
        iconLoadExecutor.execute {
            try {
                val photoUri = getPhotoUriForContact(contactName) ?: return@execute
                val drawable = activity.contentResolver.openInputStream(photoUri.toUri())?.use { inputStream ->
                    Drawable.createFromStream(inputStream, photoUri)
                } ?: return@execute
                contactPhotoCache.put(contactName, drawable)
                (context as? Activity)?.runOnUiThread {
                    if (holder.bindingAdapterPosition == position && holder.itemView.tag == cacheKey) {
                        // Safety check before setting
                        if (!(drawable is BitmapDrawable && drawable.bitmap.isRecycled)) {
                            setIconDrawable(holder.appIcon, drawable)
                            onLoaded?.invoke()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun updateHolderIcon(
        holder: AppAdapter.ViewHolder,
        position: Int,
        cacheKey: String,
        drawable: Drawable,
        onIconReady: ((String, AppAdapter.ViewHolder) -> Unit)?
    ) {
        (context as? Activity)?.runOnUiThread {
            val currentPosition = holder.bindingAdapterPosition
            val currentTag = holder.itemView.tag
            if (currentPosition != RecyclerView.NO_POSITION && currentPosition == position && currentTag == cacheKey) {
                // Safety check: ensure drawable's bitmap is not recycled before setting
                if (drawable is BitmapDrawable && drawable.bitmap.isRecycled) {
                    return@runOnUiThread
                }
                holder.appIcon?.setImageDrawable(drawable)
                onIconReady?.invoke(cacheKey.substringBefore('|'), holder)
            }
        }
    }

    private fun shapeIconDrawable(drawable: Drawable, useLegacyIconPlate: Boolean = false): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (currentIconSize * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val path = createIconMaskPath(bounds)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        canvas.save()
        canvas.clipPath(path)
        if (drawable is AdaptiveIconDrawable) {
            val background = drawable.background?.constantState?.newDrawable(context.resources)?.mutate()
                ?: drawable.background?.mutate()
            val foreground = drawable.foreground?.constantState?.newDrawable(context.resources)?.mutate()
                ?: drawable.foreground?.mutate()
            background?.setBounds(0, 0, size, size)
            foreground?.setBounds(0, 0, size, size)
            background?.draw(canvas)
            foreground?.draw(canvas)
        } else {
            val copy = drawable.constantState?.newDrawable(context.resources)?.mutate() ?: drawable.mutate()
            if (useLegacyIconPlate) {
                canvas.drawPath(path, fillPaint)
            }
            val inset = if (useLegacyIconPlate) (size * 0.18f).roundToInt() else 0
            copy.setBounds(inset, inset, size - inset, size - inset)
            copy.draw(canvas)
        }
        canvas.restore()

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun recycleDrawableBitmap(drawable: Drawable?) {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun createIconMaskPath(bounds: RectF): Path {
        val width = bounds.width()
        val height = bounds.height()
        val minSize = minOf(width, height)
        val path = Path()

        when (currentIconStyle) {
            "round" -> path.addOval(bounds, Path.Direction.CW)
            "squircle" -> path.addRoundRect(bounds, minSize * 0.28f, minSize * 0.28f, Path.Direction.CW)
            "squared" -> path.addRoundRect(bounds, minSize * 0.08f, minSize * 0.08f, Path.Direction.CW)
            "teardrop" -> {
                path.addRoundRect(
                    bounds,
                    floatArrayOf(
                        minSize * 0.5f, minSize * 0.5f,
                        minSize * 0.5f, minSize * 0.5f,
                        minSize * 0.18f, minSize * 0.18f,
                        minSize * 0.5f, minSize * 0.5f
                    ),
                    Path.Direction.CW
                )
            }
            "vortex" -> {
                path.moveTo(bounds.left + minSize * 0.2f, bounds.top)
                path.lineTo(bounds.right - minSize * 0.2f, bounds.top)
                path.lineTo(bounds.right, bounds.top + minSize * 0.2f)
                path.lineTo(bounds.right, bounds.bottom - minSize * 0.2f)
                path.lineTo(bounds.right - minSize * 0.2f, bounds.bottom)
                path.lineTo(bounds.left + minSize * 0.2f, bounds.bottom)
                path.lineTo(bounds.left, bounds.bottom - minSize * 0.2f)
                path.lineTo(bounds.left, bounds.top + minSize * 0.2f)
                path.close()
            }
            "overlay" -> path.addRoundRect(bounds, minSize * 0.18f, minSize * 0.18f, Path.Direction.CW)
            else -> path.addOval(bounds, Path.Direction.CW)
        }

        return path
    }
}
