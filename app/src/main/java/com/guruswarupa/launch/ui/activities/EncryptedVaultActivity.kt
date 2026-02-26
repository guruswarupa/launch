package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.models.Constants
import java.io.File
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.Executor

class EncryptedVaultActivity : AppCompatActivity() {
    private lateinit var vaultManager: EncryptedFolderManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: VaultAdapter
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var fileToDecrypt: File? = null
    
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { encryptAndMoveFile(it) }
    }

    private val decryptResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                fileToDecrypt?.let { file ->
                    try {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            vaultManager.getDecryptedInputStream(file.name).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Toast.makeText(this, "File decrypted and saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Decryption failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        setContentView(R.layout.activity_encrypted_vault)
        
        makeSystemBarsTransparent()
        setupWallpaper()

        vaultManager = EncryptedFolderManager(this)
        recyclerView = findViewById(R.id.vault_recycler_view)
        emptyStateText = findViewById(R.id.empty_state_text)
        
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        
        adapter = VaultAdapter(vaultManager, emptyList(), 
            onFileClick = { file -> openFile(file) },
            onFileLongClick = { file -> showFileOptions(file) }
        )
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        findViewById<FloatingActionButton>(R.id.add_file_fab).setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }

        setupBiometric()
        authenticateUser()
    }

    private fun makeSystemBarsTransparent() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetsController = window.decorView.windowInsetsController
            if (insetsController != null) {
                val appearance = if (!isDarkMode) {
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else {
                    0
                }
                insetsController.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        }
    }

    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        val overlay = findViewById<View>(R.id.settings_overlay)
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        overlay.setBackgroundColor(if (isDarkMode) "#CC000000".toColorInt() else "#66FFFFFF".toColorInt())

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED)) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(this)
                val wallpaperDrawable = wallpaperManager.drawable
                if (wallpaperDrawable != null) {
                    wallpaperImageView.setImageDrawable(wallpaperDrawable)
                }
            } catch (_: Exception) {
                wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
            }
        } else {
            wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
        }
        applyWallpaperBlur(wallpaperImageView)
    }

    private fun applyWallpaperBlur(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurLevel = prefs.getInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, 50)
            if (blurLevel > 0) {
                val blurRadius = blurLevel.toFloat().coerceAtLeast(1f)
                imageView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP))
            } else {
                imageView.setRenderEffect(null)
            }
        }
    }

    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    handleSecondFactor()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Vault Authentication")
            .setSubtitle("Authenticate to access your encrypted files")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    private fun authenticateUser() {
        biometricPrompt.authenticate(promptInfo)
    }

    private fun handleSecondFactor() {
        if (!prefs.contains(Constants.Prefs.VAULT_2FA_ENABLED)) {
            show2FASetupDialog()
        } else if (prefs.getBoolean(Constants.Prefs.VAULT_2FA_ENABLED, false)) {
            show2FAPasswordDialog()
        } else {
            loadFiles()
        }
    }

    private fun show2FASetupDialog() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Enable 2-Factor Auth?")
            .setMessage("Would you like to add a password as a second layer of security for your vault?")
            .setPositiveButton("Enable") { _, _ -> showSetPasswordDialog() }
            .setNegativeButton("No, thanks") { _, _ ->
                prefs.edit().putBoolean(Constants.Prefs.VAULT_2FA_ENABLED, false).apply()
                loadFiles()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSetPasswordDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Vault Password"
        }
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Set Vault Password")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val password = input.text.toString()
                if (password.length >= 4) {
                    prefs.edit()
                        .putBoolean(Constants.Prefs.VAULT_2FA_ENABLED, true)
                        .putString(Constants.Prefs.VAULT_PASSWORD_HASH, hashPassword(password))
                        .apply()
                    loadFiles()
                } else {
                    Toast.makeText(this, "Password too short", Toast.LENGTH_SHORT).show()
                    showSetPasswordDialog()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun show2FAPasswordDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Vault Password"
        }
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Second Factor Required")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val enteredHash = hashPassword(input.text.toString())
                val storedHash = prefs.getString(Constants.Prefs.VAULT_PASSWORD_HASH, "")
                if (enteredHash == storedHash) {
                    loadFiles()
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun loadFiles() {
        val files = vaultManager.getEncryptedFiles()
        adapter.updateFiles(files)
        updateEmptyState(files.isEmpty())
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun encryptAndMoveFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "encrypted_file_${System.currentTimeMillis()}"
        try {
            vaultManager.encryptFile(uri, fileName)
            var deleted = false
            if (uri.scheme == "file") {
                uri.path?.let { deleted = File(it).delete() }
            } else if (uri.scheme == "content") {
                try {
                    if (DocumentsContract.isDocumentUri(this, uri)) {
                        deleted = DocumentsContract.deleteDocument(contentResolver, uri)
                    }
                    if (!deleted) deleted = contentResolver.delete(uri, null, null) > 0
                } catch (e: Exception) {}
            }
            loadFiles()
            Toast.makeText(this, if (deleted) "File moved to vault" else "Encrypted. Delete original manually.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun openFile(file: File) {
        try {
            val tempFile = vaultManager.decryptToCache(file.name)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open with..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileOptions(file: File) {
        val options = arrayOf("Open", "Decrypt & Save", "Delete")
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFile(file)
                    1 -> decryptFile(file)
                    2 -> deleteFile(file)
                }
            }.show()
    }

    private fun decryptFile(file: File) {
        fileToDecrypt = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        decryptResultLauncher.launch(intent)
    }

    private fun deleteFile(file: File) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Delete File")
            .setMessage("Delete ${file.name} from vault?")
            .setPositiveButton("Delete") { _, _ ->
                if (vaultManager.deleteFile(file.name)) {
                    loadFiles()
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        vaultManager.clearCache()
    }

    class VaultAdapter(
        private val vaultManager: EncryptedFolderManager,
        private var files: List<File>,
        private val onFileClick: (File) -> Unit,
        private val onFileLongClick: (File) -> Unit
    ) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: ImageView = view.findViewById(R.id.file_thumbnail)
            val name: TextView = view.findViewById(R.id.file_name)
            val info: TextView = view.findViewById(R.id.file_info)
            val typeOverlay: ImageView = view.findViewById(R.id.file_type_overlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_grid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.name.text = file.name
            holder.info.text = formatFileSize(file.length())
            val bitmap = vaultManager.getThumbnail(file.name)
            if (bitmap != null) {
                holder.thumbnail.setImageBitmap(bitmap)
                holder.thumbnail.imageTintList = null
                holder.thumbnail.alpha = 1.0f
            } else {
                holder.thumbnail.setImageResource(R.drawable.ic_file)
                holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                holder.thumbnail.alpha = 0.3f
            }
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
            when {
                mimeType.startsWith("video/") -> {
                    holder.typeOverlay.visibility = View.VISIBLE
                    holder.typeOverlay.setImageResource(R.drawable.ic_play)
                }
                mimeType.startsWith("audio/") -> {
                    holder.typeOverlay.visibility = View.VISIBLE
                    holder.typeOverlay.setImageResource(R.drawable.ic_mic)
                }
                else -> holder.typeOverlay.visibility = View.GONE
            }
            holder.itemView.setOnClickListener { onFileClick(file) }
            holder.itemView.setOnLongClickListener { onFileLongClick(file); true }
        }

        override fun getItemCount() = files.size
        fun updateFiles(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }
        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}
