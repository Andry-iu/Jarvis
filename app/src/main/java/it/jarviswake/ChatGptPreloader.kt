package it.jarviswake

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.ref.WeakReference

/** Loads the authenticated project page before wake and transfers the same WebView to Voice. */
internal object ChatGptPreloader {
    private val main = Handler(Looper.getMainLooper())
    private var web = WeakReference<WebView>(null)
    private var app: Context? = null
    private var pageReady = false
    private var stopped = false
    private var polls = 0
    private var foundLogged = false
    private var voiceLocation: PreloadedVoiceLocation? = null

    private val poll = object : Runnable {
        override fun run() {
            val view = web.get() ?: return
            if (stopped || !pageReady || polls++ >= 48) return
            view.evaluateJavascript(WebChatActivity.preloadVoiceControlScript()) { raw ->
                if (stopped || web.get() !== view) return@evaluateJavascript
                val location = runCatching {
                    val encoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching null
                    JSONObject(encoded).let { result ->
                        if (!result.has("x") || !result.has("y") ||
                            result.optString("strategy") !in setOf("semantic", "composer")) null
                        else PreloadedVoiceLocation(
                            x = result.getDouble("x").toFloat(),
                            y = result.getDouble("y").toFloat(),
                            viewportWidth = result.optDouble("viewportWidth", 0.0).toFloat(),
                            viewportHeight = result.optDouble("viewportHeight", 0.0).toFloat(),
                            semantic = result.optString("strategy") == "semantic"
                        )
                    }
                }.getOrNull()
                if (location != null) {
                    voiceLocation = location
                    if (!foundLogged) {
                        foundLogged = true
                        app?.let { DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_VOICE_FOUND) }
                    }
                    return@evaluateJavascript
                }
                main.postDelayed(this, if (polls < 24) 250L else 1_000L)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun prepare(context: Context, container: FrameLayout) {
        if (web.get() != null) return
        app = context.applicationContext
        stopped = false
        pageReady = false
        polls = 0
        foundLogged = false
        voiceLocation = null
        val view = runCatching { WebView(context) }.getOrElse {
            DiagnosticLog.record(context, DiagnosticLog.Event.WEB_PRELOAD_FAILED)
            return
        }
        web = WeakReference(view)
        view.apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            isClickable = false
            isFocusable = false
            importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    if (stopped || web.get() !== view) return
                    pageReady = false
                    voiceLocation = null
                    polls = 0
                    foundLogged = false
                    main.removeCallbacks(poll)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (stopped || web.get() !== view) return
                    pageReady = true
                    app?.let { DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_PAGE_READY) }
                    main.removeCallbacks(poll)
                    main.post(poll)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (!request.isForMainFrame || stopped) return
                    app?.let { DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_FAILED) }
                }
            }
        }
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
            // Keep the live preloader attached, but behind the interactive app UI.
            // An alpha-zero full-screen WebView added last can still participate in
            // touch dispatch and make every control in MainActivity appear frozen.
            container.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
            DiagnosticLog.record(context, DiagnosticLog.Event.WEB_PRELOAD_STARTED)
            view.loadUrl(ChatGptProjectSettings.getUrl(context))
        } catch (_: RuntimeException) {
            DiagnosticLog.record(context, DiagnosticLog.Event.WEB_PRELOAD_FAILED)
            discard()
        }
    }

    /** Shows the same authenticated WebView as an interactive settings page. */
    fun showForConfiguration(host: FrameLayout): Boolean = moveConfigurationWebView(host, visible = true)

    /** Returns the same WebView behind Jarvis without reloading the current chat. */
    fun hideConfiguration(container: FrameLayout): Boolean = moveConfigurationWebView(container, visible = false)

    private fun moveConfigurationWebView(host: FrameLayout, visible: Boolean): Boolean {
        val view = web.get() ?: return false
        val parent = view.parent as? android.view.ViewGroup
        if (parent !== host) {
            val detached = runCatching { parent?.removeView(view); view.parent == null }.getOrDefault(false)
            if (!detached) return false
            if (!runCatching { host.addView(view, 0, FrameLayout.LayoutParams(-1, -1)); true }.getOrDefault(false)) return false
        }
        view.alpha = if (visible) 1f else 0f
        view.isClickable = visible
        view.isFocusable = visible
        view.isFocusableInTouchMode = visible
        view.importantForAccessibility = if (visible) View.IMPORTANT_FOR_ACCESSIBILITY_YES
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        return true
    }

    /** Detaches the warmed page for the cycle; no duplicate WebView or page reload is created. */
    fun handoff(cycleId: String): PreparedChatPage? {
        val view = web.get() ?: return null
        stopped = true
        main.removeCallbacks(poll)
        val context = app
        val parent = view.parent as? android.view.ViewGroup
        val detached = runCatching {
            if (parent == null) false else {
                parent.removeView(view)
                view.parent == null
            }
        }.getOrDefault(false)
        if (!detached) {
            context?.let { DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_FAILED, cycleId) }
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
            web.clear()
            pageReady = false
            voiceLocation = null
            app = null
            return null
        }
        val prepared = PreparedChatPage(view, pageReady, voiceLocation)
        web.clear()
        pageReady = false
        voiceLocation = null
        app = null
        context?.let {
            DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_REUSED, cycleId)
            if (prepared.voiceLocation != null)
                DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_VOICE_SEEDED, cycleId)
            if (prepared.pageReady)
                DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PAGE_READY, cycleId)
        }
        return prepared
    }

    /** Used only when a preloaded page cannot be handed to a live cycle or its host is destroyed. */
    fun discard() {
        val view = web.get() ?: return
        stopped = true
        main.removeCallbacks(poll)
        runCatching { view.stopLoading() }
        runCatching { view.webChromeClient = null }
        runCatching { view.webViewClient = WebViewClient() }
        runCatching { (view.parent as? android.view.ViewGroup)?.removeView(view) }
        runCatching { view.destroy() }
        web.clear()
        pageReady = false
        voiceLocation = null
        app?.let { DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_DISCARDED) }
        app = null
    }
}

internal data class PreloadedVoiceLocation(
    val x: Float,
    val y: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val semantic: Boolean
)

internal data class PreloadedViewPoint(val x: Float, val y: Float)

internal object PreloadedVoicePolicy {
    fun scale(location: PreloadedVoiceLocation, viewWidth: Int, viewHeight: Int): PreloadedViewPoint? {
        if (location.viewportWidth <= 0f || location.viewportHeight <= 0f || viewWidth <= 0 || viewHeight <= 0)
            return null
        return PreloadedViewPoint(
            location.x * viewWidth / location.viewportWidth,
            location.y * viewHeight / location.viewportHeight
        )
    }

    fun matches(seed: PreloadedViewPoint, x: Float, y: Float, tolerance: Float = 8f): Boolean =
        kotlin.math.abs(x - seed.x) <= tolerance && kotlin.math.abs(y - seed.y) <= tolerance
}

internal data class PreparedChatPage(
    val webView: WebView,
    val pageReady: Boolean,
    val voiceLocation: PreloadedVoiceLocation?
)
