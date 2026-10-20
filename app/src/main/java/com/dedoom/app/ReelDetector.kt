package com.dedoom.app

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Intelligent detector for Instagram Reels and YouTube Shorts.
 *
 * Strict Rules:
 * 1. Stories MUST NEVER be detected as Reels.
 *    Instagram Stories use reel_viewer, reel_feed_timeline, stories_tray, etc.
 * 2. Messages/Direct DMs MUST NEVER be detected as Reels.
 * 3. Bottom navigation bar items (e.g. tab icon with description "Reels") MUST NOT
 *    cause the home feed, search, or profile to be detected as Reels.
 * 4. Only an active, full-screen Reels/Shorts video player container is detected.
 */
object ReelDetector {

    // View IDs that strictly identify Instagram Stories (EXCLUDED)
    private val INSTAGRAM_STORY_MARKERS = listOf(
        "reel_viewer",
        "reel_feed_timeline",
        "stories_tray",
        "reel_viewer_framelayout",
        "reel_viewer_shadow",
        "reel_viewer_media_layout",
        "story_item"
    )

    // View IDs that strictly identify Instagram Messages / Direct DMs (EXCLUDED)
    private val INSTAGRAM_MESSAGE_MARKERS = listOf(
        "direct_",
        "thread_",
        "inbox_",
        "message_"
    )

    // View IDs that identify active Instagram Reels video player containers
    private val INSTAGRAM_REELS_CONTAINERS = listOf(
        "clips_viewer",
        "layout_clips_viewer",
        "clips_video_container",
        "clips_carousel_view",
        "clips_pager",
        "clips_media_item",
        "clips_swipe_refresh_container"
    )

    // View IDs that identify YouTube Shorts player containers
    private val YOUTUBE_SHORTS_CONTAINERS = listOf(
        "shorts_video_player",
        "shorts_player_fragment",
        "shorts_container",
        "reel_watch_fragment"
    )

    /**
     * Checks if the active window is viewing Reels or Shorts.
     * Returns true ONLY if the user is actively watching a Reel or Short.
     * Always returns false for Stories, Messages, Feeds, Profile, Search.
     */
    fun isViewingReelsOrShorts(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false

        return when (packageName) {
            "com.instagram.android" -> checkInstagramReels(rootNode)
            "com.google.android.youtube" -> checkYouTubeShorts(rootNode)
            else -> false
        }
    }

    private fun checkInstagramReels(root: AccessibilityNodeInfo): Boolean {
        var isStory = false
        var isMessage = false
        var isReelContainer = false
        var hasReelCreatorTag = false

        traverseNodes(root, depth = 0, maxDepth = 7) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            // 1. Stories check
            if (INSTAGRAM_STORY_MARKERS.any { viewId.contains(it) } ||
                desc.contains("story by") || desc == "stories"
            ) {
                isStory = true
            }

            // 2. Direct Messages check
            if (INSTAGRAM_MESSAGE_MARKERS.any { viewId.contains(it) }) {
                isMessage = true
            }

            // Exclude bottom navigation bar items so home feed is not flagged
            val isNavigationTab = viewId.contains("tab_bar") ||
                    viewId.contains("tab_reels") ||
                    viewId.contains("navigation_bar") ||
                    viewId.contains("bb_navigation")

            if (!isNavigationTab) {
                if (INSTAGRAM_REELS_CONTAINERS.any { viewId.contains(it) }) {
                    isReelContainer = true
                }
                if (desc.startsWith("reel by ") || desc.startsWith("audio: ")) {
                    hasReelCreatorTag = true
                }
            }

            false // Continue traversal to ensure stories/messages markers are not missed
        }

        // Stories and Messages must NEVER be blocked
        if (isStory || isMessage) {
            return false
        }

        return isReelContainer || hasReelCreatorTag
    }

    private fun checkYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        var isShortsContainer = false

        traverseNodes(root, depth = 0, maxDepth = 7) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            // Exclude bottom navigation bar "Shorts" tab button
            val isNavigationTab = viewId.contains("pivot_bar") ||
                    viewId.contains("bottom_nav") ||
                    viewId.contains("tab")

            if (!isNavigationTab) {
                if (YOUTUBE_SHORTS_CONTAINERS.any { viewId.contains(it) }) {
                    isShortsContainer = true
                }
                if (desc.startsWith("shorts player") || desc.contains("short by")) {
                    isShortsContainer = true
                }
            }

            false
        }

        return isShortsContainer
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        inspector: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (depth > maxDepth) return false
        if (inspector(node)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (traverseNodes(child, depth + 1, maxDepth, inspector)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }
}
