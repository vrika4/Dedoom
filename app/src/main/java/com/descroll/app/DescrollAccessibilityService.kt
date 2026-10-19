package com.descroll.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Core Accessibility Service operating on the ScrollGuard architecture:
 *
 * 1. ZERO interference with messaging / chats:
 *    User can open Instagram & YouTube infinite times to chat, view messages,
 *    or search without any pause or blocking.
 *
 * 2. Surgical Reels/Shorts interception:
 *    The exact moment the user taps into Instagram Reels or YouTube Shorts:
 *    - If daily limit (10 opens) reached -> immediately kicks user out (GLOBAL_ACTION_BACK)
 *      and shows Blocked screen.
 *    - If new reels session -> immediately kicks user back and launches the 15-second
 *      breathing pause screen.
 *    - If active reels session -> counts every swipe up to 5 reels.
 *    - At 5th reel (or timer expiry) -> immediately kicks user out and shows Blocked screen.
 */
class DescrollAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DescrollService"

        var currentForegroundPackage: String? = null
            private set

        var instance: DescrollAccessibilityService? = null
            private set
    }

    private lateinit var tracker: UsageTracker
    private val handler = Handler(Looper.getMainLooper())
    private var lastScrollTime = 0L
    private var lastDetectionCheckTime = 0L

    private val timerCheckRunnable = object : Runnable {
        override fun run() {
            checkReelsTimers()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        tracker = UsageTracker(this)
        Log.d(TAG, "Descroll Accessibility Service created (ScrollGuard engine)")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Descroll Accessibility Service connected")
        handler.post(timerCheckRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacks(timerCheckRunnable)
        Log.d(TAG, "Descroll Accessibility Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (isSystemPackage(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val previousPackage = currentForegroundPackage
                currentForegroundPackage = packageName

                // If user left monitored app entirely, clear session grace
                if (previousPackage != null &&
                    previousPackage != packageName &&
                    previousPackage in tracker.getMonitoredPackages()
                ) {
                    if (packageName != "com.descroll.app") {
                        Log.d(TAG, "User exited $previousPackage -> clearing session")
                        tracker.clearGracePeriod(previousPackage)
                    }
                }

                if (packageName in tracker.getMonitoredPackages()) {
                    checkReelInterception(packageName)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Live detection when navigating into Reels/Shorts tab
                if (packageName in tracker.getMonitoredPackages()) {
                    val now = System.currentTimeMillis()
                    if (now - lastDetectionCheckTime > 300L) { // Throttle inspections
                        lastDetectionCheckTime = now
                        checkReelInterception(packageName)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Track reels swipes when inside reels
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
     * Inspects active screen nodes to determine if Reels/Shorts are on screen.
     */
    private fun checkReelInterception(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReel = ReelDetector.isViewingReelsOrShorts(rootNode, packageName)

        if (!isReel) {
            // User is in Messages, Feed, Search, or Profile -> ZERO BLOCKING
            return
        }

        Log.d(TAG, "Reels detected in $packageName")

        // 1. Daily Limit Check: Already used 10 reels opens today?
        if (!tracker.canOpenReels()) {
            Log.d(TAG, "Daily reels limit reached! Kicking out of reels...")
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchBlockedScreen(packageName, isDailyLimit = true)
            return
        }

        // 2. Active Session Check: Is the user authorized in an active 5-reel session?
        val inSession = tracker.isInGracePeriod(packageName) &&
                tracker.getSessionMode(packageName) == "reels" &&
                !tracker.isReelsTimerExpired(packageName)

        if (!inSession) {
            // New Reels access! Kick back and require the 15-second pause
            Log.d(TAG, "New reels session attempt -> triggering 15s breathing delay")
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchOverlay(packageName)
        }
    }

    /**
     * Handles scroll/swipe events while viewing reels.
     */
    private fun handleReelSwipe(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        if (!ReelDetector.isViewingReelsOrShorts(rootNode, packageName)) return

        val now = System.currentTimeMillis()
        if (now - lastScrollTime > 1200L) { // Debounce swipe gesture
            lastScrollTime = now
            val currentSwipes = tracker.incrementReelsSwipes(packageName)
            val maxSwipes = tracker.getMaxReelsPerSession()
            Log.d(TAG, "Reels swiped: $currentSwipes / $maxSwipes")

            if (currentSwipes >= maxSwipes) {
                Log.d(TAG, "5 Reels limit reached! Bouncing user out...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchBlockedScreen(packageName, isSessionExpiry = true)
            }
        }
    }

    /**
     * Launches the 15-second delay overlay screen.
     */
    private fun launchOverlay(packageName: String) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(OverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    /**
     * Launches the blocked screen when reels limit is reached.
     */
    private fun launchBlockedScreen(
        packageName: String,
        isSessionExpiry: Boolean = false,
        isDailyLimit: Boolean = false
    ) {
        tracker.incrementBlocks()
        tracker.clearGracePeriod(packageName)

        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(BlockedActivity.EXTRA_IS_SESSION_EXPIRY, isSessionExpiry)
            putExtra(BlockedActivity.EXTRA_IS_DAILY_LIMIT, isDailyLimit || !tracker.canOpenReels())
        }
        startActivity(intent)
    }

    /**
     * Checks if active session timer has expired.
     */
    private fun checkReelsTimers() {
        val current = currentForegroundPackage ?: return
        if (current !in tracker.getMonitoredPackages()) return

        val mode = tracker.getSessionMode(current)
        if (mode == "reels" && tracker.isReelsTimerExpired(current)) {
            Log.d(TAG, "Session timer expired for $current!")
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchBlockedScreen(current, isSessionExpiry = true)
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("com.samsung.") ||
                packageName.startsWith("com.sec.") ||
                packageName == "com.descroll.app" ||
                packageName == "android" ||
                packageName.contains("launcher") ||
                packageName.contains("systemui") ||
                packageName.contains("keyboard") ||
                packageName.contains("inputmethod")
    }
}
