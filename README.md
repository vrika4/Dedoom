# Dedoom 🧘‍♂️
### Personal Anti-Doomscroll Guardian for Android (Instagram & YouTube)

Dedoom is your personal Android app built to break mindless reels/shorts addiction without cutting you off from important direct messages.

---

## ⚡ How It Works

1. **15-Second Pause**: Whenever you tap Instagram or YouTube, a 15-second breathing screen intercepts the launch.
2. **Intent Choice**: After the 15-second breathing exercise, two options appear:
   - 🎬 **Reels / Shorts**: Opens the app with strict limits:
     - **5 Reels per session**: As soon as you swipe through 5 reels, Dedoom kicks you out and locks the app with a block screen.
     - **10 Reels opens per day**: Once you use all 10 opens, you cannot open the app for reels until midnight.
   - 💬 **Messages / Regular Videos**: Opens immediately with **unlimited** access and no restrictions.
3. **Session Expiry & Daily Lock**:
   - If you exceed 5 reels or the session timer expires, you are redirected to the **Blocked Screen**.
   - From the blocked screen, you can go to your home screen or switch directly to **Messages mode**.

---

## 📦 How to Download or Build the APK

### Direct Download (GitHub Releases)
Download **`Dedoom.apk`** directly from the [GitHub Releases](https://github.com/vrika4/Dedoom/releases) section.

### Method 1: Using Android Studio
1. Open **Android Studio**.
2. Click **Open** and select this directory.
3. In the top menu, go to **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. Transfer `Dedoom.apk` to your Android phone.

### Method 2: Using Command Line (if Android SDK is installed)
In the project directory, run:
```bash
./gradlew assembleRelease
```
The APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📱 How to Set Up on Your Phone

Once installed on your phone:
1. Open **Dedoom**.
2. Tap **Enable Accessibility Service** -> Find **Dedoom** in your phone's Accessibility settings and toggle it **ON**.
   > *Note: On Android 13+, if Accessibility is greyed out as a "Restricted Setting", go to Phone Settings > Apps > Dedoom > tap the 3 dots in the top right > tap **"Allow restricted settings"**.*
3. Tap **Enable Overlay Permission** -> Grant **"Display over other apps"**.
4. You're all set! Now whenever you open Instagram or YouTube, Dedoom will protect your attention.
