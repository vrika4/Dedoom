package com.dedoom.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen overlay that appears when a monitored app is opened.
 *
 * Flow:
 * 1. Shows breathing animation with 15-second countdown
 * 2. After countdown, reveals two choice buttons:
 *    - "Reels/Shorts" (limited: 5 per session, 10 opens/day)
 *    - "Messages/Videos" (unlimited)
 * 3. Records the user's choice and sets grace period
 */
class OverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }

    private lateinit var tracker: UsageTracker
    private var targetPackage: String = ""
    private var countdownTimer: CountDownTimer? = null
    private var breathAnimator: AnimatorSet? = null

    // Views
    private lateinit var countdownText: TextView
    private lateinit var breatheLabel: TextView
    private lateinit var breatheCircle: View
    private lateinit var breatheGlow: View
    private lateinit var countdownContainer: View
    private lateinit var choiceContainer: LinearLayout
    private lateinit var btnReels: LinearLayout
    private lateinit var btnReelsText: TextView
    private lateinit var btnReelsSubtext: TextView
    private lateinit var btnMessages: LinearLayout
    private lateinit var btnMessagesText: TextView
    private lateinit var btnDismiss: TextView
    private lateinit var appNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay)

        tracker = UsageTracker(this)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""

        if (targetPackage.isEmpty()) {
            finish()
            return
        }

        bindViews()
        setupUI()
        startBreathingAnimation()
        startCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        breathAnimator?.cancel()
    }

    override fun onBackPressed() {
        // Prevent back press during countdown — user must wait or dismiss
        if (choiceContainer.visibility == View.VISIBLE) {
            goHome()
        }
        // During countdown, back press is blocked
    }

    private fun bindViews() {
        countdownText = findViewById(R.id.countdownText)
        breatheLabel = findViewById(R.id.breatheLabel)
        breatheCircle = findViewById(R.id.breatheCircle)
        breatheGlow = findViewById(R.id.breatheGlow)
        countdownContainer = findViewById(R.id.countdownContainer)
        choiceContainer = findViewById(R.id.choiceContainer)
        btnReels = findViewById(R.id.btnReels)
        btnReelsText = findViewById(R.id.btnReelsText)
        btnReelsSubtext = findViewById(R.id.btnReelsSubtext)
        btnMessages = findViewById(R.id.btnMessages)
        btnMessagesText = findViewById(R.id.btnMessagesText)
        btnDismiss = findViewById(R.id.btnDismiss)
        appNameText = findViewById(R.id.appNameText)
    }

    private fun setupUI() {
        // Set app name
        appNameText.text = tracker.getAppDisplayName(targetPackage)

        // Set button labels based on app type
        if (tracker.isReelsApp(targetPackage)) {
            btnReelsText.text = "🎬  Reels"
            btnMessagesText.text = "💬  Messages"
        } else {
            btnReelsText.text = "🎬  Shorts"
            btnMessagesText.text = "▶️  Regular Videos"
        }

        // Update remaining count
        val remaining = tracker.getRemainingReelsOpens()
        val max = tracker.getMaxReelsOpens()
        btnReelsSubtext.text = "$remaining / $max opens left today"

        // If no reels opens remaining, disable the button
        if (!tracker.canOpenReels()) {
            btnReels.background = getDrawable(R.drawable.bg_button_blocked)
            btnReelsSubtext.text = "No opens left today"
            btnReels.alpha = 0.6f
        }

        // Click listeners
        btnReels.setOnClickListener { onReelsChosen() }
        btnMessages.setOnClickListener { onMessagesChosen() }
        btnDismiss.setOnClickListener { goHome() }
    }

    /**
     * Starts the breathing circle animation (expand/contract loop).
     */
    private fun startBreathingAnimation() {
        val scaleXUp = ObjectAnimator.ofFloat(breatheCircle, "scaleX", 0.6f, 1.1f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleYUp = ObjectAnimator.ofFloat(breatheCircle, "scaleY", 0.6f, 1.1f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleXDown = ObjectAnimator.ofFloat(breatheCircle, "scaleX", 1.1f, 0.6f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleYDown = ObjectAnimator.ofFloat(breatheCircle, "scaleY", 1.1f, 0.6f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Glow follows the main circle
        val glowXUp = ObjectAnimator.ofFloat(breatheGlow, "scaleX", 0.5f, 1.2f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val glowYUp = ObjectAnimator.ofFloat(breatheGlow, "scaleY", 0.5f, 1.2f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val glowXDown = ObjectAnimator.ofFloat(breatheGlow, "scaleX", 1.2f, 0.5f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val glowYDown = ObjectAnimator.ofFloat(breatheGlow, "scaleY", 1.2f, 0.5f).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
        }

        val expandSet = AnimatorSet().apply {
            playTogether(scaleXUp, scaleYUp, glowXUp, glowYUp)
        }
        val contractSet = AnimatorSet().apply {
            playTogether(scaleXDown, scaleYDown, glowXDown, glowYDown)
        }

        breathAnimator = AnimatorSet().apply {
            playSequentially(expandSet, contractSet)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Loop the animation
                    if (!isFinishing && !isDestroyed) {
                        startBreathingAnimation()
                    }
                }
            })
            start()
        }
    }

    /**
     * Starts the 15-second countdown.
     * After it finishes, reveals the choice buttons.
     */
    private fun startCountdown() {
        val delayMs = tracker.getDelaySeconds() * 1000L

        countdownTimer = object : CountDownTimer(delayMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                countdownText.text = secondsLeft.toString()
            }

            override fun onFinish() {
                showChoiceButtons()
            }
        }.start()
    }

    /**
     * Reveals the choice buttons with a fade-in animation.
     */
    private fun showChoiceButtons() {
        // Fade out countdown
        countdownContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .start()

        breatheLabel.animate()
            .alpha(0f)
            .setDuration(300)
            .start()

        // Shrink breathing circle
        breatheCircle.animate()
            .scaleX(0.3f)
            .scaleY(0.3f)
            .alpha(0.2f)
            .setDuration(500)
            .start()

        breatheGlow.animate()
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0.1f)
            .setDuration(500)
            .start()

        // Show choice buttons with slide-up + fade-in
        choiceContainer.visibility = View.VISIBLE
        choiceContainer.translationY = 200f
        choiceContainer.alpha = 0f
        choiceContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(200)
            .start()
    }

    /**
     * User chose "Reels/Shorts" — check limits and proceed.
     */
    private fun onReelsChosen() {
        if (!tracker.canOpenReels()) {
            // Daily limit reached — block
            launchBlocked(isDailyLimit = true)
            return
        }

        // Record the open and start the session timer
        tracker.incrementReelsOpens()
        tracker.resetReelsSwipes(targetPackage)
        tracker.setSessionMode(targetPackage, "reels")
        tracker.setGracePeriod(targetPackage)
        tracker.startReelsTimer(targetPackage)

        // Let the app proceed
        finish()
    }

    /**
     * User chose "Messages/Videos" — no limits, proceed freely.
     */
    private fun onMessagesChosen() {
        tracker.setSessionMode(targetPackage, "messages")
        tracker.setGracePeriod(targetPackage)

        // Let the app proceed
        finish()
    }

    /**
     * User chose to go back to home screen.
     */
    private fun goHome() {
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }

    /**
     * Launch the blocked screen.
     */
    private fun launchBlocked(isDailyLimit: Boolean) {
        val intent = android.content.Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(BlockedActivity.EXTRA_IS_DAILY_LIMIT, isDailyLimit)
            putExtra(BlockedActivity.EXTRA_IS_SESSION_EXPIRY, false)
        }
        startActivity(intent)
        finish()
    }
}
