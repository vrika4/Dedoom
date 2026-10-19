package com.descroll.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages all usage tracking state in SharedPreferences.
 * Tracks daily reels opens, session grace periods, and blocked counts.
 * All data resets at midnight automatically.
 */
class UsageTracker(context: Context) {

    companion object {
        private const val PREFS_NAME = "descroll_prefs"

        // Daily tracking
        private const val KEY_REELS_OPENS_TODAY = "reels_opens_today"
        private const val KEY_BLOCKS_TODAY = "blocks_today"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"

        // Session tracking
        private const val KEY_GRACE_PREFIX = "grace_"           // grace_<packageName> = timestamp
        private const val KEY_SESSION_MODE_PREFIX = "mode_"     // mode_<packageName> = "reels"|"messages"
        private const val KEY_REELS_TIMER_END_PREFIX = "timer_" // timer_<packageName> = end timestamp
        private const val KEY_REELS_SWIPES_PREFIX = "swipes_"   // swipes_<packageName> = count

        // Settings
        private const val KEY_DELAY_SECONDS = "delay_seconds"
        private const val KEY_MAX_REELS_OPENS = "max_reels_opens"
        private const val KEY_MAX_REELS_PER_SESSION = "max_reels_per_session"
        private const val KEY_SESSION_DURATION_MS = "session_duration_ms"
        private const val KEY_MONITOR_INSTAGRAM = "monitor_instagram"
        private const val KEY_MONITOR_YOUTUBE = "monitor_youtube"

        // Defaults
        const val DEFAULT_DELAY_SECONDS = 15
        const val DEFAULT_MAX_REELS_OPENS = 10
        const val DEFAULT_MAX_REELS_PER_SESSION = 5
        const val DEFAULT_SESSION_DURATION_MS = 180_000L  // 3 minutes fallback
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Daily Reset ──────────────────────────────────────────────

    private fun todayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun checkAndResetIfNewDay() {
        val today = todayString()
        val lastReset = prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""
        if (lastReset != today) {
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putInt(KEY_REELS_OPENS_TODAY, 0)
                .putInt(KEY_BLOCKS_TODAY, 0)
                .apply()
        }
    }

    // ─── Reels Opens ──────────────────────────────────────────────

    fun getReelsOpensToday(): Int {
        checkAndResetIfNewDay()
        return prefs.getInt(KEY_REELS_OPENS_TODAY, 0)
    }

    fun incrementReelsOpens(): Int {
        checkAndResetIfNewDay()
        val current = getReelsOpensToday() + 1
        prefs.edit().putInt(KEY_REELS_OPENS_TODAY, current).apply()
        return current
    }

    fun getMaxReelsOpens(): Int {
        return prefs.getInt(KEY_MAX_REELS_OPENS, DEFAULT_MAX_REELS_OPENS)
    }

    fun canOpenReels(): Boolean {
        return getReelsOpensToday() < getMaxReelsOpens()
    }

    fun getRemainingReelsOpens(): Int {
        return (getMaxReelsOpens() - getReelsOpensToday()).coerceAtLeast(0)
    }

    // ─── Blocks ──────────────────────────────────────────────────

    fun getBlocksToday(): Int {
        checkAndResetIfNewDay()
        return prefs.getInt(KEY_BLOCKS_TODAY, 0)
    }

    fun incrementBlocks() {
        checkAndResetIfNewDay()
        val current = getBlocksToday() + 1
        prefs.edit().putInt(KEY_BLOCKS_TODAY, current).apply()
    }

    // ─── Grace Period ─────────────────────────────────────────────

    /**
     * Sets a grace period for an app so the overlay won't trigger again
     * until the user fully leaves and re-enters the app.
     */
    fun setGracePeriod(packageName: String) {
        prefs.edit()
            .putLong(KEY_GRACE_PREFIX + packageName, System.currentTimeMillis())
            .apply()
    }

    fun clearGracePeriod(packageName: String) {
        prefs.edit()
            .remove(KEY_GRACE_PREFIX + packageName)
            .remove(KEY_SESSION_MODE_PREFIX + packageName)
            .remove(KEY_REELS_TIMER_END_PREFIX + packageName)
            .remove(KEY_REELS_SWIPES_PREFIX + packageName)
            .apply()
    }

    // ─── Reels Swipes Tracking ────────────────────────────────────

    fun getMaxReelsPerSession(): Int {
        return prefs.getInt(KEY_MAX_REELS_PER_SESSION, DEFAULT_MAX_REELS_PER_SESSION)
    }

    fun getReelsSwipes(packageName: String): Int {
        return prefs.getInt(KEY_REELS_SWIPES_PREFIX + packageName, 0)
    }

    fun incrementReelsSwipes(packageName: String): Int {
        val current = getReelsSwipes(packageName) + 1
        prefs.edit().putInt(KEY_REELS_SWIPES_PREFIX + packageName, current).apply()
        return current
    }

    fun isReelsSwipeLimitReached(packageName: String): Boolean {
        return getReelsSwipes(packageName) >= getMaxReelsPerSession()
    }

    fun resetReelsSwipes(packageName: String) {
        prefs.edit().remove(KEY_REELS_SWIPES_PREFIX + packageName).apply()
    }

    fun isInGracePeriod(packageName: String): Boolean {
        val graceTime = prefs.getLong(KEY_GRACE_PREFIX + packageName, 0L)
        if (graceTime == 0L) return false

        // Check if in reels mode and timer has expired
        val mode = getSessionMode(packageName)
        if (mode == "reels") {
            val timerEnd = prefs.getLong(KEY_REELS_TIMER_END_PREFIX + packageName, 0L)
            if (timerEnd > 0 && System.currentTimeMillis() >= timerEnd) {
                // Timer expired — no longer in grace period, should block
                return false
            }
        }

        return true
    }

    // ─── Session Mode ─────────────────────────────────────────────

    fun setSessionMode(packageName: String, mode: String) {
        prefs.edit()
            .putString(KEY_SESSION_MODE_PREFIX + packageName, mode)
            .apply()
    }

    fun getSessionMode(packageName: String): String? {
        return prefs.getString(KEY_SESSION_MODE_PREFIX + packageName, null)
    }

    // ─── Reels Timer ──────────────────────────────────────────────

    fun startReelsTimer(packageName: String) {
        val endTime = System.currentTimeMillis() + getSessionDurationMs()
        prefs.edit()
            .putLong(KEY_REELS_TIMER_END_PREFIX + packageName, endTime)
            .apply()
    }

    fun isReelsTimerExpired(packageName: String): Boolean {
        val timerEnd = prefs.getLong(KEY_REELS_TIMER_END_PREFIX + packageName, 0L)
        if (timerEnd == 0L) return false
        return System.currentTimeMillis() >= timerEnd
    }

    fun getReelsTimerRemainingMs(packageName: String): Long {
        val timerEnd = prefs.getLong(KEY_REELS_TIMER_END_PREFIX + packageName, 0L)
        if (timerEnd == 0L) return 0L
        return (timerEnd - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    // ─── Settings ─────────────────────────────────────────────────

    fun getDelaySeconds(): Int {
        return prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY_SECONDS)
    }

    fun getSessionDurationMs(): Long {
        return prefs.getLong(KEY_SESSION_DURATION_MS, DEFAULT_SESSION_DURATION_MS)
    }

    fun isInstagramMonitored(): Boolean {
        return prefs.getBoolean(KEY_MONITOR_INSTAGRAM, true)
    }

    fun setInstagramMonitored(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITOR_INSTAGRAM, enabled).apply()
    }

    fun isYoutubeMonitored(): Boolean {
        return prefs.getBoolean(KEY_MONITOR_YOUTUBE, true)
    }

    fun setYoutubeMonitored(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITOR_YOUTUBE, enabled).apply()
    }

    /**
     * Returns the set of currently monitored package names.
     */
    fun getMonitoredPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        if (isInstagramMonitored()) packages.add("com.instagram.android")
        if (isYoutubeMonitored()) packages.add("com.google.android.youtube")
        return packages
    }

    /**
     * Returns human-readable app name for a package.
     */
    fun getAppDisplayName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.google.android.youtube" -> "YouTube"
            else -> packageName
        }
    }

    /**
     * Returns whether the app has a "reels" type feed (vs shorts).
     */
    fun isReelsApp(packageName: String): Boolean {
        return packageName == "com.instagram.android"
    }
}
