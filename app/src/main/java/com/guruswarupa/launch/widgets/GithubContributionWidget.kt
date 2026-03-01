package com.guruswarupa.launch.widgets

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.widget.AdapterView
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.GithubApiService
import java.util.concurrent.Executors

class GithubContributionWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {

    private lateinit var githubApiService: GithubApiService
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false

    // UI Components
    private lateinit var githubContainer: LinearLayout
    private lateinit var githubIcon: ImageView
    private lateinit var githubUsername: TextView
    private lateinit var githubRefreshButton: ImageButton
    private lateinit var githubYearSpinner: Spinner
    private lateinit var githubTotalContributions: TextView
    private lateinit var githubCurrentStreak: TextView
    private lateinit var githubLongestStreak: TextView
    private lateinit var githubStatsContainer: LinearLayout
    private lateinit var githubGraphTitle: TextView
    private lateinit var githubContributionGraphView: com.guruswarupa.launch.ui.views.GithubContributionGraphView
    private lateinit var githubStatusText: TextView
    private lateinit var widgetView: View
    private var currentYear = java.time.LocalDate.now().year
    private var availableYears = listOf<Int>()

    companion object {
        private const val PREF_GITHUB_TOKEN = "github_token"
        private const val PREF_GITHUB_USERNAME = "github_username"
    }

    fun initialize() {
        if (isInitialized) return

        // Inflate the widget layout
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_github_contributions, container, false)
        container.addView(widgetView)

        // Initialize views
        githubContainer = widgetView.findViewById(R.id.github_container)
        githubIcon = widgetView.findViewById(R.id.github_icon)
        githubUsername = widgetView.findViewById(R.id.github_username)
        githubYearSpinner = widgetView.findViewById(R.id.github_year_spinner)
        githubRefreshButton = widgetView.findViewById(R.id.github_refresh_button)
        githubTotalContributions = widgetView.findViewById(R.id.github_total_contributions)
        githubCurrentStreak = widgetView.findViewById(R.id.github_current_streak)
        githubLongestStreak = widgetView.findViewById(R.id.github_longest_streak)
        githubStatsContainer = widgetView.findViewById(R.id.github_stats_container)
        githubGraphTitle = widgetView.findViewById(R.id.github_graph_title)
        githubContributionGraphView = widgetView.findViewById(R.id.github_contribution_graph_view)
        githubStatusText = widgetView.findViewById(R.id.github_status_text)

        // Initially hide the widget until visibility is properly set by MainActivity
        githubContainer.visibility = View.GONE

        // Initialize API service
        githubApiService = GithubApiService(context)

        // Setup year spinner
        setupYearSpinner()
        
        // Setup click listeners
        githubRefreshButton.setOnClickListener {
            val savedUsername = sharedPreferences.getString(PREF_GITHUB_USERNAME, "")
            val savedToken = sharedPreferences.getString(PREF_GITHUB_TOKEN, "")
            if (!savedToken.isNullOrEmpty() && !savedUsername.isNullOrEmpty()) {
                loadAvailableYears(savedUsername, savedToken)
            } else {
                loadGithubData()
            }
        }

        githubStatusText.setOnClickListener {
            showGithubTokenDialog()
        }

        // Load saved username and token
        val savedUsername = sharedPreferences.getString(PREF_GITHUB_USERNAME, "")
        val savedToken = sharedPreferences.getString(PREF_GITHUB_TOKEN, "")

        if (!savedToken.isNullOrEmpty() && !savedUsername.isNullOrEmpty()) {
            githubUsername.text = "GitHub: $savedUsername"
            loadAvailableYears(savedUsername, savedToken)
        } else {
            githubStatusText.text = "Tap to configure GitHub token"
            // Show default years if no token configured
            availableYears = listOf(currentYear)
            setupYearSpinner()
        }

        isInitialized = true
    }

    private fun loadAvailableYears(username: String, token: String) {
        githubStatusText.text = "Loading available years..."
        
        executor.execute {
            try {
                val years = githubApiService.getAvailableContributionYears(username, token)
                handler.post {
                    availableYears = years
                    if (availableYears.isNotEmpty()) {
                        currentYear = availableYears.first() // Set to most recent year with contributions
                    }
                    setupYearSpinner()
                    loadGithubData()
                }
            } catch (e: Exception) {
                handler.post {
                    // Fallback to current year if API fails
                    availableYears = listOf(currentYear)
                    setupYearSpinner()
                    githubStatusText.text = "Error loading years: ${e.message}"
                }
            }
        }
    }

    private fun setupYearSpinner() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, availableYears)
        // Use custom translucent dropdown item
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        githubYearSpinner.adapter = adapter
        
        // Set current year as selected
        val currentYearIndex = availableYears.indexOf(currentYear)
        if (currentYearIndex >= 0) {
            githubYearSpinner.setSelection(currentYearIndex)
        }
        
        githubYearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedYear = availableYears[position]
                if (selectedYear != currentYear) {
                    currentYear = selectedYear
                    loadGithubData()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun loadGithubData() {
        val token = sharedPreferences.getString(PREF_GITHUB_TOKEN, "")
        val username = sharedPreferences.getString(PREF_GITHUB_USERNAME, "")

        if (token.isNullOrEmpty() || username.isNullOrEmpty()) {
            showGithubTokenDialog()
            return
        }

        githubStatusText.text = "Loading $currentYear contributions..."
        githubStatsContainer.visibility = View.GONE
        githubGraphTitle.text = "Contribution Activity ($currentYear)"

        executor.execute {
            try {
                val contributionData = githubApiService.fetchContributionData(username, token, currentYear)
                
                handler.post {
                    updateContributionGraph(contributionData.contributions)
                    updateStats(contributionData.totalContributions, contributionData.currentStreak, contributionData.longestStreak)
                    githubStatusText.text = "Last updated: ${contributionData.lastUpdated}"
                    githubStatsContainer.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                handler.post {
                    githubStatusText.text = "Error: ${e.message}"
                    githubStatsContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun updateContributionGraph(contributions: Map<String, Int>) {
        // Update the custom view with the contribution data
        githubContributionGraphView.setContributions(contributions)
    }



    private fun updateStats(total: Int, currentStreak: Int, longestStreak: Int) {
        githubTotalContributions.text = total.toString()
        githubCurrentStreak.text = currentStreak.toString()
        githubLongestStreak.text = longestStreak.toString()
    }

    private fun showGithubTokenDialog() {
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_github_token, null)
        
        val tokenInput = dialogView.findViewById<EditText>(R.id.github_token_input)
        val usernameInput = dialogView.findViewById<EditText>(R.id.github_username_input)
        
        val savedToken = sharedPreferences.getString(PREF_GITHUB_TOKEN, "")
        val savedUsername = sharedPreferences.getString(PREF_GITHUB_USERNAME, "")
        
        tokenInput.setText(savedToken)
        usernameInput.setText(savedUsername)

        val builder = AlertDialog.Builder(context, R.style.CustomDialogTheme)
        builder.setTitle("GitHub Token Configuration")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val token = tokenInput.text.toString().trim()
                val username = usernameInput.text.toString().trim()
                
                if (token.isNotEmpty() && username.isNotEmpty()) {
                    sharedPreferences.edit {
                        putString(PREF_GITHUB_TOKEN, token)
                        putString(PREF_GITHUB_USERNAME, username)
                    }
                    
                    githubUsername.text = "GitHub: $username"
                    loadGithubData()
                } else {
                    Toast.makeText(context, "Please enter both token and username", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun onResume() {
        if (isInitialized) {
            val savedToken = sharedPreferences.getString(PREF_GITHUB_TOKEN, "")
            val savedUsername = sharedPreferences.getString(PREF_GITHUB_USERNAME, "")
            
            if (!savedToken.isNullOrEmpty() && !savedUsername.isNullOrEmpty()) {
                loadGithubData()
            }
        }
    }

    fun onPause() {
        // Nothing to do here for now
    }

    fun cleanup() {
        executor.shutdown()
    }
    
    fun setGlobalVisibility(visible: Boolean) {
        if (isInitialized) {
            githubContainer.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}