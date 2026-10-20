package com.dedoom.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Intelligent detector for Instagram Reels and YouTube Shorts.
 * Reverse-engineered from Scrolless & ScrollGuard architecture.
 *
 * Strict Rules:
 * 1. Stories MUST NEVER be detected as Reels (exempted via reel_viewer_root, stories_tray, etc.).
 * 2. Direct Messages (DMs) MUST NEVER be detected as Reels (exempted via reply_bar_edittext & sender tags).
 * 3. Bottom navigation bar icons MUST NOT trigger false positives on home feed or search.
 * 4. Fast O(1) indexed view ID lookup via Android Accessibility framework.
 */
object ReelDetector {

    // Package constants
    const val PACKAGE_INSTAGRAM = "com.instagram.android"
    const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    const val PACKAGE_YOUTUBE_REVANCED = "app.revanced.android.youtube"

    // Instagram: Exact verified View ID for Reels viewer
    // (from reverse-engineered Scrolless / ScrollGuard)
    private val INSTAGRAM_REELS_VIEW_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/layout_clips_viewer",
        "com.instagram.android:id/clips_video_container"
    )

    // Instagram: Story indicators (MUST NEVER BE BLOCKED)
    private val INSTAGRAM_STORY_VIEW_IDS = listOf(
        "com.instagram.android:id/reel_viewer_root",
        "com.instagram.android:id/reel_feed_timeline",
        "com.instagram.android:id/stories_tray",
        "com.instagram.android:id/reel_viewer_framelayout",
        "com.instagram.android:id/reel_viewer_shadow",
        "com.instagram.android:id/story_item"
    )

    // Instagram: Direct Message indicators (MUST NEVER BE BLOCKED)
    private val INSTAGRAM_DM_REQUIRED_IDS = listOf(
        "com.instagram.android:id/reply_bar_edittext",
        "com.instagram.android:id/sender_username_or_fullname"
    )
    private const val INSTAGRAM_SUGGESTED_REEL_TITLE = "com.instagram.android:id/suggested_title"

    // YouTube: Exact verified View ID for Shorts container
    private val YOUTUBE_SHORTS_VIEW_IDS = listOf(
        "com.google.android.youtube:id/reel_player_page_container",
        "app.revanced.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/shorts_video_player",
        "com.google.android.youtube:id/reel_watch_fragment"
    )

    /**
     * Checks if the active window is currently displaying an active Instagram Reel or YouTube Short.
     * Returns true ONLY if on an active, full-screen Reels/Shorts feed.
     * Returns false for Stories, DMs/Messages, Home feed, Search, or Profile.
     */
    fun isViewingReelsOrShorts(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false

        return when (packageName) {
            PACKAGE_INSTAGRAM -> checkInstagram(rootNode)
            PACKAGE_YOUTUBE, PACKAGE_YOUTUBE_REVANCED -> checkYouTube(rootNode)
            else -> false
        }
    }

    /**
     * Extracts a unique signature (creator, caption, or description) for the reel currently playing.
     * Used to detect when a user has actually swiped to a new reel vs re-scanning the same reel.
     */
    fun getReelSignature(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null

        when (packageName) {
            PACKAGE_INSTAGRAM -> {
                // Try finding caption or creator tags
                for (id in listOf(
                    "com.instagram.android:id/clips_viewer_author_name",
                    "com.instagram.android:id/row_feed_photo_profile_name",
                    "com.instagram.android:id/clips_author_name"
                )) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    val text = nodes?.firstOrNull()?.text?.toString()
                    if (!text.isNullOrBlank()) return text
                }

                // Fallback: search descriptions starting with "reel by" or "audio: "
                return findFirstMatchingDesc(rootNode, listOf("reel by ", "audio: "))
            }
            PACKAGE_YOUTUBE, PACKAGE_YOUTUBE_REVANCED -> {
                for (id in listOf(
                    "com.google.android.youtube:id/reel_channel_name",
                    "com.google.android.youtube:id/channel_name"
                )) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    val text = nodes?.firstOrNull()?.text?.toString()
                    if (!text.isNullOrBlank()) return text
                }
                return findFirstMatchingDesc(rootNode, listOf("shorts player", "short by"))
            }
            else -> return null
        }
    }

    private fun checkInstagram(root: AccessibilityNodeInfo): Boolean {
        // 1. Stories Exemption: If user is watching stories, NEVER block!
        for (storyId in INSTAGRAM_STORY_VIEW_IDS) {
            if (hasVisibleNode(root, storyId)) {
                return false
            }
        }

        // 2. Direct Messages (DM) Exemption:
        // In Instagram, when a reel is shared in chat, a reply bar or sender header is shown,
        // while algorithmic feed reels show "suggested_title" or no DM reply bar.
        val hasDmReplyBar = INSTAGRAM_DM_REQUIRED_IDS.any { hasVisibleNode(root, it) }
        val hasSuggestedTitle = hasVisibleNode(root, INSTAGRAM_SUGGESTED_REEL_TITLE)
        if (hasDmReplyBar && !hasSuggestedTitle) {
            // This is a private DM video, NOT an endless doomscrolling feed
            return false
        }

        // 3. Reels Detection: Check for the exact clips viewer view pager
        for (reelsId in INSTAGRAM_REELS_VIEW_IDS) {
            if (hasVisibleNode(root, reelsId)) {
                return true
            }
        }

        return false
    }

    private fun checkYouTube(root: AccessibilityNodeInfo): Boolean {
        // YouTube Shorts use the dedicated reel_player_page_container
        for (shortsId in YOUTUBE_SHORTS_VIEW_IDS) {
            if (hasVisibleNode(root, shortsId)) {
                return true
            }
        }
        return false
    }

    /**
     * Efficiently checks if a node with the given viewId exists and is visible on screen.
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
