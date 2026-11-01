package com.descroll.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered at midnight or on device reboot to ensure
 * daily reels counters reset properly.
 */
class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.d("MidnightReset", "Midnight reset / boot receiver triggered: ${intent?.action}")

        val tracker = UsageTracker(context)
        // Accessing getReelsWatchedToday() verifies date and triggers reset if day changed
        tracker.getReelsWatchedToday()
    }
}
