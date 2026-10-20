package com.dedoom.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Dedoom Core Accessibility Service.
 *
 * Rules:
 * 1. Stories and Messages are NEVER blocked.
 * 2. Counts reels up to 10 per day.
 * 3. Once 10 reels are watched today, blocks reels completely until tomorrow.
 * 4. Resets only on the next day (midnight).
 */
class DedoomAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DedoomService"

        var currentForegroundPackage: String? = null
            private set

        var instance: DedoomAccessibilityService? = null
            private set
    }

    private lateinit var tracker: UsageTracker
    private val handler = Handler(Looper.getMainLooper())
    private var lastScrollTime = 0L
    private var lastDetectionCheckTime = 0L
    private var isCurrentlyInReels = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        tracker = UsageTracker(this)
        Log.d(TAG, "Dedoom Accessibility Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Dedoom Accessibility Service destroyed")
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
        Log.d(TAG, "Dedoom Accessibility Service interrupted")
    }

    /**
     * Inspects active window to detect if user is in Reels or Shorts.
     */
    private fun checkReelsStatus(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReels = ReelDetector.isViewingReelsOrShorts(rootNode, packageName)

        if (!isReels) {
            // User is in Messages, Stories, Home feed, or Profile -> NEVER BLOCK
            isCurrentlyInReels = false
            return
        }

        // 1. If daily limit of 10 reels is reached: BLOCK IMMEDIATELY
        if (tracker.isDailyLimitReached()) {
            Log.d(TAG, "Daily limit of 10 reels reached! Blocking reels...")
            isCurrentlyInReels = false
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchBlockedScreen(packageName)
            return
        }

        // 2. If entering reels for the first time in this session, count reel #1
        if (!isCurrentlyInReels) {
            isCurrentlyInReels = true
            val count = tracker.incrementReelWatched()
            Log.d(TAG, "Started watching Reel #$count / ${tracker.getMaxReelsPerDay()}")

            if (count >= tracker.getMaxReelsPerDay()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchBlockedScreen(packageName)
            } else {
                showToast("Reel $count of ${tracker.getMaxReelsPerDay()} today")
            }
        }
    }

    /**
     * Handles scrolling/swiping between reels.
     */
    private fun handleReelSwipe(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        if (!ReelDetector.isViewingReelsOrShorts(rootNode, packageName)) {
            isCurrentlyInReels = false
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastScrollTime > 2000L) { // Debounce swipe gesture: at least 2s between swipes
            lastScrollTime = now
            val count = tracker.incrementReelWatched()
            Log.d(TAG, "Swiped to Reel #$count / ${tracker.getMaxReelsPerDay()}")

            if (count >= tracker.getMaxReelsPerDay()) {
                Log.d(TAG, "10 Reels limit reached! Blocking...")
                isCurrentlyInReels = false
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchBlockedScreen(packageName)
            } else {
                showToast("Reel $count of ${tracker.getMaxReelsPerDay()} today")
            }
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
            putExtra(BlockedActivity.EXTRA_IS_DAILY_LIMIT, true)
        }
        startActivity(intent)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("com.samsung.") ||
                packageName.startsWith("com.sec.") ||
                packageName == "com.dedoom.app" ||
                packageName == this.packageName ||
                packageName == "android" ||
                packageName.contains("launcher") ||
                packageName.contains("systemui") ||
                packageName.contains("keyboard") ||
                packageName.contains("inputmethod")
    }
}
