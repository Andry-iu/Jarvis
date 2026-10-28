package it.jarviswake

import android.annotation.SuppressLint
import android.os.Build
import android.window.OnBackInvokedDispatcher
import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/** Visible launch surface above keyguard; unlocking is optional and user initiated. */
class LaunchActivity : Activity() {
    companion object {
        private var active = WeakReference<LaunchActivity>(null)
        fun closeForCycle(token: String) {
            active.get()?.takeIf { it.token == token }?.finish()
        }
    }
    private var token = ""
    private var resumed = false
    private var handedOff = false
    private var dismissalPending = false
    private val main = Handler(Looper.getMainLooper())
    private lateinit var hud: HolographicOverlayView
    private val poll = object : Runnable {
        override fun run() {
            advance()
            if (resumed && !handedOff && VoiceCycle.matches(token)) main.postDelayed(this, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(VoiceCycle.EXTRA_ID).orEmpty()
        if (!VoiceCycle.matches(token)) {
            State.setAttempt(this, "Avvio precedente scaduto o annullato; nessuna chat riaperta.")
            finish()
            return
        }
        if (VoiceCycle.phase != VoiceCycleState.Phase.UNLOCK) {
            handedOff = true
            finish()
            return
        }
        active = WeakReference(this)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { cancel() }
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        hud = HolographicOverlayView(this).apply {
            setStatus("AVVIO CHATGPT")
            setCoverActions(
                singleTap = { /* The microphone is available only after Voice starts. */ },
                belowTap = { CoverSettings.setOpaque(this@LaunchActivity, !CoverSettings.isOpaque(this@LaunchActivity)) },
                close = { cancel() },
                export = { DiagnosticExportActivity.open(this@LaunchActivity) },
                hide = { cancel() }
            )
        }
        val root = FrameLayout(this)
        root.addView(hud, FrameLayout.LayoutParams(-1, -1))
        val unlock = Button(this).apply {
            text = "Sblocca telefono"
            setOnClickListener { requestUnlock() }
        }
        root.addView(unlock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(112)
        })
        hud.setOpacityBottomAnchor(unlock)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        hud.post { WaitingAudio.coverShown(this, token) }
        State.setAttempt(this, "Schermata Jarvis pronta; avvio Voice senza richiedere lo sblocco.")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        main.removeCallbacks(poll)
        main.post(poll)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && resumed) { main.removeCallbacks(poll); main.post(poll) }
    }

    override fun onPause() {
        resumed = false
        main.removeCallbacks(poll)
        super.onPause()
    }

    private fun advance() {
        if (!VoiceCycle.matches(token)) { finish(); return }
        if (handedOff || !resumed || !hasWindowFocus()) return
        if (NativeChatSettings.enabled(this) && getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            hud.setStatus("SBLOCCA IL TELEFONO PER CHATGPT APP")
            return
        }
        // Both this Activity and the cycle's Web Activity can remain resumed above keyguard.
        if (VoiceCycle.openWeb(this, token, resumed, hasWindowFocus())) {
            handedOff = true
            hud.setStatus("APERTURA CHATGPT")
            State.setAttempt(this, "Jarvis in primo piano; apro ChatGPT anche a telefono bloccato.")
            ChatGpt.attempt(this, token)
            finish()
        }
    }

    private fun requestUnlock() {
        if (!VoiceCycle.matches(token) || !resumed || !hasWindowFocus() || dismissalPending) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) return
        dismissalPending = true
        State.setAttempt(this, "Richiesta sblocco inviata ad Android.")
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                if (!VoiceCycle.matches(token) || isDestroyed) return
                dismissalPending = false
                State.setAttempt(this@LaunchActivity, "Android conferma lo sblocco; attendo ritorno alla schermata Jarvis.")
                main.removeCallbacks(poll)
                if (resumed) main.post(poll)
            }
            override fun onDismissCancelled() { unlockIncomplete("SBLOCCO ANNULLATO — PREMI SBLOCCA") }
            override fun onDismissError() { unlockIncomplete("PREMI SBLOCCA O SBLOCCA MANUALMENTE") }
        })
    }

    private fun unlockIncomplete(message: String) {
        if (!VoiceCycle.matches(token) || isDestroyed) return
        dismissalPending = false
        hud.setStatus(message)
        State.setAttempt(this, message)
    }

    private fun cancel() = VoiceCycle.cancel(this, token, "Avvio annullato. Ascolto Hey Jarvis riattivato.")
    // API 31-32 fallback only; API 33+ uses OnBackInvokedDispatcher above.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    @Deprecated("Legacy back callback for API 31-32")
    override fun onBackPressed() { cancel() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        main.removeCallbacks(poll)
        if (active.get() === this) active.clear()
        if (!isChangingConfigurations && !handedOff && VoiceCycle.matches(token)) cancel()
        super.onDestroy()
    }
}
