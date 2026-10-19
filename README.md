# Descroll 🧘‍♂️
### Personal Anti-Doomscroll Guardian for Android (Instagram & YouTube)

Descroll is your personal Android app built to break mindless reels/shorts addiction without cutting you off from important direct messages.

---

## ⚡ How It Works

1. **15-Second Pause**: Whenever you tap Instagram or YouTube, a 15-second breathing screen intercepts the launch.
2. **Intent Choice**: After the 15-second breathing exercise, two options appear:
   - 🎬 **Reels / Shorts**: Opens the app with strict limits:
     - **5 Reels per session**: As soon as you swipe through 5 reels, Descroll kicks you out and locks the app with a block screen.
     - **10 Reels opens per day**: Once you use all 10 opens, you cannot open the app for reels until midnight.
   - 💬 **Messages / Regular Videos**: Opens immediately with **unlimited** access and no restrictions.
3. **Session Expiry & Daily Lock**:
   - If you exceed 5 reels or the session timer expires, you are redirected to the **Blocked Screen**.
   - From the blocked screen, you can go to your home screen or switch directly to **Messages mode**.

---

## 📦 How to Build the APK

### Method 1: Using Android Studio (Recommended & Easiest)
1. Open **Android Studio**.
2. Click **Open** and select this directory: `E:\Compi\Descroll`.
3. Wait for Gradle Sync to complete.
4. In the top menu, go to **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. Once built, click the **"locate"** link in the bottom-right notification popup to get your `app-debug.apk`.
6. Transfer `app-debug.apk` to your Android phone via USB, Google Drive, WhatsApp, or Telegram.

### Method 2: Using Command Line (if Android SDK is installed)
In the project directory, run:
```bash
./gradlew assembleDebug
```
The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 How to Set Up on Your Phone

Once installed on your phone:
1. Open **Descroll**.
2. Tap **Enable Accessibility Service** -> Find **Descroll** in your phone's Accessibility settings and toggle it **ON**.
   > *Note: On Android 13+, if Accessibility is greyed out as a "Restricted Setting", go to Phone Settings > Apps > Descroll > tap the 3 dots in the top right > tap **"Allow restricted settings"**.*
3. Tap **Enable Overlay Permission** -> Grant **"Display over other apps"**.
4. You're all set! Now whenever you open Instagram or YouTube, Descroll will protect your attention.
