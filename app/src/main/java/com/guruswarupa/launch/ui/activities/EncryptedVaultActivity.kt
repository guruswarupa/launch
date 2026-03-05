package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.managers.RecoveryKeyManager
import com.guruswarupa.launch.models.Constants
import java.io.File
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.Executor

class EncryptedVaultActivity : VaultBaseActivity() {
    private lateinit var vaultManager: EncryptedFolderManager
    private lateinit var recoveryKeyManager: RecoveryKeyManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var adapter: VaultAdapter
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var fileToDecrypt: File? = null
    
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            this,
            "vault_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { encryptAndMoveFile(it) }
    }
    
    private val createNoteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFiles() // Refresh the file list after note is created
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
        recoveryKeyManager = RecoveryKeyManager(this)
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
            showVaultSetupDialog()
        } else if (prefs.getBoolean(Constants.Prefs.VAULT_2FA_ENABLED, false)) {
            show2FAPasswordDialog()
        } else {
            loadFiles()
        }
    }

    private fun showVaultSetupDialog() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Vault Setup")
            .setMessage("Would you like to add a password and recovery phrase for your vault?")
            .setPositiveButton("Setup") { _, _ -> showSetPasswordDialog() }
            .setNegativeButton("Skip") { _, _ ->
                prefs.edit()
                    .putBoolean(Constants.Prefs.VAULT_2FA_ENABLED, false)
                    .putBoolean(Constants.Prefs.VAULT_SETUP_COMPLETE, true)
                    .apply()
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
                    generateAndShowRecoveryPhrase()
                } else {
                    Toast.makeText(this, "Password too short", Toast.LENGTH_SHORT).show()
                    showSetPasswordDialog()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun generateAndShowRecoveryPhrase() {
        val phrase = recoveryKeyManager.generateRecoveryPhrase()
        val phraseString = phrase.joinToString(" ")
        
        // Store hash in normal prefs for verification
        prefs.edit()
            .putString(Constants.Prefs.VAULT_RECOVERY_PHRASE_HASH, recoveryKeyManager.hashPhrase(phrase))
            .putBoolean(Constants.Prefs.VAULT_SETUP_COMPLETE, true)
            .apply()
            
        // Store actual phrase in secure prefs for viewing later
        securePrefs.edit()
            .putString("vault_recovery_phrase", phraseString)
            .apply()

        showRecoveryPhraseDisplayDialog(phraseString)
    }
    
    private fun showRecoveryPhraseDisplayDialog(phrase: String) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Your Recovery Phrase")
            .setMessage("SAVE THIS CAREFULLY! If you forget your password, this is the ONLY way to recover your data:\n\n$phrase")
            .setNeutralButton("Copy to Clipboard") { _, _ ->
                copyToClipboard(phrase)
                showRecoveryPhraseDisplayDialog(phrase) // Re-show after copy
            }
            .setPositiveButton("I have saved it") { _, _ ->
                loadFiles()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Vault Recovery Phrase", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun show2FAPasswordDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }
        
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Vault Password"
        }
        layout.addView(input)
        
        val recoveryButton = Button(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Forgot Password? Use Recovery Key"
            setTextColor(ContextCompat.getColor(this@EncryptedVaultActivity, R.color.nord7))
            setOnClickListener {
                showRecoveryKeyDialog()
            }
        }
        layout.addView(recoveryButton)

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Second Factor Required")
            .setView(layout)
            .setPositiveButton("Unlock", null) // Set null to override behavior
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .create()

        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredHash = hashPassword(input.text.toString())
            val storedHash = prefs.getString(Constants.Prefs.VAULT_PASSWORD_HASH, "")
            if (enteredHash == storedHash) {
                dialog.dismiss()
                loadFiles()
            } else {
                Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRecoveryKeyDialog() {
        val input = EditText(this).apply {
            hint = "Enter 20-word recovery phrase"
        }
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Recover Vault")
            .setMessage("Enter your 20-word recovery phrase to reset your password.")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val enteredPhrase = input.text.toString()
                val storedHash = prefs.getString(Constants.Prefs.VAULT_RECOVERY_PHRASE_HASH, "")
                if (storedHash != null && recoveryKeyManager.verifyPhrase(enteredPhrase, storedHash)) {
                    Toast.makeText(this, "Phrase verified! Please set a new password.", Toast.LENGTH_LONG).show()
                    showSetPasswordDialog()
                } else {
                    Toast.makeText(this, "Invalid Recovery Phrase", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
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
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
            
            // Handle text files (notes) specially by opening the note editor
            if (mimeType.startsWith("text/") || file.name.endsWith(".txt")) {
                // Launch note editor in edit mode
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
                        // Launch note editor for creating a new note
                        val intent = Intent(this, NoteEditorActivity::class.java)
                        createNoteLauncher.launch(intent)
                    }
                }
            }.show()
    }
    
    private fun showVaultSettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vault_settings, null)
        val timeoutSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.timeout_switch)
        val recoveryBtn = dialogView.findViewById<Button>(R.id.view_recovery_phrase_button)
        
        // Load current settings
        timeoutSwitch.isChecked = prefs.getBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, false)
        
        recoveryBtn.setOnClickListener {
            // Confirm password before showing recovery phrase
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "Enter Vault Password"
            }
            AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Security Check")
                .setMessage("Please enter your vault password to view recovery phrase.")
                .setView(input)
                .setPositiveButton("Verify") { _, _ ->
                    val enteredHash = hashPassword(input.text.toString())
                    val storedHash = prefs.getString(Constants.Prefs.VAULT_PASSWORD_HASH, "")
                    if (enteredHash == storedHash) {
                        val phrase = securePrefs.getString("vault_recovery_phrase", null)
                        if (phrase != null) {
                            showRecoveryPhraseDisplayDialog(phrase)
                        } else {
                            Toast.makeText(this, "Recovery phrase not found. It might not have been stored during setup.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Vault Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, timeoutSwitch.isChecked)
                    .putInt(Constants.Prefs.VAULT_TIMEOUT_DURATION, 1)
                    .apply()
                
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
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
                // Set a default icon based on file type
                val extension = file.extension.lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
                
                when {
                    mimeType.startsWith("text/") || file.name.endsWith(".txt") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_note)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 1.0f
                    }
                    mimeType.contains("pdf") || file.name.endsWith(".pdf") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_pdf)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 1.0f
                    }
                    mimeType.startsWith("audio/") || listOf("mp3", "wav", "aac", "flac", "ogg").any { file.name.endsWith(".$it") } -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_audio)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 1.0f
                    }
                    mimeType.startsWith("image/") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_image)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 0.3f
                    }
                    mimeType.startsWith("video/") -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_video)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 0.3f
                    }
                    else -> {
                        holder.thumbnail.setImageResource(R.drawable.ic_file)
                        holder.thumbnail.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.text)
                        holder.thumbnail.alpha = 0.3f
                    }
                }
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
                mimeType.startsWith("text/") || file.name.endsWith(".txt") -> {
                    holder.typeOverlay.visibility = View.VISIBLE
                    holder.typeOverlay.setImageResource(R.drawable.ic_note)
                }
                mimeType.contains("pdf") || file.name.endsWith(".pdf") -> {
                    holder.typeOverlay.visibility = View.VISIBLE
                    holder.typeOverlay.setImageResource(R.drawable.ic_pdf)
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
