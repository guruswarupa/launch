package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants

/**
 * Activity that shows prominent disclosure for app data collection.
 * Required by Google Play User Data policy for collecting installed applications information.
 */
class AppDataDisclosureActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_data_disclosure)
        
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        
        // Check if user has already consented
        if (prefs.getBoolean("app_data_consent_given", false)) {
            // Already consented, proceed to main app
            startMainActivity()
            return
        }
        
        setupViews()

        // Handle back press - user must make a choice before proceeding
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing: prominent disclosure cannot be dismissed by back button
            }
        })
    }
    
    private fun setupViews() {
        val titleText = findViewById<TextView>(R.id.disclosure_title)
        val messageText = findViewById<TextView>(R.id.disclosure_message)
        val acceptButton = findViewById<Button>(R.id.accept_button)
        val declineButton = findViewById<Button>(R.id.decline_button)
        
        titleText.text = getString(R.string.app_data_disclosure_title)
        messageText.text = getString(R.string.app_data_disclosure_message)
        
        acceptButton.text = getString(R.string.i_agree)
        declineButton.text = getString(R.string.decline)
        
        acceptButton.setOnClickListener {
            // Save consent
            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
            prefs.edit { putBoolean("app_data_consent_given", true) }
            
            // Proceed to main app
            startMainActivity()
        }
        
        declineButton.setOnClickListener {
            // User declined - close app
            finishAffinity()
        }
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
