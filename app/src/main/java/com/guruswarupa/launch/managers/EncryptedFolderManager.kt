package com.guruswarupa.launch.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class EncryptedFolderManager(private val context: Context) {
    private val mainKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedFolder = File(context.filesDir, "encrypted_vault")
    private val thumbnailFolder = File(context.filesDir, "vault_thumbs")

    init {
        if (!encryptedFolder.exists()) encryptedFolder.mkdirs()
        if (!thumbnailFolder.exists()) thumbnailFolder.mkdirs()
    }

    fun getEncryptedFolder(): File = encryptedFolder

    fun getThumbnailFolder(): File = thumbnailFolder

    fun encryptFile(sourceUri: Uri, fileName: String) {
        val destinationFile = File(encryptedFolder, fileName)
        if (destinationFile.exists()) destinationFile.delete()

        // Generate and save thumbnail IF it's a media file
        generateThumbnail(sourceUri, fileName)

        val encryptedFile = EncryptedFile.Builder(
            context,
            destinationFile,
            mainKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                encryptedFile.openFileOutput().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun generateThumbnail(uri: Uri, fileName: String) {
        var mimeType = context.contentResolver.getType(uri)
        
        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        
        // Generate appropriate thumbnail based on file type
        var bitmap: Bitmap? = null
        try {
            // Priority 1: Modern loadThumbnail API for media files
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true) {
                    try {
                        bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                    } catch (e: Exception) {
                        // Fallback to manual processing
                    }
                }
            }

            // Priority 2: Manual Image Decoding
            if (bitmap == null && (mimeType?.startsWith("image/") == true || mimeType == null)) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(input, null, options)
                    
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        context.contentResolver.openInputStream(uri)?.use { input2 ->
                            options.inJustDecodeBounds = false
                            options.inSampleSize = calculateInSampleSize(options, 512, 512)
                            bitmap = BitmapFactory.decodeStream(input2, null, options)
                        }
                    }
                }
            }
            
            // Priority 3: Video Fallback
            if (bitmap == null && mimeType?.startsWith("video/") == true) {
                bitmap = getVideoThumbnailFallback(uri)
            }
            
            // Priority 4: Document and Audio Thumbnails
            if (bitmap == null) {
                bitmap = getDefaultThumbnailForFileType(mimeType)
            }

            bitmap?.let {
                saveThumbnail(it, fileName)
                it.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getDefaultThumbnailForFileType(mimeType: String?): Bitmap? {
        // Create a placeholder bitmap based on file type
        val config = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(512, 512, config)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw a colored background based on file type
        val backgroundColor = when {
            mimeType?.startsWith("audio/") == true -> android.graphics.Color.parseColor("#FF6200EE") // Purple
            mimeType?.contains("pdf") == true -> android.graphics.Color.parseColor("#FFF44336") // Red
            mimeType?.startsWith("text/") == true -> android.graphics.Color.parseColor("#FF2196F3") // Blue
            mimeType?.contains("document") == true || mimeType?.contains("word") == true -> android.graphics.Color.parseColor("#FF4CAF50") // Green
            mimeType?.contains("sheet") == true || mimeType?.contains("excel") == true -> android.graphics.Color.parseColor("#FF4CAF50") // Green
            mimeType?.contains("presentation") == true || mimeType?.contains("powerpoint") == true -> android.graphics.Color.parseColor("#FFFF9800") // Orange
            else -> android.graphics.Color.parseColor("#FF9E9E9E") // Gray
        }
        
        canvas.drawColor(backgroundColor)
        
        // Draw an icon based on file type
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 100f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        val iconText = when {
            mimeType?.startsWith("audio/") == true -> "🎵"
            mimeType?.contains("pdf") == true -> "📄"
            mimeType?.startsWith("text/") == true -> "📝"
            mimeType?.contains("document") == true || mimeType?.contains("word") == true -> " WORD"
            mimeType?.contains("sheet") == true || mimeType?.contains("excel") == true -> " EXCEL"
            mimeType?.contains("presentation") == true || mimeType?.contains("powerpoint") == true -> " PPT"
            else -> "📁"
        }
        
        val y = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(iconText, canvas.width / 2f, y, paint)
        
        return bitmap
    }

    private fun getVideoThumbnailFallback(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun saveThumbnail(bitmap: Bitmap, fileName: String) {
        val thumbFile = File(thumbnailFolder, "$fileName.thumb")
        val encryptedThumb = EncryptedFile.Builder(
            context,
            thumbFile,
            mainKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val stream = ByteArrayOutputStream()
        
        // Crop to square for uniform gallery look
        val width = bitmap.width
        val height = bitmap.height
        val size = if (width > height) height else width
        val x = (width - size) / 2
        val y = (height - size) / 2
        
        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        cropped.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()

        try {
            encryptedThumb.openFileOutput().use { outputStream ->
                outputStream.write(byteArray)
            }
        } finally {
            if (cropped != bitmap) cropped.recycle()
        }
    }

    fun getThumbnail(fileName: String): Bitmap? {
        val thumbFile = File(thumbnailFolder, "$fileName.thumb")
        
        // If thumb doesn't exist, we might try to generate it from the encrypted file
        // but that should be done in background. For now, just return null if missing.
        if (!thumbFile.exists()) {
            return null
        }

        return try {
            val encryptedThumb = EncryptedFile.Builder(
                context,
                thumbFile,
                mainKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedThumb.openFileInput().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getEncryptedFiles(): List<File> {
        return encryptedFolder.listFiles()?.toList() ?: emptyList()
    }

    fun deleteFile(fileName: String): Boolean {
        File(thumbnailFolder, "$fileName.thumb").delete()
        return File(encryptedFolder, fileName).delete()
    }
    
    fun getDecryptedInputStream(fileName: String): InputStream {
        val sourceFile = File(encryptedFolder, fileName)
        val encryptedFile = EncryptedFile.Builder(
            context,
            sourceFile,
            mainKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        return encryptedFile.openFileInput()
    }

    fun decryptToCache(fileName: String): File {
        val cacheDir = File(context.cacheDir, "vault_temp")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val tempFile = File(cacheDir, fileName)
        if (tempFile.exists()) tempFile.delete()

        getDecryptedInputStream(fileName).use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return tempFile
    }

    fun clearCache() {
        val cacheDir = File(context.cacheDir, "vault_temp")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
