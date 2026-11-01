# Descroll 🛡️
### Personal Anti-Doomscroll Guardian for Android (Reverse-Engineered ScrollGuard Engine)

**Descroll** is a lightweight, privacy-first Android accessibility guardian designed to break mindless reels/shorts addiction without cutting you off from important direct messages, stories, or regular content.

---

## ⚡ How It Works

1. **Surgical Reels & Shorts Interception**:
   - Descroll uses Android's Accessibility Service with exact reverse-engineered node hierarchies (`clips_viewer_view_pager`, `reel_player_page_container`, `player_view`).
   - Only fullscreen short-video feeds are intercepted.
2. **Strict Daily Limit (10 Reels/Day)**:
   - You can watch up to **10 distinct reels per day**.
   - A non-intrusive HUD toast notifies you of your progress: *(e.g., "Reel 3 of 10 today")*.
   - Debouncing and content signatures prevent rapid scrolling glitches or counting the same video multiple times.
3. **Instant Block on Limit**:
   - The moment you scroll onto or open the **10th reel**, Descroll instantly executes a back navigation action (`GLOBAL_ACTION_BACK`) and displays the **Blocked Screen**.
   - Reels remain locked until midnight (automatic daily reset).
4. **Zero Interference with Messages & Stories**:
   - 💬 **Direct Messages (DMs)**: Private video shares inside chats are exempted via `reply_bar_edittext` and sender tags.
   - 📸 **Stories**: Instagram and Snapchat stories (`reel_viewer_root`, `stories_tray`, etc.) are completely unrestricted.
   - 🏠 **Home Feed & Search**: Unhindered browsing.

---

## 📦 How to Download or Build the APK

### Direct Download (GitHub Releases)
Download **`Descroll.apk`** directly from the [GitHub Releases](https://github.com/vrika4/Descroll/releases) section.

### Method 1: Using Android Studio
1. Open **Android Studio**.
2. Click **Open** and select this directory.
3. In the top menu, go to **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. Transfer `Descroll.apk` to your Android device.

### Method 2: Command Line (with Android SDK)
In the project directory, run:
```bash
./gradlew assembleRelease
```
The APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📱 How to Set Up on Your Phone

1. Install and open **Descroll**.
2. Tap **Accessibility Service** -> Find **Descroll** in Accessibility settings and toggle it **ON**.
   > *Note on Android 13+: If Accessibility is greyed out as a "Restricted Setting", go to Phone Settings > Apps > Descroll > tap the 3 dots in the top right > tap **"Allow restricted settings"**.*
3. Tap **Display Over Other Apps** -> Grant permission.
4. Tap **Battery Optimization** -> Set to "Unrestricted" so the OS doesn't kill the background accessibility service.
5. You're protected! Descroll will quietly guard your attention.
