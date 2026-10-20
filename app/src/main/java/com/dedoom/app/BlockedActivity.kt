package com.dedoom.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Shown when the user's reels/shorts session has expired or
 * when they've hit the daily limit of 10 reels opens.
 *
 * Provides options to:
 * - Go back to home screen
 * - Reopen the app for messaging instead
 */
class BlockedActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_IS_SESSION_EXPIRY = "is_session_expiry"
        const val EXTRA_IS_DAILY_LIMIT = "is_daily_limit"
    }

    private lateinit var tracker: UsageTracker
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        tracker = UsageTracker(this)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        val isSessionExpiry = intent.getBooleanExtra(EXTRA_IS_SESSION_EXPIRY, false)
        val isDailyLimit = intent.getBooleanExtra(EXTRA_IS_DAILY_LIMIT, false)

        // Update UI based on reason for blocking
        val blockedTitle = findViewById<TextView>(R.id.blockedTitle)
        val blockedMessage = findViewById<TextView>(R.id.blockedMessage)
        val btnGoHome = findViewById<LinearLayout>(R.id.btnGoHome)
        val btnOpenMessages = findViewById<TextView>(R.id.btnOpenMessages)

        if (isDailyLimit || tracker.isDailyLimitReached()) {
            blockedTitle.text = "Daily Limit Reached 🚫"
            blockedMessage.text = "You've watched your 10 reels for today.\n\nReels are blocked until tomorrow.\n\nMessages and Stories can still be accessed freely!"
        } else {
            blockedTitle.text = "Reels Blocked 🛑"
            blockedMessage.text = "You've reached your limit for today.\n\nCome back tomorrow, or open for Messages and Stories instead."
        }

        // Record the block
        tracker.incrementBlocks()
        tracker.clearGracePeriod(targetPackage)

        // Button handlers
        btnGoHome.setOnClickListener {
            goHome()
        }

        btnOpenMessages.setOnClickListener {
            // Set messages mode and grace period, then let them back in
            tracker.setSessionMode(targetPackage, "messages")
            tracker.setGracePeriod(targetPackage)

            // Relaunch the target app
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
            finish()
        }
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }
}
