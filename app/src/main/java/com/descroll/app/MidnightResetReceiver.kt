package com.descroll.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives BOOT_COMPLETED broadcast to ensure the accessibility
 * service restarts after device reboot.
 * 
 * Note: The actual daily counter reset is handled inside UsageTracker
 * by checking the date on every access. This receiver just ensures
 * the service is ready after boot.
 */
class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // The accessibility service will auto-restart if enabled.
            // We just need to ensure our tracker is initialized.
            val tracker = UsageTracker(context)
            // Calling any method triggers the date check + reset
            tracker.getReelsOpensToday()
        }
    }
}
