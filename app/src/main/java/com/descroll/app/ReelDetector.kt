package com.descroll.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Intelligent detector for Instagram Reels, YouTube Shorts, TikTok, and Facebook Reels.
 * Engineered from verified View IDs and node hierarchies reverse-engineered from Scrolless & ScrollGuard.
 *
 * Core Guarantees:
 * 1. Stories MUST NEVER be blocked (exempted via reel_viewer_root, stories_tray, etc.).
 * 2. Direct Messages (DMs) MUST NEVER be blocked (exempted via reply_bar_edittext & sender credentials).
 * 3. Regular feed posts, searches, channels, and profile pages are 100% unrestricted.
 * 4. Fast, low-latency O(1) indexed view ID lookup via Android Accessibility framework.
 */
object ReelDetector {

    // Instagram: Target Reels View IDs
    private val INSTAGRAM_REELS_VIEW_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/layout_clips_viewer",
        "com.instagram.android:id/clips_video_container"
    )

    // Instagram: Stories View IDs (Exempted - Never Blocked)
    private val INSTAGRAM_STORY_VIEW_IDS = listOf(
        "com.instagram.android:id/reel_viewer_root",
        "com.instagram.android:id/reel_feed_timeline",
        "com.instagram.android:id/stories_tray",
        "com.instagram.android:id/reel_viewer_framelayout",
        "com.instagram.android:id/reel_viewer_shadow",
        "com.instagram.android:id/story_item"
    )

    // Instagram: DM Indicators (Exempted - Never Blocked)
    private val INSTAGRAM_DM_REQUIRED_IDS = listOf(
        "com.instagram.android:id/reply_bar_edittext",
        "com.instagram.android:id/sender_username_or_fullname"
    )
    private const val INSTAGRAM_SUGGESTED_REEL_TITLE = "com.instagram.android:id/suggested_title"

    // YouTube Shorts View IDs
    private val YOUTUBE_SHORTS_VIEW_IDS = listOf(
        "com.google.android.youtube:id/reel_player_page_container",
        "app.revanced.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/shorts_video_player",
        "com.google.android.youtube:id/reel_watch_fragment"
    )

    // TikTok View IDs
    private val TIKTOK_VIEW_IDS = listOf(
        "player_view",
        "simplayer_api_player_view"
    )

    /**
     * Inspects active window hierarchy to determine if the user is currently viewing short-form video.
     * Returns true ONLY if the screen is an active, immersive Reels or Shorts feed.
     */
    fun isViewingReelsOrShorts(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false

        return when {
            packageName == UsageTracker.PKG_INSTAGRAM -> checkInstagram(rootNode)
            packageName.contains("youtube") -> checkYouTube(rootNode)
            packageName.contains("musically") || packageName.contains("ugc") -> checkTikTok(rootNode, packageName)
            packageName.contains("facebook") -> checkFacebook(rootNode)
            else -> false
        }
    }

    /**
     * Extracts a unique signature (creator, caption, or description) for the reel currently playing.
     * Prevents false increments when lingering on the same video.
     */
    fun getReelSignature(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null

        return when {
            packageName == UsageTracker.PKG_INSTAGRAM -> {
                for (id in listOf(
                    "com.instagram.android:id/clips_viewer_author_name",
                    "com.instagram.android:id/row_feed_photo_profile_name",
                    "com.instagram.android:id/clips_author_name"
                )) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    val text = nodes?.firstOrNull()?.text?.toString()
                    if (!text.isNullOrBlank()) return text
                }
                findFirstMatchingDesc(rootNode, listOf("reel by ", "audio: "))
            }
            packageName.contains("youtube") -> {
                for (id in listOf(
                    "com.google.android.youtube:id/reel_channel_name",
                    "com.google.android.youtube:id/channel_name"
                )) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    val text = nodes?.firstOrNull()?.text?.toString()
                    if (!text.isNullOrBlank()) return text
                }
                findFirstMatchingDesc(rootNode, listOf("shorts player", "short by"))
            }
            packageName.contains("musically") || packageName.contains("ugc") -> {
                findFirstMatchingDesc(rootNode, listOf("@", "video by"))
            }
            else -> null
        }
    }

    private fun checkInstagram(root: AccessibilityNodeInfo): Boolean {
        // 1. Stories exemption: If viewing stories, NEVER block
        for (storyId in INSTAGRAM_STORY_VIEW_IDS) {
            if (hasVisibleNode(root, storyId)) {
                return false
            }
        }

        // 2. Direct Messages (DM) exemption:
        // A video shared inside a chat has a reply bar or sender header,
        // unlike endless algorithmic feed reels which carry "suggested_title" or have no DM reply bar.
        val hasDmReplyBar = INSTAGRAM_DM_REQUIRED_IDS.any { hasVisibleNode(root, it) }
        val hasSuggestedTitle = hasVisibleNode(root, INSTAGRAM_SUGGESTED_REEL_TITLE)
        if (hasDmReplyBar && !hasSuggestedTitle) {
            return false
        }

        // 3. Reels detection: Check for dedicated clips viewer view pager
        for (reelsId in INSTAGRAM_REELS_VIEW_IDS) {
            if (hasVisibleNode(root, reelsId)) {
                return true
            }
        }

        return false
    }

    private fun checkYouTube(root: AccessibilityNodeInfo): Boolean {
        for (shortsId in YOUTUBE_SHORTS_VIEW_IDS) {
            if (hasVisibleNode(root, shortsId)) {
                return true
            }
        }
        return false
    }

    private fun checkTikTok(root: AccessibilityNodeInfo, packageName: String): Boolean {
        for (id in TIKTOK_VIEW_IDS) {
            if (hasVisibleNode(root, "$packageName:id/$id")) {
                return true
            }
        }
        return false
    }

    private fun checkFacebook(root: AccessibilityNodeInfo): Boolean {
        // Check for Facebook Shorts composer labels or selected Reels tab
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var depth = 0

        while (queue.isNotEmpty() && depth < 40) {
            val node = queue.removeFirst()
            depth++

            val desc = node.contentDescription?.toString() ?: ""
            if (desc.startsWith("FbShortsComposerAttachment") || (desc.startsWith("Reels,") && node.isSelected)) {
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /**
     * Efficiently checks if a node with the given viewId exists, is visible to the user,
     * and has non-zero screen bounds.
     */
    private fun hasVisibleNode(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        val rect = Rect()
        for (node in nodes) {
            if (node.isVisibleToUser) {
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    return true
                }
            }
        }
        return false
    }

    private fun findFirstMatchingDesc(root: AccessibilityNodeInfo, prefixes: List<String>): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var depth = 0

        while (queue.isNotEmpty() && depth < 60) {
            val node = queue.removeFirst()
            depth++

            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (prefixes.any { desc.startsWith(it) }) {
                return desc
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
