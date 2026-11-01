package com.descroll.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Manages dynamic distraction-free cover overlays placed over home feed Reels carousels
 * and YouTube Shorts shelves.
 *
 * Technical Design:
 * - Operates via WindowManager with TYPE_ACCESSIBILITY_OVERLAY.
 * - Uses FLAG_NOT_TOUCHABLE so touches pass directly to the underlying feed.
 *   This ensures 100% smooth, native vertical feed scrolling without freezing.
 * - Solid opaque black card completely blocks the auto-playing video and thumbnails.
 * - Automatically tracks, resizes, and detaches when shelves scroll off-screen or apps switch.
 */
class ReelCoverOverlayManager(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "ReelCoverOverlay"
        private const val MAX_COVERS = 4
    }

    private val windowManager: WindowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val coverPool = mutableListOf<CoverViewHolder>()

    private class CoverViewHolder(
        val view: View,
        var isAttached: Boolean = false,
        var currentRect: Rect = Rect()
    )

    /**
     * Updates active covers to match the detected on-screen bounds.
     * Hides or detaches any excess covers.
     */
    fun updateCovers(rects: List<Rect>, title: String = "Reels Hidden") {
        if (rects.isEmpty()) {
            hideAll()
            return
        }

        val targetRects = rects.take(MAX_COVERS)

        // Ensure we have enough holders in the pool
        while (coverPool.size < targetRects.size) {
            val holder = createViewHolder()
            coverPool.add(holder)
        }

        // Position and display each needed cover
        for (i in targetRects.indices) {
            val rect = targetRects[i]
            val holder = coverPool[i]
            updateCoverView(holder, rect, title)
        }

        // Hide any excess holders
        for (i in targetRects.size until coverPool.size) {
            hideCover(coverPool[i])
        }
    }

    @SuppressLint("InflateParams")
    private fun createViewHolder(): CoverViewHolder {
        val inflater = LayoutInflater.from(service)
        val view = inflater.inflate(R.layout.layout_reel_cover, null)
        return CoverViewHolder(view)
    }

    private fun updateCoverView(holder: CoverViewHolder, rect: Rect, title: String) {
        val titleView = holder.view.findViewById<TextView>(R.id.tvCoverTitle)
        titleView?.text = title

        val width = rect.width().coerceAtLeast(100)
        val height = rect.height().coerceAtLeast(100)

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            if (!holder.isAttached) {
                windowManager.addView(holder.view, params)
                holder.isAttached = true
                holder.currentRect = Rect(rect)
                holder.view.visibility = View.VISIBLE
            } else {
                if (holder.currentRect != rect) {
                    windowManager.updateViewLayout(holder.view, params)
                    holder.currentRect = Rect(rect)
                }
                if (holder.view.visibility != View.VISIBLE) {
                    holder.view.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to position cover view: ${e.message}")
        }
    }

    private fun hideCover(holder: CoverViewHolder) {
        if (holder.isAttached) {
            try {
                if (holder.view.visibility != View.GONE) {
                    holder.view.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide cover: ${e.message}")
            }
        }
    }

    /**
     * Hides all active covers immediately.
     */
    fun hideAll() {
        for (holder in coverPool) {
            hideCover(holder)
        }
    }

    /**
     * Completely removes all views from WindowManager on service termination.
     */
    fun onDestroy() {
        for (holder in coverPool) {
            if (holder.isAttached) {
                try {
                    windowManager.removeViewImmediate(holder.view)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing cover on destroy: ${e.message}")
                }
                holder.isAttached = false
            }
        }
        coverPool.clear()
    }
}
