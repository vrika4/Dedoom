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
 * 1. Stories and Direct Messages (DMs) are NEVER blocked or covered.
 * 2. Allows watching up to 10 distinct full-screen reels per day.
 * 3. Hysteresis & Signature verification prevent false increments on UI redraws.
 * 4. Once 10 reels are watched today, enforces INSTANT BLOCK (GLOBAL_ACTION_BACK + Blocked Screen).
 * 5. Daily counter resets automatically at midnight.
 * 6. Dynamic Black Cover Overlay: Covers embedded reels carousels and YouTube Shorts shelves
 *    directly on the Home Feed while keeping feed scrolling 100% fluid and usable.
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
    private lateinit var coverManager: ReelCoverOverlayManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastScrollTime = 0L
    private var lastDetectionCheckTime = 0L
    private var lastCoverScanTime = 0L
    private var isCurrentlyInReels = false
    private var lastReelSignature: String? = null
    private var reelsEntryTime = 0L
    private var consecutiveNonReelsFrames = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        tracker = UsageTracker(this)
        coverManager = ReelCoverOverlayManager(this)
        Log.d(TAG, "Descroll Accessibility Service initialized")
    }

    override fun onDestroy() {
        super.onDestroy()
        coverManager.onDestroy()
        instance = null
        Log.d(TAG, "Descroll Accessibility Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (isSystemPackage(packageName)) {
            coverManager.hideAll()
            return
        }

        if (packageName !in tracker.getMonitoredPackages()) {
            coverManager.hideAll()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentForegroundPackage = packageName
                checkReelsStatus(packageName)
                checkHomeFeedCovers(packageName)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastDetectionCheckTime > 300L) {
                    lastDetectionCheckTime = now
                    checkReelsStatus(packageName)
                }
                if (now - lastCoverScanTime > 120L) {
                    lastCoverScanTime = now
                    checkHomeFeedCovers(packageName)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleReelSwipe(packageName)
                checkHomeFeedCovers(packageName)
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                checkHomeFeedCovers(packageName)
            }
        }
    }

    override fun onInterrupt() {
        coverManager.hideAll()
        Log.d(TAG, "Descroll Accessibility Service interrupted")
    }

    /**
     * Inspects active window to detect if user is in full-screen Reels or Shorts.
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
            coverManager.hideAll()
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
            coverManager.hideAll()

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
     * Inspects active window to detect embedded reels/shorts shelves on the Home Feed.
     * Places distraction-free opaque black covers over them while preserving feed scrolling.
     */
    private fun checkHomeFeedCovers(packageName: String) {
        if (!tracker.isCoverHomeReelsEnabled()) {
            coverManager.hideAll()
            return
        }

        // If user is inside the full-screen reel viewer, hide embedded covers
        if (isCurrentlyInReels) {
            coverManager.hideAll()
            return
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            coverManager.hideAll()
            return
        }

        val dm = resources.displayMetrics
        val bounds = ReelDetector.findEmbeddedReelBounds(
            rootNode,
            packageName,
            dm.widthPixels,
            dm.heightPixels
        )

        if (bounds.isNotEmpty()) {
            val title = if (packageName.contains("youtube")) "Shorts Hidden" else "Reels Hidden"
            coverManager.updateCovers(bounds, title)
        } else {
            coverManager.hideAll()
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
