package com.descroll.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe usage tracker managing daily reel counts, block statistics,
 * and user preferences for monitored applications and home feed overlays.
 * Automatically resets counts at midnight.
 */
class UsageTracker(context: Context) {

    companion object {
        private const val PREFS_NAME = "descroll_prefs"

        // Tracking keys
        private const val KEY_REELS_WATCHED_TODAY = "reels_watched_today"
        private const val KEY_BLOCKS_TODAY = "blocks_today"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"

        // Monitored app toggles
        private const val KEY_MONITOR_INSTAGRAM = "monitor_instagram"
        private const val KEY_MONITOR_YOUTUBE = "monitor_youtube"
        private const val KEY_MONITOR_TIKTOK = "monitor_tiktok"
        private const val KEY_MONITOR_FACEBOOK = "monitor_facebook"

        // Home Feed Reels Cover Toggle (ScrollGuard black cover feature)
        private const val KEY_COVER_HOME_REELS = "cover_home_reels"

        // Limit configuration
        private const val KEY_MAX_REELS_PER_DAY = "max_reels_per_day"
        const val DEFAULT_MAX_REELS_PER_DAY = 10

        // Packages
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_YOUTUBE_KIDS = "com.google.android.apps.youtube.kids"
        const val PKG_YOUTUBE_REVANCED = "app.revanced.android.youtube"
        const val PKG_TIKTOK = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_TRILL = "com.ss.android.ugc.trill"
        const val PKG_TIKTOK_AWEME = "com.ss.android.ugc.aweme"
        const val PKG_FACEBOOK = "com.facebook.katana"
        const val PKG_FACEBOOK_LITE = "com.facebook.lite"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @Synchronized
    private fun checkAndResetIfNewDay() {
        val today = getTodayDateString()
        val lastReset = prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""
        if (lastReset != today) {
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putInt(KEY_REELS_WATCHED_TODAY, 0)
                .putInt(KEY_BLOCKS_TODAY, 0)
                .apply()
        }
    }

    // ─── Daily Reels Counting ────────────────────────────────────────

    @Synchronized
    fun getReelsWatchedToday(): Int {
        checkAndResetIfNewDay()
        return prefs.getInt(KEY_REELS_WATCHED_TODAY, 0)
    }

    @Synchronized
    fun incrementReelWatched(): Int {
        checkAndResetIfNewDay()
        val current = getReelsWatchedToday() + 1
        prefs.edit().putInt(KEY_REELS_WATCHED_TODAY, current).apply()
        return current
    }

    fun getMaxReelsPerDay(): Int {
        return prefs.getInt(KEY_MAX_REELS_PER_DAY, DEFAULT_MAX_REELS_PER_DAY)
    }

    fun setMaxReelsPerDay(max: Int) {
        prefs.edit().putInt(KEY_MAX_REELS_PER_DAY, max.coerceAtLeast(1)).apply()
    }

    fun isDailyLimitReached(): Boolean {
        return getReelsWatchedToday() >= getMaxReelsPerDay()
    }

    fun getRemainingReelsToday(): Int {
        return (getMaxReelsPerDay() - getReelsWatchedToday()).coerceAtLeast(0)
    }

    // ─── Block Events ───────────────────────────────────────────────

    @Synchronized
    fun getBlocksToday(): Int {
        checkAndResetIfNewDay()
        return prefs.getInt(KEY_BLOCKS_TODAY, 0)
    }

    @Synchronized
    fun incrementBlocks(): Int {
        checkAndResetIfNewDay()
        val current = getBlocksToday() + 1
        prefs.edit().putInt(KEY_BLOCKS_TODAY, current).apply()
        return current
    }

    @Synchronized
    fun resetTodayCounters() {
        prefs.edit()
            .putString(KEY_LAST_RESET_DATE, getTodayDateString())
            .putInt(KEY_REELS_WATCHED_TODAY, 0)
            .putInt(KEY_BLOCKS_TODAY, 0)
            .apply()
    }

    // ─── Home Feed Covers Feature (ScrollGuard) ─────────────────────

    fun isCoverHomeReelsEnabled(): Boolean = prefs.getBoolean(KEY_COVER_HOME_REELS, true)
    fun setCoverHomeReelsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_COVER_HOME_REELS, enabled).apply()

    // ─── Monitored Apps ─────────────────────────────────────────────

    fun isInstagramMonitored(): Boolean = prefs.getBoolean(KEY_MONITOR_INSTAGRAM, true)
    fun setInstagramMonitored(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_MONITOR_INSTAGRAM, enabled).apply()

    fun isYoutubeMonitored(): Boolean = prefs.getBoolean(KEY_MONITOR_YOUTUBE, true)
    fun setYoutubeMonitored(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_MONITOR_YOUTUBE, enabled).apply()

    fun isTiktokMonitored(): Boolean = prefs.getBoolean(KEY_MONITOR_TIKTOK, true)
    fun setTiktokMonitored(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_MONITOR_TIKTOK, enabled).apply()

    fun isFacebookMonitored(): Boolean = prefs.getBoolean(KEY_MONITOR_FACEBOOK, false)
    fun setFacebookMonitored(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_MONITOR_FACEBOOK, enabled).apply()

    fun getMonitoredPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        if (isInstagramMonitored()) {
            packages.add(PKG_INSTAGRAM)
        }
        if (isYoutubeMonitored()) {
            packages.add(PKG_YOUTUBE)
            packages.add(PKG_YOUTUBE_KIDS)
            packages.add(PKG_YOUTUBE_REVANCED)
        }
        if (isTiktokMonitored()) {
            packages.add(PKG_TIKTOK)
            packages.add(PKG_TIKTOK_TRILL)
            packages.add(PKG_TIKTOK_AWEME)
        }
        if (isFacebookMonitored()) {
            packages.add(PKG_FACEBOOK)
            packages.add(PKG_FACEBOOK_LITE)
        }
        return packages
    }

    fun getAppDisplayName(packageName: String): String {
        return when {
            packageName == PKG_INSTAGRAM -> "Instagram"
            packageName.contains("youtube") -> "YouTube Shorts"
            packageName.contains("musically") || packageName.contains("ugc") -> "TikTok"
            packageName.contains("facebook") -> "Facebook Reels"
            else -> "Short Video"
        }
    }
}
