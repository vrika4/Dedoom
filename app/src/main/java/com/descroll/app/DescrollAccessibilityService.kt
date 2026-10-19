package com.descroll.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Core accessibility service that monitors app launches.
 * When a monitored app (Instagram/YouTube) comes to the foreground,
 * it launches the OverlayActivity with the 15-sec delay.
 *
 * Also handles reels timer expiry — if the user is still in a
 * reels session when the timer runs out, launches BlockedActivity.
 */
class DescrollAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DescrollService"

        // Track current foreground app
        var currentForegroundPackage: String? = null
            private set

        // Singleton reference for checking service state
        var instance: DescrollAccessibilityService? = null
            private set
    }

    private lateinit var tracker: UsageTracker
    private val handler = Handler(Looper.getMainLooper())
    private val timerCheckRunnable = object : Runnable {
        override fun run() {
            checkReelsTimers()
            handler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        tracker = UsageTracker(this)
        Log.d(TAG, "Descroll Accessibility Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Descroll Accessibility Service connected")
        // Start periodic timer checks
        handler.post(timerCheckRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacks(timerCheckRunnable)
        Log.d(TAG, "Descroll Accessibility Service destroyed")
    }

    private var lastScrollTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (isSystemPackage(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val previousPackage = currentForegroundPackage
                currentForegroundPackage = packageName

                // If user left a monitored app, clear its grace period
                if (previousPackage != null &&
                    previousPackage != packageName &&
                    previousPackage in tracker.getMonitoredPackages()
                ) {
                    // Only clear if the user actually left (not just switching to our overlay)
                    if (packageName != "com.descroll.app") {
                        Log.d(TAG, "User left $previousPackage → clearing grace period")
                        tracker.clearGracePeriod(previousPackage)
                    }
                }

                // Check if this is a monitored app
                if (packageName in tracker.getMonitoredPackages()) {
                    handleMonitoredAppOpened(packageName)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // If user is actively in reels mode, track swipes
                if (packageName in tracker.getMonitoredPackages() &&
                    tracker.getSessionMode(packageName) == "reels"
                ) {
                    val now = System.currentTimeMillis()
                    // Debounce rapid scroll events (min 1.2s between distinct reels)
                    if (now - lastScrollTime > 1200L) {
                        lastScrollTime = now
                        val currentSwipes = tracker.incrementReelsSwipes(packageName)
                        val maxSwipes = tracker.getMaxReelsPerSession()
                        Log.d(TAG, "Reel swipe detected: $currentSwipes / $maxSwipes")

                        if (currentSwipes >= maxSwipes) {
                            Log.d(TAG, "Hit 5 reels limit in session! Blocking...")
                            launchBlockedScreen(packageName, isSessionExpiry = true)
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Descroll Accessibility Service interrupted")
    }

    /**
     * Called when a monitored app comes to the foreground.
     */
    private fun handleMonitoredAppOpened(packageName: String) {
        // Check if user is already in an active session (grace period)
        if (tracker.isInGracePeriod(packageName)) {
            Log.d(TAG, "$packageName has active grace period — skipping overlay")
            return
        }

        // Check if reels timer just expired (should block, not show choice)
        val mode = tracker.getSessionMode(packageName)
        if (mode == "reels" && tracker.isReelsTimerExpired(packageName)) {
            Log.d(TAG, "$packageName reels timer expired — blocking!")
            launchBlockedScreen(packageName, isSessionExpiry = true)
            return
        }

        Log.d(TAG, "Monitored app opened: $packageName → showing overlay")
        launchOverlay(packageName)
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
    private fun launchBlockedScreen(packageName: String, isSessionExpiry: Boolean = false) {
        tracker.incrementBlocks()
        tracker.clearGracePeriod(packageName)

        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(BlockedActivity.EXTRA_IS_SESSION_EXPIRY, isSessionExpiry)
            putExtra(BlockedActivity.EXTRA_IS_DAILY_LIMIT, !tracker.canOpenReels())
        }
        startActivity(intent)
    }

    /**
     * Periodically checks if any reels timer has expired while user
     * is still in the monitored app.
     */
    private fun checkReelsTimers() {
        val current = currentForegroundPackage ?: return

        if (current !in tracker.getMonitoredPackages()) return

        val mode = tracker.getSessionMode(current)
        if (mode == "reels" && tracker.isReelsTimerExpired(current)) {
            Log.d(TAG, "Reels timer expired for $current while still in app!")
            launchBlockedScreen(current, isSessionExpiry = true)
        }
    }

    /**
     * Filter out system packages we don't want to track.
     */
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
