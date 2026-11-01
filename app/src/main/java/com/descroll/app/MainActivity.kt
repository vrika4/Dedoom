package com.descroll.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Main Dashboard Activity for Descroll / ScrollGuard.
 * Displays real-time daily reels usage (0-10), service permissions status,
 * monitored app toggles, and manual test reset.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tracker: UsageTracker

    // UI elements
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var reelsCountText: TextView
    private lateinit var reelsRemainingText: TextView
    private lateinit var reelsProgress: ProgressBar
    private lateinit var blocksCountText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var accessibilityDot: View
    private lateinit var overlayStatusText: TextView
    private lateinit var overlayDot: View
    private lateinit var batteryStatusText: TextView
    private lateinit var batteryDot: View

    // Switches
    private lateinit var switchInstagram: MaterialSwitch
    private lateinit var switchYoutube: MaterialSwitch
    private lateinit var switchTiktok: MaterialSwitch
    private lateinit var switchFacebook: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tracker = UsageTracker(this)

        bindViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun bindViews() {
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        reelsCountText = findViewById(R.id.reelsCountText)
        reelsRemainingText = findViewById(R.id.reelsRemainingText)
        reelsProgress = findViewById(R.id.reelsProgress)
        blocksCountText = findViewById(R.id.blocksCountText)

        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        accessibilityDot = findViewById(R.id.accessibilityDot)
        overlayStatusText = findViewById(R.id.overlayStatusText)
        overlayDot = findViewById(R.id.overlayDot)
        batteryStatusText = findViewById(R.id.batteryStatusText)
        batteryDot = findViewById(R.id.batteryDot)

        switchInstagram = findViewById(R.id.switchInstagram)
        switchYoutube = findViewById(R.id.switchYoutube)
        switchTiktok = findViewById(R.id.switchTiktok)
        switchFacebook = findViewById(R.id.switchFacebook)
    }

    private fun setupListeners() {
        // Accessibility Service setup button
        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            openAccessibilitySettings()
        }

        // Overlay permission setup button
        findViewById<View>(R.id.btnOverlay).setOnClickListener {
            openOverlaySettings()
        }

        // Battery optimization exemption button
        findViewById<View>(R.id.btnBattery).setOnClickListener {
            openBatterySettings()
        }

        // App monitor switches
        switchInstagram.isChecked = tracker.isInstagramMonitored()
        switchYoutube.isChecked = tracker.isYoutubeMonitored()
        switchTiktok.isChecked = tracker.isTiktokMonitored()
        switchFacebook.isChecked = tracker.isFacebookMonitored()

        switchInstagram.setOnCheckedChangeListener { _, isChecked ->
            tracker.setInstagramMonitored(isChecked)
        }
        switchYoutube.setOnCheckedChangeListener { _, isChecked ->
            tracker.setYoutubeMonitored(isChecked)
        }
        switchTiktok.setOnCheckedChangeListener { _, isChecked ->
            tracker.setTiktokMonitored(isChecked)
        }
        switchFacebook.setOnCheckedChangeListener { _, isChecked ->
            tracker.setFacebookMonitored(isChecked)
        }

        // Reset button for testing
        findViewById<View>(R.id.btnResetCounters).setOnClickListener {
            tracker.resetTodayCounters()
            refreshDashboard()
            Toast.makeText(this, "Daily counters reset to 0 / 10", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDashboard() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val batteryOptimized = isIgnoringBatteryOptimizations()

        val isFullyActive = accessibilityEnabled && overlayEnabled

        // Main status banner
        statusDot.setBackgroundColor(
            getColor(if (isFullyActive) R.color.status_active else R.color.status_warning)
        )
        statusText.text = if (isFullyActive) {
            getString(R.string.status_active)
        } else {
            getString(R.string.status_inactive)
        }

        // Accessibility status row
        accessibilityDot.setBackgroundColor(
            getColor(if (accessibilityEnabled) R.color.status_active else R.color.status_blocked)
        )
        accessibilityStatusText.text = if (accessibilityEnabled) "Enabled" else "Requires Setup"

        // Overlay status row
        overlayDot.setBackgroundColor(
            getColor(if (overlayEnabled) R.color.status_active else R.color.status_blocked)
        )
        overlayStatusText.text = if (overlayEnabled) "Granted" else "Requires Setup"

        // Battery optimization row
        batteryDot.setBackgroundColor(
            getColor(if (batteryOptimized) R.color.status_active else R.color.status_warning)
        )
        batteryStatusText.text = if (batteryOptimized) "Optimized" else "Recommended"

        // Stats
        val watched = tracker.getReelsWatchedToday()
        val max = tracker.getMaxReelsPerDay()
        val remaining = tracker.getRemainingReelsToday()
        val blocks = tracker.getBlocksToday()

        reelsCountText.text = "$watched / $max"
        reelsProgress.max = max
        reelsProgress.progress = watched

        if (watched >= max) {
            reelsRemainingText.text = "🛑 Limit reached! Reels locked until midnight."
            reelsRemainingText.setTextColor(getColor(R.color.status_blocked))
        } else {
            reelsRemainingText.text = "$remaining reels remaining today"
            reelsRemainingText.setTextColor(getColor(R.color.cyan_accent))
        }

        blocksCountText.text = blocks.toString()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val myService = "${packageName}/${DescrollAccessibilityService::class.java.canonicalName}"
        return enabledServices.any {
            val component = "${it.resolveInfo.serviceInfo.packageName}/${it.resolveInfo.serviceInfo.name}"
            component.equals(myService, ignoreCase = true)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Overlay Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallback)
        }
    }
}
