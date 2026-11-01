package com.descroll.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen blocking display shown when the user's daily limit of 10 reels is reached.
 * Allows quick navigation to home or directly to DMs/Messages.
 */
class BlockedActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }

    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""

        val blockedTitle = findViewById<TextView>(R.id.blockedTitle)
        val blockedMessage = findViewById<TextView>(R.id.blockedMessage)
        val btnGoHome = findViewById<View>(R.id.btnGoHome)
        val btnOpenMessages = findViewById<TextView>(R.id.btnOpenMessages)

        blockedTitle.text = getString(R.string.blocked_title)
        blockedMessage.text = getString(R.string.blocked_daily_limit)

        btnGoHome.setOnClickListener {
            goHome()
        }

        btnOpenMessages.setOnClickListener {
            openAppSafeMode()
        }
    }

    override fun onBackPressed() {
        // Exiting the block screen safely returns to phone home screen
        goHome()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun openAppSafeMode() {
        if (targetPackage.isNotEmpty()) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
            }
        }
        finish()
    }
}
