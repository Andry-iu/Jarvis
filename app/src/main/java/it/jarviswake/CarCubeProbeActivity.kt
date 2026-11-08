package it.jarviswake

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import java.io.ByteArrayInputStream

/** Only an already-armed in-process dispatch can animate; direct exported launches fail closed. */
class CarCubeProbeActivity : Activity() {
    private var web: WebView? = null
    private var resumed = false
    private var frameSeen = false
    private var lastMuted: Boolean? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 35 || launchedFromUid != Process.myUid() || !CarCubeProbe.accepts(this)) {
            finish(); return
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val browser = WebView(this)
        web = browser
        browser.setBackgroundColor(Color.BLACK)
        browser.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
            mediaPlaybackRequiresUserGesture = true
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (request.url.toString() == ASSET) null else WebResourceResponse(
                    "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            override fun onPageFinished(view: WebView, url: String) {
                if (url != ASSET) { finish(); return }
                updateRuntime()
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.sourceId() == ASSET && message.message() == "jw_cube_frame") {
                    frameSeen = true
                    if (resumed) CarCubeProbe.firstFrame(this@CarCubeProbeActivity)
                }
                return true // Never forward arbitrary console text to diagnostics.
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "Chiudi prova"; setOnClickListener { finish() } })
        setContentView(root)
        browser.loadUrl(ASSET)
    }

    override fun onResume() {
        super.onResume()
        if (web == null || !CarCubeProbe.accepts(this)) { finish(); return }
        resumed = true
        web?.onResume()
        CarCubeProbe.resumed(this)
        updateRuntime()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        updateRuntime()
        if (hasFocus && resumed && frameSeen) CarCubeProbe.firstFrame(this)
    }

    fun setMuted(value: Boolean) {
        if (lastMuted == value) return
        lastMuted = value
        updateRuntime()
    }

    private fun updateRuntime() {
        val running = resumed && hasWindowFocus() && CarCubeProbe.accepts(this)
        val muted = CarCubeProbe.microphoneMuted()
        web?.evaluateJavascript("window.JarvisCube&&window.JarvisCube.update($running,$muted)", null)
    }

    override fun onPause() {
        resumed = false
        updateRuntime()
        web?.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // User/host departure is terminal. Never bring it back in the same VoiceCycle.
        CarCubeProbe.closed(this)
        finish()
    }

    override fun onDestroy() {
        CarCubeProbe.closed(this)
        web?.stopLoading()
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object { private const val ASSET = "file:///android_asset/car-cube.html" }
}
