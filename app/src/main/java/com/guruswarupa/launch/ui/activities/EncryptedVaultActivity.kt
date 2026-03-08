package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
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
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.models.Constants
import java.io.File
import java.text.DecimalFormat

class EncryptedVaultActivity : VaultBaseActivity() {
    private lateinit var vaultManager: EncryptedFolderManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: VaultAdapter
    private var fileToDecrypt: File? = null
    
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { encryptAndMoveFile(it) }
    }
    
    private val createNoteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFiles()
        }
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

    private val exportVaultLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    if (vaultManager.exportVault(outputStream)) {
                        Toast.makeText(this, "Vault exported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importVaultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.use { inputStream ->
                    if (vaultManager.importVault(inputStream)) {
                        Toast.makeText(this, "Vault imported successfully. Please unlock to see files.", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    } else {
                        Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportActionBar?.hide()
        
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

        findViewById<ImageView>(R.id.settings_button).setOnClickListener {
            showVaultSettings()
        }

        findViewById<FloatingActionButton>(R.id.add_file_fab).setOnClickListener {
            showAddFileOptions()
        }

        checkVaultState()
    }

    private fun checkVaultState() {
        if (!vaultManager.isVaultSetup()) {
            showSetupDialog()
        } else if (!vaultManager.isUnlocked()) {
            showUnlockDialog()
        } else {
            loadFiles()
        }
    }

    private fun showSetupDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter New Vault Password"
        }
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Setup Vault")
            .setMessage("Set a password to encrypt your vault. Remember it carefully, it cannot be recovered.")
            .setView(input)
            .setPositiveButton("Set Password") { _, _ ->
                val password = input.text.toString()
                if (password.length >= 4) {
                    if (vaultManager.setupVault(password)) {
                        loadFiles()
                    } else {
                        Toast.makeText(this, "Setup failed", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    showSetupDialog()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setNeutralButton("Import Existng") { _, _ -> importVaultLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            .setCancelable(false)
            .show()
    }

    private fun showUnlockDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Vault Password"
        }
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Vault Locked")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val password = input.text.toString()
                if (vaultManager.unlock(password)) {
                    loadFiles()
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    showUnlockDialog()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun makeSystemBarsTransparent() {
    }

    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        val overlay = findViewById<View>(R.id.settings_overlay)
        overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.settings_overlay))

        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaperDrawable = wallpaperManager.drawable
            if (wallpaperDrawable != null) {
                wallpaperImageView.setImageDrawable(wallpaperDrawable)
            }
        } catch (_: Exception) {
            wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
        }
        applyWallpaperBlur(wallpaperImageView)
    }

    private fun applyWallpaperBlur(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val elderlyModeEnabled = prefs.getBoolean(Constants.Prefs.ELDERLY_READABILITY_MODE_ENABLED, false)
            val blurLevel = if (elderlyModeEnabled) 0 else prefs.getInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, 50)
            if (blurLevel > 0) {
                val blurRadius = blurLevel.toFloat().coerceAtLeast(1f)
                imageView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP))
            } else {
                imageView.setRenderEffect(null)
            }
        }
    }

    private fun loadFiles() {
        if (!vaultManager.isUnlocked()) {
            showUnlockDialog()
            return
        }
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
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
            
            if (mimeType.startsWith("text/") || file.name.endsWith(".txt")) {
                val intent = Intent(this, NoteEditorActivity::class.java).apply {
                    putExtra("FILE_NAME", file.name)
                }
                createNoteLauncher.launch(intent)
            } else {
                val tempFile = vaultManager.decryptToCache(file.name)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
                val actualMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, actualMimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(Intent.createChooser(intent, "Open with..."))
            }
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
    
    private fun showAddFileOptions() {
        val options = arrayOf("Add File", "Create Note")
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Add to Vault")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFile.launch(arrayOf("*/*"))
                    1 -> {
                        val intent = Intent(this, NoteEditorActivity::class.java)
                        createNoteLauncher.launch(intent)
                    }
                }
            }.show()
    }
    
    private fun showVaultSettings() {
        val options = arrayOf("Export Vault", "Import Vault", "Change Password", "Auto-Lock Settings")
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Vault Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportVaultLauncher.launch("vault_backup_${System.currentTimeMillis()}.zip")
                    1 -> importVaultLauncher.launch(arrayOf("application/zip", "*/*"))
                    2 -> showSetupDialog()
                    3 -> showAutoLockDialog()
                }
            }.show()
    }

    private fun showAutoLockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vault_settings, null)
        val timeoutSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.timeout_switch)
        val recoveryBtn = dialogView.findViewById<Button>(R.id.view_recovery_phrase_button)
        
        recoveryBtn.visibility = View.GONE // Removed recovery phrase
        
        timeoutSwitch.isChecked = prefs.getBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, false)
        
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Auto-Lock Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, timeoutSwitch.isChecked)
                    .putInt(Constants.Prefs.VAULT_TIMEOUT_DURATION, 1)
                    .apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                val extension = file.extension.lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
                when {
                    mimeType.startsWith("text/") || file.name.endsWith(".txt") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_note)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 1.0f
                    }
                    else -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_file)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 0.3f
                    }
                }
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
