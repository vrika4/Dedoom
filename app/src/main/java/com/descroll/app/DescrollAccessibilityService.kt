package com.descroll.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Core Accessibility Service operating on the reverse-engineered ScrollGuard engine.
 *
 * Operational Rules:
 * 1. Stories and Direct Messages (DMs) are NEVER blocked.
 * 2. Allows watching up to 10 distinct reels per day.
 * 3. Hysteresis & Signature verification prevent false increments on UI redraws.
 * 4. Once 10 reels are watched today, enforces INSTANT BLOCK (GLOBAL_ACTION_BACK + Blocked Screen).
 * 5. Daily counter resets automatically at midnight.
 */
class DescrollAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DescrollService"

        var currentForegroundPackage: String? = null
            private set

        var instance: DescrollAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private lateinit var tracker: UsageTracker
    private val handler = Handler(Looper.getMainLooper())

    private var lastScrollTime = 0L
    private var lastDetectionCheckTime = 0L
    private var isCurrentlyInReels = false
    private var lastReelSignature: String? = null
    private var reelsEntryTime = 0L
    private var consecutiveNonReelsFrames = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        tracker = UsageTracker(this)
        Log.d(TAG, "Descroll Accessibility Service initialized")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Descroll Accessibility Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (isSystemPackage(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentForegroundPackage = packageName
                if (packageName in tracker.getMonitoredPackages()) {
                    checkReelsStatus(packageName)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (packageName in tracker.getMonitoredPackages()) {
                    val now = System.currentTimeMillis()
                    if (now - lastDetectionCheckTime > 400L) {
                        lastDetectionCheckTime = now
                        checkReelsStatus(packageName)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (packageName in tracker.getMonitoredPackages()) {
                    handleReelSwipe(packageName)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Descroll Accessibility Service interrupted")
    }

    /**
     * Inspects active window to detect if user is in Reels or Shorts.
     */
    private fun checkReelsStatus(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReels = ReelDetector.isViewingReelsOrShorts(rootNode, packageName)

        if (!isReels) {
            // Hysteresis: Require 3 consecutive non-reels frames before marking as exited
            consecutiveNonReelsFrames++
            if (consecutiveNonReelsFrames >= 3) {
                isCurrentlyInReels = false
                lastReelSignature = null
            }
            return
        }

        consecutiveNonReelsFrames = 0
        val now = System.currentTimeMillis()

        // 1. If daily limit of 10 reels is reached: INSTANT BLOCK
        if (tracker.isDailyLimitReached()) {
            Log.d(TAG, "Daily limit reached (${tracker.getMaxReelsPerDay()} reels)! Instant blocking...")
            isCurrentlyInReels = false
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchBlockedScreen(packageName)
            return
        }

        val currentSignature = ReelDetector.getReelSignature(rootNode, packageName)

        // 2. Entering reels for the first time in this session
        if (!isCurrentlyInReels) {
            isCurrentlyInReels = true
            reelsEntryTime = now
            lastReelSignature = currentSignature

            val count = tracker.incrementReelWatched()
            Log.d(TAG, "Watching Reel #$count / ${tracker.getMaxReelsPerDay()}")

            if (count >= tracker.getMaxReelsPerDay()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchBlockedScreen(packageName)
            } else {
                showToast("Reel $count of ${tracker.getMaxReelsPerDay()} today")
            }
        } else {
            // 3. User is actively viewing reels. If signature changed and user stayed on reel >= 3.0s, count next reel
            if (currentSignature != null && currentSignature != lastReelSignature && (now - reelsEntryTime >= 3000L)) {
                lastReelSignature = currentSignature
                reelsEntryTime = now

                val count = tracker.incrementReelWatched()
                Log.d(TAG, "Advanced to Reel #$count / ${tracker.getMaxReelsPerDay()}")

                if (count >= tracker.getMaxReelsPerDay()) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    launchBlockedScreen(packageName)
                } else {
                    showToast("Reel $count of ${tracker.getMaxReelsPerDay()} today")
                }
            }
        }
    }

    /**
     * Handles scrolling/swiping between reels with debouncing.
     */
    private fun handleReelSwipe(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        if (!ReelDetector.isViewingReelsOrShorts(rootNode, packageName)) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastScrollTime > 3000L) {
            lastScrollTime = now
            checkReelsStatus(packageName)
        }
    }

    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchBlockedScreen(packageName: String) {
        tracker.incrementBlocks()

        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_TARGET_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("com.samsung.") ||
                packageName.startsWith("com.sec.") ||
                packageName == "com.descroll.app" ||
                packageName == this.packageName ||
                packageName == "android" ||
                packageName.contains("launcher") ||
                packageName.contains("systemui") ||
                packageName.contains("keyboard") ||
                packageName.contains("inputmethod")
    }
}
