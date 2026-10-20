package com.dedoom.app

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Intelligent detector for Instagram Reels and YouTube Shorts
 * via AccessibilityNodeInfo inspection (similar to ScrollGuard / AntiScroll).
 */
object ReelDetector {

    // Instagram identifiers
    private val INSTAGRAM_REEL_KEYWORDS = listOf(
        "clips_viewer",
        "reel_viewer",
        "clips_video_container",
        "layout_clips_viewer",
        "clips_carousel_view",
        "reel_feed_timeline"
    )

    // YouTube identifiers
    private val YOUTUBE_SHORTS_KEYWORDS = listOf(
        "reel_watch_fragment",
        "reel_recycler",
        "shorts_container",
        "reel_player_fragment",
        "shorts_shelf",
        "shorts_video_player"
    )

    /**
     * Checks if the active window in Instagram or YouTube is currently showing Reels/Shorts.
     */
    fun isViewingReelsOrShorts(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false

        return when (packageName) {
            "com.instagram.android" -> checkInstagramReels(rootNode)
            "com.google.android.youtube" -> checkYouTubeShorts(rootNode)
            else -> false
        }
    }

    private fun checkInstagramReels(node: AccessibilityNodeInfo): Boolean {
        // Fast resource ID check
        for (keyword in INSTAGRAM_REEL_KEYWORDS) {
            val matching = node.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$keyword")
            if (matching.isNotEmpty()) {
                matching.forEach { it.recycle() }
                return true
            }
        }

        // Deep recursive search for clips/reels markers
        return traverseNodes(node, depth = 0, maxDepth = 6) { curr ->
            val viewId = curr.viewIdResourceName?.lowercase() ?: ""
            val desc = curr.contentDescription?.toString()?.lowercase() ?: ""

            INSTAGRAM_REEL_KEYWORDS.any { viewId.contains(it) } ||
                    desc == "reels" || desc.startsWith("reel by")
        }
    }

    private fun checkYouTubeShorts(node: AccessibilityNodeInfo): Boolean {
        // Fast resource ID check
        for (keyword in YOUTUBE_SHORTS_KEYWORDS) {
            val matching = node.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$keyword")
            if (matching.isNotEmpty()) {
                matching.forEach { it.recycle() }
                return true
            }
        }

        // Deep recursive search for shorts markers
        return traverseNodes(node, depth = 0, maxDepth = 6) { curr ->
            val viewId = curr.viewIdResourceName?.lowercase() ?: ""
            val desc = curr.contentDescription?.toString()?.lowercase() ?: ""

            YOUTUBE_SHORTS_KEYWORDS.any { viewId.contains(it) } ||
                    desc == "shorts" || desc.startsWith("shorts player")
        }
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        matcher: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (depth > maxDepth) return false
        if (matcher(node)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (traverseNodes(child, depth + 1, maxDepth, matcher)) {
                    child.recycle()
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }
}
