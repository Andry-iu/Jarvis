package it.jarviswake

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONTokener
import java.lang.ref.WeakReference

/** Warms the authenticated ChatGPT project page and DOM while Vosk listens. It never taps Voice. */
internal object ChatGptPreloader {
    private const val PROJECT_URL =
        "https://chatgpt.com/g/g-p-6ad5d9f2d9f48191a7c4860050aa43cc-jarvisruntime/project"
    private val main = Handler(Looper.getMainLooper())
    private var host = WeakReference<FrameLayout>(null)
    private var web = WeakReference<WebView>(null)
    private var app: Context? = null
    private var pageReady = false
    private var stopped = false
    private var polls = 0
    private val poll = object : Runnable {
        override fun run() {
            val view = web.get() ?: return
            if (stopped || !pageReady || polls++ >= 24) return
            view.evaluateJavascript(WebChatActivity.preloadVoiceControlScript()) { raw ->
                if (stopped || web.get() !== view) return@evaluateJavascript
                val located = runCatching {
                    val encoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching false
                    val result = org.json.JSONObject(encoded)
                    result.has("x") && result.has("y") &&
                        result.optString("strategy") in setOf("semantic", "composer")
                }.getOrDefault(false)
                app?.let { context ->
                    if (located) {
                        DiagnosticLog.record(context, DiagnosticLog.Event.WEB_PRELOAD_VOICE_FOUND)
                        return@evaluateJavascript
                    }
                }
                main.postDelayed(this, 250L)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun prepare(context: Context, container: FrameLayout) {
        if (web.get() != null) return
        app = context.applicationContext
        host = WeakReference(container)
        stopped = false
        pageReady = false
        polls = 0
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
            container.addView(view, FrameLayout.LayoutParams(-1, -1))
            DiagnosticLog.record(context, DiagnosticLog.Event.WEB_PRELOAD_STARTED)
            view.loadUrl(PROJECT_URL)
        } catch (_: RuntimeException) {
            DiagnosticLog.record(context, DiagnosticLog.Event.WEB_PRELOAD_FAILED)
            discard()
        }
    }

    /** Keep the warmed HTTP/WebView cache, but release its renderer before opening the live page. */
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
        host.clear()
        pageReady = false
        app?.let { DiagnosticLog.record(it, DiagnosticLog.Event.WEB_PRELOAD_DISCARDED) }
    }
}
