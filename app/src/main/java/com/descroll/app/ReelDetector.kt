package com.descroll.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Intelligent detector for Instagram Reels, YouTube Shorts, TikTok, and Facebook Reels.
 * Engineered from verified View IDs and node hierarchies reverse-engineered from Scrolless & ScrollGuard.
 *
 * Core Guarantees:
 * 1. Stories MUST NEVER be blocked or covered (exempted via reel_viewer_root, stories_tray, etc.).
 * 2. Direct Messages (DMs) MUST NEVER be blocked or covered (exempted via reply_bar_edittext & sender credentials).
 * 3. Regular feed posts, searches, channels, and profile pages are 100% unrestricted.
 * 4. Full-screen reels trigger daily quota tracking & instant back blocker when limit is reached.
 * 5. Embedded home feed reels & Shorts shelves are dynamically covered with an opaque black shield.
 */
object ReelDetector {

    // ─── Instagram: Full-Screen Reels View IDs ───────────────────────
    private val INSTAGRAM_REELS_VIEW_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/layout_clips_viewer",
        "com.instagram.android:id/clips_video_container"
    )

    // ─── Instagram: Embedded Home Feed Shelves / Netego ─────────────
    private val INSTAGRAM_EMBEDDED_SHELF_IDS = listOf(
        "com.instagram.android:id/clips_netego_item",
        "com.instagram.android:id/netego_reel_tray",
        "com.instagram.android:id/clips_card_container",
        "com.instagram.android:id/clips_item_container",
        "com.instagram.android:id/netego_bloks_view",
        "com.instagram.android:id/netego_layout",
        "com.instagram.android:id/reel_netego_item",
        "com.instagram.android:id/carousel_media_group"
    )

    // ─── Instagram: Stories View IDs (Exempted - Never Blocked) ─────
    private val INSTAGRAM_STORY_VIEW_IDS = listOf(
        "com.instagram.android:id/reel_viewer_root",
        "com.instagram.android:id/reel_feed_timeline",
        "com.instagram.android:id/stories_tray",
        "com.instagram.android:id/reel_viewer_framelayout",
        "com.instagram.android:id/reel_viewer_shadow",
        "com.instagram.android:id/story_item"
    )

    // ─── Instagram: DM Indicators (Exempted - Never Blocked) ────────
    private val INSTAGRAM_DM_REQUIRED_IDS = listOf(
        "com.instagram.android:id/reply_bar_edittext",
        "com.instagram.android:id/sender_username_or_fullname"
    )
    private const val INSTAGRAM_SUGGESTED_REEL_TITLE = "com.instagram.android:id/suggested_title"

    // ─── YouTube: Full-Screen Shorts View IDs ────────────────────────
    private val YOUTUBE_SHORTS_VIEW_IDS = listOf(
        "com.google.android.youtube:id/reel_player_page_container",
        "app.revanced.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/shorts_video_player",
        "com.google.android.youtube:id/reel_watch_fragment"
    )

    // ─── YouTube: Embedded Home Feed Shorts Shelves ─────────────────
    private val YOUTUBE_SHORTS_SHELF_IDS = listOf(
        "com.google.android.youtube:id/shorts_shelf",
        "com.google.android.youtube:id/reel_shelf",
        "com.google.android.youtube:id/inline_shorts_container",
        "com.google.android.youtube:id/reel_recycler_view",
        "com.google.android.youtube:id/reel_item",
        "app.revanced.android.youtube:id/shorts_shelf",
        "app.revanced.android.youtube:id/reel_shelf",
        "app.revanced.android.youtube:id/inline_shorts_container"
    )

    // ─── Facebook: Embedded Reels Shelves ───────────────────────────
    private val FACEBOOK_EMBEDDED_REELS_IDS = listOf(
        "fb_shorts_tray",
        "video_channel_reels_tray",
        "FbShortsViewerTray"
    )

    // ─── TikTok: Player View IDs ────────────────────────────────────
    private val TIKTOK_VIEW_IDS = listOf(
        "player_view",
        "simplayer_api_player_view"
    )

    /**
     * Inspects active window hierarchy to determine if the user is currently viewing short-form video in full screen.
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
     * Finds screen bounds of embedded reels carousels or shorts shelves displayed on the home page.
     * Used by ReelCoverOverlayManager to place an opaque black cover directly over them.
     */
    fun findEmbeddedReelBounds(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): List<Rect> {
        if (rootNode == null) return emptyList()

        // 1. Stories Exemption: NEVER cover Stories
        if (packageName == UsageTracker.PKG_INSTAGRAM) {
            for (storyId in INSTAGRAM_STORY_VIEW_IDS) {
                if (hasVisibleNode(rootNode, storyId)) return emptyList()
            }
            // 2. DM Exemption: NEVER cover DMs
            val hasDmReplyBar = INSTAGRAM_DM_REQUIRED_IDS.any { hasVisibleNode(rootNode, it) }
            val hasSuggestedTitle = hasVisibleNode(rootNode, INSTAGRAM_SUGGESTED_REEL_TITLE)
            if (hasDmReplyBar && !hasSuggestedTitle) return emptyList()
        }

        // 3. Full-screen reels: If user is inside full-screen viewer, do not place small embedded shelf covers
        if (isViewingReelsOrShorts(rootNode, packageName)) {
            return emptyList()
        }

        val candidates = mutableListOf<Rect>()

        when {
            packageName == UsageTracker.PKG_INSTAGRAM -> {
                // Find netego / shelf containers
                for (id in INSTAGRAM_EMBEDDED_SHELF_IDS) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id) ?: continue
                    for (node in nodes) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (isValidCoverRect(rect, screenWidth, screenHeight)) {
                            candidates.add(rect)
                        }
                    }
                }

                // Check for "Suggested Reels" or "Reels and short videos" header containers
                findShelfByHeader(
                    rootNode,
                    listOf("reels and short videos", "suggested reels"),
                    screenWidth,
                    screenHeight
                )?.let { candidates.add(it) }
            }

            packageName.contains("youtube") -> {
                // YouTube Shorts shelves on home feed
                for (id in YOUTUBE_SHORTS_SHELF_IDS) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id) ?: continue
                    for (node in nodes) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (isValidCoverRect(rect, screenWidth, screenHeight)) {
                            candidates.add(rect)
                        }
                    }
                }

                // Check for "Shorts" header container on YouTube home feed
                findShelfByHeader(rootNode, listOf("shorts"), screenWidth, screenHeight)?.let {
                    candidates.add(it)
                }
            }

            packageName.contains("facebook") -> {
                for (id in FACEBOOK_EMBEDDED_REELS_IDS) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id) ?: continue
                    for (node in nodes) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (isValidCoverRect(rect, screenWidth, screenHeight)) {
                            candidates.add(rect)
                        }
                    }
                }
            }
        }

        return mergeAndDeduplicateBounds(candidates)
    }

    private fun isValidCoverRect(rect: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        // Reasonable size for an embedded shelf/card
        if (rect.width() < 120 || rect.height() < 100) return false

        // Must be visible on screen
        if (rect.bottom <= 0 || rect.top >= screenHeight) return false
        if (rect.right <= 0 || rect.left >= screenWidth) return false

        return true
    }

    private fun findShelfByHeader(
        root: AccessibilityNodeInfo,
        headerKeywords: List<String>,
        screenWidth: Int,
        screenHeight: Int
    ): Rect? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var depth = 0

        while (queue.isNotEmpty() && depth < 50) {
            val node = queue.removeFirst()
            depth++

            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (headerKeywords.any { text.contains(it) || desc.contains(it) }) {
                // Traverse up to parent/grandparent container to find the shelf bounds
                var parent = node.parent
                var steps = 0
                while (parent != null && steps < 3) {
                    val pRect = Rect()
                    parent.getBoundsInScreen(pRect)
                    if (pRect.width() > screenWidth * 0.7 && pRect.height() > 200) {
                        if (isValidCoverRect(pRect, screenWidth, screenHeight)) {
                            return pRect
                        }
                    }
                    parent = parent.parent
                    steps++
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun mergeAndDeduplicateBounds(bounds: List<Rect>): List<Rect> {
        if (bounds.size <= 1) return bounds

        val result = mutableListOf<Rect>()
        for (rect in bounds) {
            var merged = false
            for (i in result.indices) {
                val existing = result[i]
                if (existing.contains(rect)) {
                    merged = true
                    break
                } else if (rect.contains(existing)) {
                    result[i] = rect
                    merged = true
                    break
                } else if (Rect.intersects(existing, rect)) {
                    existing.union(rect)
                    merged = true
                    break
                }
            }
            if (!merged) {
                result.add(Rect(rect))
            }
        }
        return result
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
