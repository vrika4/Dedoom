package com.descroll.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Main dashboard activity.
 * Shows current stats, setup status, and app toggle controls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tracker: UsageTracker

    // Views
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var reelsCountText: TextView
    private lateinit var reelsProgress: ProgressBar
    private lateinit var blocksCountText: TextView
    private lateinit var accessibilityDot: View
    private lateinit var overlayDot: View
    private lateinit var switchInstagram: MaterialSwitch
    private lateinit var switchYoutube: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tracker = UsageTracker(this)

        bindViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun bindViews() {
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        reelsCountText = findViewById(R.id.reelsCountText)
        reelsProgress = findViewById(R.id.reelsProgress)
        blocksCountText = findViewById(R.id.blocksCountText)
        accessibilityDot = findViewById(R.id.accessibilityDot)
        overlayDot = findViewById(R.id.overlayDot)
        switchInstagram = findViewById(R.id.switchInstagram)
        switchYoutube = findViewById(R.id.switchYoutube)
    }

    private fun setupClickListeners() {
        // Accessibility service setup
        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            openAccessibilitySettings()
        }

        // Overlay permission setup
        findViewById<View>(R.id.btnOverlay).setOnClickListener {
            openOverlaySettings()
        }

        // App toggles
        switchInstagram.isChecked = tracker.isInstagramMonitored()
        switchYoutube.isChecked = tracker.isYoutubeMonitored()

        switchInstagram.setOnCheckedChangeListener { _, isChecked ->
            tracker.setInstagramMonitored(isChecked)
        }

        switchYoutube.setOnCheckedChangeListener { _, isChecked ->
            tracker.setYoutubeMonitored(isChecked)
        }
    }

    private fun refreshUI() {
        // Check permissions
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)

        // Status indicator
        val isActive = accessibilityEnabled && overlayEnabled
        statusDot.setBackgroundColor(
            getColor(if (isActive) R.color.status_active else R.color.status_blocked)
        )
        statusText.text = if (isActive) {
            getString(R.string.status_active)
        } else {
            getString(R.string.status_inactive)
        }

        // Setup dots
        accessibilityDot.setBackgroundColor(
            getColor(if (accessibilityEnabled) R.color.status_active else R.color.status_blocked)
        )
        overlayDot.setBackgroundColor(
            getColor(if (overlayEnabled) R.color.status_active else R.color.status_blocked)
        )

        // Stats
        val reelsOpens = tracker.getReelsOpensToday()
        val maxOpens = tracker.getMaxReelsOpens()
        reelsCountText.text = "$reelsOpens / $maxOpens"
        reelsProgress.max = maxOpens
        reelsProgress.progress = reelsOpens

        // Color the count based on usage
        val countColor = when {
            reelsOpens >= maxOpens -> R.color.status_blocked
            reelsOpens >= maxOpens * 0.7 -> R.color.status_warning
            else -> R.color.purple_primary
        }
        reelsCountText.setTextColor(getColor(countColor))

        blocksCountText.text = tracker.getBlocksToday().toString()

        // Toggle states
        switchInstagram.isChecked = tracker.isInstagramMonitored()
        switchYoutube.isChecked = tracker.isYoutubeMonitored()
    }

    /**
     * Checks if our accessibility service is currently enabled.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
