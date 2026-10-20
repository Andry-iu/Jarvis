package it.jarviswake

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.window.OnBackInvokedDispatcher
import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Button
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.lang.ref.WeakReference
import org.json.JSONObject
import org.json.JSONTokener

/** Persistent, authenticated ChatGPT Web surface used by the experimental voice flow. */
class WebChatActivity : Activity() {
    companion object {
        const val EXTRA_AUTOMATION = "automation"
        private const val CHATGPT_URL = "https://chatgpt.com/g/g-p-6ad5d9f2d9f48191a7c4860050aa43cc-jarvisruntime/project"
        private const val MICROPHONE_REQUEST_CODE = 41
        private val FILL_GREETING_SCRIPT = """
            (() => {
              const state = window.__jarvisGreeting;
              if (state?.submitted) return JSON.stringify({result:'submitted'});
              const capture = window.__jarvisWakeMedia;
              const captureWaiting = capture && (capture.pending || capture.error ||
                (capture.requested && ![...capture.tracks].some(track => track.readyState === 'live')));
              if (captureWaiting) return JSON.stringify({result:'composer_waiting'});
              if (state?.composer) {
                if (!document.contains(state.composer)) return JSON.stringify({result:'composer_lost'});
                const value = (state.composer.value || state.composer.innerText || state.composer.textContent || '').trim();
                return JSON.stringify({result: value === state.message ? 'filled' : 'composer_not_empty'});
              }
              const visible = element => {
                const rect = element.getBoundingClientRect();
                const style = getComputedStyle(element);
                return rect.width > 8 && rect.height > 8 && style.visibility !== 'hidden' &&
                  style.display !== 'none' && style.opacity !== '0' &&
                  rect.bottom > 0 && rect.right > 0 && rect.top < innerHeight && rect.left < innerWidth;
              };
              const candidates = [...document.querySelectorAll(
                'textarea,input[type="text"],[contenteditable="true"],[role="textbox"]'
              )].filter(element => visible(element) && !element.disabled && !element.readOnly &&
                element.getAttribute('aria-disabled') !== 'true' && element.getAttribute('aria-busy') !== 'true');
              const target = candidates.find(element => {
                const label = [element.getAttribute('aria-label'), element.getAttribute('placeholder'),
                  element.getAttribute('data-testid'), element.id].join(' ').toLowerCase();
                return label.includes('message') || label.includes('prompt') || label.includes('messaggio') ||
                  label.includes('chiedi a chatgpt') || label.includes('composer');
              });
              if (!target) return JSON.stringify({result:'composer_missing'});
              const rect = target.getBoundingClientRect();
              const position = [rect.left, rect.top, rect.width, rect.height].join(',');
              const previous = window.__jarvisGreetingCandidate;
              window.__jarvisGreetingCandidate = {target, position};
              if (!previous || previous.target !== target || previous.position !== position)
                return JSON.stringify({result:'composer_waiting'});
              const current = (target.value || target.innerText || target.textContent || '').trim();
              if (current) return JSON.stringify({result:'composer_not_empty'});
              const message = 'ciao jarvis';
              if (target.isContentEditable) {
                // Update the editor DOM without focusing it, which can open Android's keyboard.
                const before = new InputEvent('beforeinput', {
                  bubbles:true, cancelable:true, inputType:'insertText', data:message
                });
                if (!target.dispatchEvent(before)) return JSON.stringify({result:'composer_waiting'});
                target.textContent = message;
                target.dispatchEvent(new InputEvent('input', {
                  bubbles:true, inputType:'insertText', data:message
                }));
              } else {
                const prototype = target instanceof HTMLInputElement
                  ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
                if (!setter) return JSON.stringify({result:'composer_unavailable'});
                setter.call(target, message);
                target.dispatchEvent(new InputEvent('input', {
                  bubbles:true, inputType:'insertText', data:message
                }));
                target.dispatchEvent(new Event('change', {bubbles:true}));
              }
              const value = (target.value || target.innerText || target.textContent || '').trim();
              if (value !== message) return JSON.stringify({result:'composer_unavailable'});
              window.__jarvisGreeting = {composer:target, message, submitted:false};
              return JSON.stringify({result:'filled'});
            })()
        """.trimIndent()
        private val SUBMIT_GREETING_SCRIPT = """
            (() => {
              const state = window.__jarvisGreeting;
              if (state?.submitted) return JSON.stringify({result:'submitted'});
              const capture = window.__jarvisWakeMedia;
              const captureWaiting = capture && (capture.pending || capture.error ||
                (capture.requested && ![...capture.tracks].some(track => track.readyState === 'live')));
              if (captureWaiting) return JSON.stringify({result:'send_not_ready'});
              const composer = state?.composer;
              if (!composer || !document.contains(composer)) return JSON.stringify({result:'composer_lost'});
              const value = (composer.value || composer.innerText || composer.textContent || '').trim();
              if (value !== state.message) return JSON.stringify({result:'composer_not_empty'});
              const visible = element => {
                const r = element.getBoundingClientRect(), style = getComputedStyle(element);
                return r.width > 8 && r.height > 8 && style.visibility !== 'hidden' &&
                  style.display !== 'none' && style.opacity !== '0' &&
                  r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth;
              };
              if (!visible(composer)) return JSON.stringify({result:'send_not_ready'});
              const form = composer.closest('form');
              const labels = new Set(['send', 'send message', 'send prompt', 'invia', 'invia messaggio']);
              const candidates = [...(form || document).querySelectorAll('button,[role="button"]')]
                .filter(element => visible(element) && !element.disabled &&
                  element.getAttribute('aria-disabled') !== 'true' && element.getAttribute('aria-busy') !== 'true')
                .filter(element => {
                  const names = [element.getAttribute('aria-label'), element.getAttribute('title')]
                    .filter(Boolean).map(value => value.trim().toLowerCase());
                  return names.some(name => labels.has(name)) || element.getAttribute('data-testid') === 'send-button';
                });
              if (candidates.length !== 1) return JSON.stringify({result:'send_not_ready'});
              // Commit before click: later callbacks cannot send a second message.
              state.submitted = true;
              candidates[0].click();
              return JSON.stringify({result:'submitted_button'});
            })()
        """.trimIndent()
        private val VOICE_CONTROL_LOCATION_SCRIPT = """
(() => {
  const preferBottom = __JARVIS_LOCKED__;
  // Observe actual streams, including when WebView caches permission grants.
  // No audio or transcript is read; only track lifecycle is inspected.
  if (!window.__jarvisWakeMedia && navigator.mediaDevices?.getUserMedia) {
    const media = { tracks: new Set(), pending: 0, error: null, requested: false };
    const original = navigator.mediaDevices.getUserMedia;
    navigator.mediaDevices.getUserMedia = async function(...args) {
      const audio = !!args[0]?.audio;
      if (audio) { media.pending++; media.error = null; media.requested = true; }
      try {
        const stream = await original.apply(this, args);
        if (audio) stream.getAudioTracks().forEach(track => media.tracks.add(track));
        return stream;
      } catch (error) {
        if (audio) media.error = error.name || 'MediaError';
        throw error;
      } finally {
        if (audio) media.pending--;
      }
    };
    window.__jarvisWakeMedia = media;
  }
  const media = window.__jarvisWakeMedia;
  const mediaActive = media && [...media.tracks].some(track => track.readyState === 'live');
  const outputPlaying = [...document.querySelectorAll('audio,video')].some(element =>
    !element.paused && element.readyState >= 2 && !element.muted && element.volume > 0);
  if (media) [...media.tracks].filter(track => track.readyState === 'ended')
    .forEach(track => media.tracks.delete(track));
  const visible = element => {
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    return rect.width > 8 && rect.height > 8 && style.visibility !== 'hidden' &&
      style.display !== 'none' && style.opacity !== '0' &&
      rect.bottom > 0 && rect.right > 0 && rect.top < innerHeight && rect.left < innerWidth;
  };
  const norm = value => (value || '').toLowerCase().normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').replace(/[^a-z ]/g, ' ')
    .trim().replace(/ +/g, ' ');
  const exact = new Set([
    'start voice mode', 'voice mode', 'open voice mode',
    'start voice conversation', 'start a voice conversation',
    'start voice chat', 'start voice', 'voice',
    'avvia modalita vocale', 'modalita vocale',
    'avvia conversazione vocale', 'avvia una conversazione vocale',
    'avvia chat vocale', 'inizia una conversazione vocale',
    'usa la modalita vocale', 'parla con chatgpt', 'avvia voice', 'voce'
  ]);
  const descriptor = element => {
    const labelledBy = (element.getAttribute('aria-labelledby') || '').trim()
      .split(/\s+/).filter(Boolean)
      .map(id => document.getElementById(id)?.textContent || '').join(' ');
    return norm([
      labelledBy || element.getAttribute('aria-label'), element.getAttribute('title'),
      element.getAttribute('data-testid'), element.innerText,
      ...[...element.querySelectorAll('svg title,[aria-label]')]
        .map(child => child.getAttribute('aria-label') || child.textContent)
    ].filter(Boolean).join(' '));
  };
  const voiceTerm = label => label.includes('voice') || label.includes('vocal');
  const exitTerm = label => ['end', 'stop', 'leave', 'exit', 'close', 'terminate',
    'termina', 'chiudi', 'esci'].some(term => label.split(' ').includes(term));
  const activeHeading = [...document.querySelectorAll('h1,h2,h3,header,[role="heading"]')]
    .filter(visible).map(descriptor)
    .some(label => label === 'voice mode' || label === 'modalita vocale' ||
      label.endsWith(' voice mode') || label.endsWith(' modalita vocale'));
  const activeExitControl = [...document.querySelectorAll(
    'button,[role="button"]'
  )].filter(visible).map(descriptor).some(label => voiceTerm(label) && exitTerm(label));
  const visiblePageText = norm(document.body ? document.body.innerText : '');
  const activePageText = visiblePageText.includes('chatgpt modalita vocale') ||
    visiblePageText.includes('chatgpt voice mode');
  if (mediaActive || activeHeading || activeExitControl || activePageText) {
    return JSON.stringify({ state: 'active', inputTrackLive: !!mediaActive, outputPlaying });
  }
  if (media?.pending) return JSON.stringify({ state: 'opening' });
  const controls = [...document.querySelectorAll(
    'button,[role="button"]'
  )]
    .map(element => {
      const label = descriptor(element);
      const rect = element.getBoundingClientRect();
      const forbidden = /microphon|microfon|dictat|dettat|record|registr|send|invia/.test(label) ||
        /attach|allega|upload|carica|settings|impostazioni|share|condividi|menu/.test(label) ||
        (!!element.getAttribute('aria-haspopup') && element.getAttribute('aria-haspopup') !== 'false') ||
        (voiceTerm(label) && exitTerm(label));
      const voiceWord = voiceTerm(label);
      const score = exact.has(label) ? 4 :
        ([...exact].some(value => label.startsWith(value + ' ')) ? 3 :
        (voiceWord ? 2 : 0));
      return { element, rect, visible: visible(element), forbidden, score };
    });
  const enabled = item => !item.element.disabled &&
    item.element.getAttribute('aria-disabled') !== 'true';
  const reachable = item => {
    const x = item.rect.left + item.rect.width / 2;
    const y = item.rect.top + item.rect.height / 2;
    const hit = document.elementFromPoint(x, y);
    return hit && (hit === item.element || item.element.contains(hit));
  };
  const locationRank = rect => {
    if (!preferBottom) return 0;
    if (rect.top >= innerHeight * 2 / 3) return 0;
    if (rect.bottom <= innerHeight / 3) return 1;
    return 2;
  };
  const semantic = controls
    .filter(item => item.visible && !item.forbidden && item.score > 0 && enabled(item) && reachable(item))
    .sort((a, b) => preferBottom
      ? locationRank(a.rect) - locationRank(b.rect) || b.score - a.score || b.rect.bottom - a.rect.bottom
      : b.score - a.score || a.rect.top - b.rect.top);

  // Mobile ChatGPT has changed the accessible Voice label more than once.
  // Fallback stays inside the empty editor's compact DOM container. Project
  // composers can be at the top; normal chat composers can be at the bottom.
  // While keyguard is showing, inspect bottom, top, then other positions.
  // Never choose an unrelated page/header button.
  const composers = [...document.querySelectorAll('textarea,[contenteditable="true"]')]
    .filter(visible).sort((a, b) => {
      const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
      return preferBottom
        ? locationRank(ar) - locationRank(br) || br.bottom - ar.bottom
        : ar.top - br.top;
    });
  const composerCandidate = () => {
    for (const composer of composers) {
      if ((composer.value || composer.innerText || '').trim() || composer.disabled ||
        composer.getAttribute('aria-disabled') === 'true') continue;
      const editorRect = composer.getBoundingClientRect();
      for (let scope = composer.parentElement; scope && scope !== document.body; scope = scope.parentElement) {
        const box = scope.getBoundingClientRect();
        if (box.height > editorRect.height + 144 || box.width > innerWidth) break;
        const local = controls.filter(item => scope.contains(item.element) && item.visible &&
          item.rect.left >= box.left + box.width / 2 && item.rect.right <= box.right + 1 &&
          item.rect.top >= box.top - 1 && item.rect.bottom <= box.bottom + 1 &&
          item.rect.width >= 28 && item.rect.width <= 96 &&
          item.rect.height >= 28 && item.rect.height <= 96 &&
          Math.abs(item.rect.width - item.rect.height) <= 24)
          .sort((a, b) => b.rect.right - a.rect.right);
        if (!local.length) continue;
        const candidate = local[0];
        // Do not substitute Dictation/Send when Voice is disabled or ambiguous.
        const unique = local.length === 1 || candidate.rect.left >= local[1].rect.right;
        if (unique && !candidate.forbidden && enabled(candidate) && reachable(candidate)) return candidate;
        break;
      }
    }
    return null;
  };
  const selected = semantic[0] || composerCandidate();
  if (!selected) return null;
  const rect = selected.rect;
  return JSON.stringify({
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2,
    viewportWidth: innerWidth,
    viewportHeight: innerHeight,
    strategy: semantic.length ? 'semantic' : 'composer'
  });
})()
        """.trimIndent()
        internal fun preloadVoiceControlScript(): String =
            VOICE_CONTROL_LOCATION_SCRIPT.replace("__JARVIS_LOCKED__", "false")
        @Volatile private var active = WeakReference<WebChatActivity>(null)
        @Volatile private var automationPageReady = false
        fun isAutomationPageReady(): Boolean = active.get()?.let {
            automationPageReady && it.resumed && it.hasWindowFocus() && it.isValidCycle() && !it.webDestroyed
        } == true

        fun isCycleVisible(token: String): Boolean = active.get()?.let {
            it.cycleToken == token && it.isValidCycle() && it.resumed && !it.isFinishing
        } == true

        fun setExternalCoverVisible(token: String, visible: Boolean) {
            active.get()?.takeIf { it.cycleToken == token }?.let {
                it.localHud?.visibility = if (visible) View.GONE else View.VISIBLE
                it.localClose?.visibility = if (visible) View.GONE else View.VISIBLE
                it.updateLockScreenUi()
            }
        }

        fun showStatus(token: String, message: String) {
            active.get()?.takeIf { it.cycleToken == token }?.localHud?.setStatus(message)
        }

        fun showVoiceActive(token: String) {
            active.get()?.takeIf { it.cycleToken == token }?.localHud?.setVoiceActive()
        }

        fun tapVoiceControl(callback: (WebVoiceTapResult) -> Unit) {
            val activity = active.get()
            if (activity == null) callback(WebVoiceTapResult.UNAVAILABLE) else activity.runOnUiThread {
                activity.locateAndTapVoiceControl(callback)
            }
        }

        fun inspectVoiceState(callback: (WebVoicePageState) -> Unit) {
            val activity = active.get()
            if (activity == null) callback(WebVoicePageState.UNAVAILABLE) else activity.runOnUiThread {
                activity.inspectVoiceState(callback)
            }
        }

        fun sendGreetingToVoice(callback: (VoiceGreetingResult) -> Unit) {
            val activity = active.get()
            if (activity == null) callback(VoiceGreetingResult.UNAVAILABLE) else activity.runOnUiThread {
                activity.sendGreetingToVoice(callback)
            }
        }

        fun closeForCycle(token: String) {
            active.get()?.takeIf { it.cycleToken == token }?.let {
                it.releaseWebView()
                it.finish()
            }
        }
    }

    private lateinit var webView: WebView
    private var pendingMicrophoneRequest: PermissionRequest? = null
    private var voiceTapInFlight = false
    private var cycleToken: String? = null
    private var resumed = false
    private var webDestroyed = false
    private var localHud: HolographicOverlayView? = null
    private var localClose: View? = null
    private var localUnlock: Button? = null
    private var unlockPending = false
    private val voiceTouchGate: WebVoiceTouchGate get() = VoiceCycle.touchGate
    private fun isValidCycle() = VoiceCycle.matches(cycleToken) && !isFinishing && !isDestroyed

    private var documentGeneration = 0
    private var lastVoiceCandidateX = Float.NaN
    private var lastVoiceCandidateY = Float.NaN
    private var stableVoiceCandidateCount = 0
    private var voiceReadyLogged = false
    private var voiceInputTrackLogged = false
    private var voiceMediaPlayingLogged = false
    private var preloadedVoiceLocation: PreloadedVoiceLocation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cycleToken = intent.getStringExtra(VoiceCycle.EXTRA_ID)
        if (intent.getBooleanExtra(EXTRA_AUTOMATION, false)) {
            if (!VoiceCycle.matches(cycleToken)) {
                State.setAttempt(this, "ChatGPT richiesto da un ciclo terminato: apertura annullata.")
                finish()
                return
            }
            if (savedInstanceState != null && !voiceTouchGate.canDispatch) {
                VoiceCycle.cancel(this, cycleToken!!,
                    "Android ha ricreato ChatGPT durante Voice. Ciclo chiuso per evitare un secondo tocco.")
                finish()
                return
            }
        }
        if (cycleToken != null) {
            // Show this app's voice surface above keyguard; do not dismiss device security.
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        active = WeakReference(this)
        if (cycleToken != null && Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                VoiceCycle.cancel(this, cycleToken!!, "ChatGPT chiuso. Ascolto Hey Jarvis riattivato.")
            }
        }
        val preparedPage = cycleToken?.let { ChatGptPreloader.handoff(it) }
        automationPageReady = preparedPage?.pageReady ?: false
        preloadedVoiceLocation = preparedPage?.voiceLocation
        State.setAttempt(this, "ChatGPT creato; WebView ${WebView.getCurrentWebViewPackage()?.versionName ?: "sconosciuto"}.")
        webView = (preparedPage?.webView ?: WebView(this)).apply {
            setBackgroundColor(android.graphics.Color.rgb(2, 8, 15))
            alpha = 1f
            isClickable = true
            isFocusable = true
            importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_YES
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    if (webDestroyed || active.get() !== this@WebChatActivity) return
                    automationPageReady = false
                    voiceReadyLogged = false
                    documentGeneration++
                    resetVoiceCandidate()
                    super.onPageStarted(view, url, favicon)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return if (uri.scheme == "https") false else {
                        Toast.makeText(this@WebChatActivity, "Collegamento non consentito: ${uri.scheme}", Toast.LENGTH_LONG).show()
                        true
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (webDestroyed || active.get() !== this@WebChatActivity) return
                    automationPageReady = true
                    if (cycleToken != null && isValidCycle()) DiagnosticLog.record(this@WebChatActivity, DiagnosticLog.Event.WEB_PAGE_READY, cycleToken)
                    localHud?.setStatus("PAGINA CARICATA — PREPARAZIONE VOICE")
                    State.setAttempt(this@WebChatActivity, "Pagina ChatGPT caricata nel WebView.")
                    State.set(this@WebChatActivity, "Pagina Web caricata; attendo i controlli ChatGPT.")
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!webDestroyed && request.isForMainFrame) failLoading("Caricamento ChatGPT fallito: errore ${error.errorCode}.")
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (!webDestroyed && request.isForMainFrame && response.statusCode >= 400)
                        failLoading("Caricamento ChatGPT fallito: HTTP ${response.statusCode}.")
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    releaseWebView()
                    failLoading("Processo WebView terminato da Android; ciclo chiuso.")
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed || webDestroyed ||
                            (cycleToken != null && !isValidCycle())) {
                            request.deny()
                            return@runOnUiThread
                        }
                        val host = request.origin.host.orEmpty().lowercase()
                        val audioRequested = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        if (!isTrustedVoiceHost(host) || !audioRequested) {
                            request.deny()
                            val reason = if (!audioRequested) "risorsa audio non richiesta" else "origine non autorizzata"
                            reportMicrophonePermission("MICROFONO WEB RIFIUTATO: $host ($reason)")
                            return@runOnUiThread
                        }
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            grantMicrophone(request, host)
                        } else {
                            pendingMicrophoneRequest?.let { runCatching { it.deny() } }
                            pendingMicrophoneRequest = request
                            reportMicrophonePermission("RICHIESTA PERMESSO MICROFONO ANDROID")
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST_CODE)
                        }
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    if (pendingMicrophoneRequest === request) pendingMicrophoneRequest = null
                }
            }
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        val root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        if (cycleToken != null) {
            localHud = HolographicOverlayView(this).apply { setStatus("CARICAMENTO CHATGPT") }
            root.addView(localHud, FrameLayout.LayoutParams(-1, -1))
            localClose = CycleUi.closeButton(this) {
                VoiceCycle.cancel(this, cycleToken!!, "ChatGPT chiuso. Ascolto Hey Jarvis riattivato.")
            }
            val density = resources.displayMetrics.density
            root.addView(localClose, FrameLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt(),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (36 * density).toInt() })
            localUnlock = Button(this).apply {
                text = "Sblocca telefono"
                setOnClickListener { requestOptionalUnlock() }
            }
            root.addView(localUnlock, FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (112 * density).toInt() })
            updateLockScreenUi()
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        if (preparedPage == null) {
            val restored = savedInstanceState?.getBundle("web_state")?.let { webView.restoreState(it) }
            if (restored == null || restored.size == 0) webView.loadUrl(CHATGPT_URL)
            else State.setAttempt(this, "WebView ripristinato; attendo caricamento pagina.")
        } else {
            State.setAttempt(this, if (preparedPage.pageReady)
                "WebView del progetto riutilizzato senza ricaricare ChatGPT."
            else "WebView del progetto riutilizzato; attendo il completamento del caricamento.")
        }
        if (!intent.getBooleanExtra(EXTRA_AUTOMATION, false)) {
            Toast.makeText(
                this,
                "Accedi a ChatGPT. JarvisWake userà una nuova chat normale per Voice.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (cycleToken != null && !VoiceCycle.matches(cycleToken)) { releaseWebView(); finish(); return }
        updateLockScreenUi()
        if (cycleToken != null) State.setAttempt(this, "ChatGPT visibile; attendo pagina e controllo Voice.")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateLockScreenUi()
    }

    private fun updateLockScreenUi() {
        if (cycleToken == null) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard.isDeviceLocked || keyguard.isKeyguardLocked
        // Direct Voice dispatch targets WebView itself. Physical touches stay on the
        // cover while locked, so they cannot navigate account pages underneath it.
        localHud?.isClickable = locked
        localUnlock?.visibility = if (locked && localHud?.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun requestOptionalUnlock() {
        if (!isValidCycle() || !resumed || !hasWindowFocus() || unlockPending) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) { updateLockScreenUi(); return }
        unlockPending = true
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = completeUnlock()
            override fun onDismissCancelled() = completeUnlock()
            override fun onDismissError() = completeUnlock()
            private fun completeUnlock() {
                if (!isValidCycle()) return
                unlockPending = false
                updateLockScreenUi()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized && !webDestroyed) {
            val webState = Bundle()
            webView.saveState(webState)
            outState.putBundle("web_state", webState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        resumed = false
        android.webkit.CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_REQUEST_CODE) return
        val pending = pendingMicrophoneRequest ?: return
        pendingMicrophoneRequest = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            grantMicrophone(pending, pending.origin.host.orEmpty().lowercase())
        } else {
            pending.deny()
            reportMicrophonePermission("PERMESSO MICROFONO ANDROID NEGATO")
        }
    }

    private fun isTrustedVoiceHost(host: String): Boolean =
        host == "chatgpt.com" || host.endsWith(".chatgpt.com") || host == "chat.openai.com"

    private fun grantMicrophone(request: PermissionRequest, host: String) {
        runCatching { request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) }
            .onFailure { State.set(this, "Richiesta microfono Web già terminata: $host") }
    }

    private fun reportMicrophonePermission(message: String) {
        State.set(this, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun voiceControlLocationScript(): String {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard.isDeviceLocked || keyguard.isKeyguardLocked
        return VOICE_CONTROL_LOCATION_SCRIPT.replace("__JARVIS_LOCKED__", locked.toString())
    }

    /**
     * The DOM is used only to obtain the visible button coordinates. A native DOWN/UP
     * sequence is then delivered to WebView, so the page receives a real touch path
     * instead of JavaScript's synthetic element.click().
     */
    private fun locateAndTapVoiceControl(callback: (WebVoiceTapResult) -> Unit) {
        if (!isValidCycle() || !resumed || !hasWindowFocus() || webDestroyed) {
            callback(WebVoiceTapResult.UNAVAILABLE)
            return
        }
        if (!WakeService.isMicrophoneReleased()) {
            callback(WebVoiceTapResult.UNAVAILABLE)
            return
        }
        if (!voiceTouchGate.canDispatch) {
            callback(WebVoiceTapResult.BLOCKED)
            return
        }
        if (isFinishing || isDestroyed ||
            voiceTapInFlight || !automationPageReady || !::webView.isInitialized) {
            callback(WebVoiceTapResult.UNAVAILABLE)
            return
        }
        voiceTapInFlight = true
        val generation = documentGeneration
        webView.evaluateJavascript(voiceControlLocationScript()) { rawResult ->
            if (!isValidCycle() || webDestroyed || generation != documentGeneration) {
                voiceTapInFlight = false
                callback(WebVoiceTapResult.UNAVAILABLE)
                return@evaluateJavascript
            }
            val result = runCatching {
                val encoded = JSONTokener(rawResult).nextValue() as? String ?: return@runCatching null
                val value = JSONObject(encoded)
                if (value.optString("state") == "active") {
                    return@runCatching WebVoiceDomResult(active = true)
                }
                val viewportWidth = value.getDouble("viewportWidth").toFloat()
                val viewportHeight = value.getDouble("viewportHeight").toFloat()
                if (viewportWidth <= 0f || viewportHeight <= 0f) return@runCatching null
                WebVoiceDomResult(
                    x = value.getDouble("x").toFloat() * webView.width / viewportWidth,
                    y = value.getDouble("y").toFloat() * webView.height / viewportHeight,
                    semantic = value.optString("strategy") == "semantic"
                )
            }.getOrNull()
            if (result?.active == true) {
                voiceTouchGate.block()
                resetVoiceCandidate()
                voiceTapInFlight = false
                callback(WebVoiceTapResult.ACTIVE)
                return@evaluateJavascript
            }
            if (result == null) {
                resetVoiceCandidate()
                voiceTapInFlight = false
                callback(WebVoiceTapResult.NOT_FOUND)
                return@evaluateJavascript
            }

            if (stableVoiceCandidateCount == 0) {
                val seed = preloadedVoiceLocation
                preloadedVoiceLocation = null
                val seedPoint = seed?.let { PreloadedVoicePolicy.scale(it, webView.width, webView.height) }
                if (seedPoint != null && PreloadedVoicePolicy.matches(seedPoint, result.x, result.y)) {
                        lastVoiceCandidateX = seedPoint.x
                        lastVoiceCandidateY = seedPoint.y
                        stableVoiceCandidateCount = 1
                }
            }
            val agreesWithPrevious = kotlin.math.abs(result.x - lastVoiceCandidateX) <= 8f &&
                kotlin.math.abs(result.y - lastVoiceCandidateY) <= 8f
            stableVoiceCandidateCount = if (agreesWithPrevious) stableVoiceCandidateCount + 1 else 1
            lastVoiceCandidateX = result.x
            lastVoiceCandidateY = result.y
            if (stableVoiceCandidateCount < 2) {
                voiceTapInFlight = false
                callback(WebVoiceTapResult.NOT_FOUND)
                return@evaluateJavascript
            }
            resetVoiceCandidate()
            if (!voiceReadyLogged) {
                voiceReadyLogged = true
                DiagnosticLog.record(this, DiagnosticLog.Event.VOICE_CONTROL_READY, cycleToken)
                DiagnosticLog.record(this, if (result.semantic) DiagnosticLog.Event.VOICE_CONTROL_SEMANTIC
                    else DiagnosticLog.Event.VOICE_CONTROL_COMPOSER, cycleToken)
            }

            // Latch BEFORE dispatch: even an unaccepted touch must never be repeated.
            VoiceCycle.voiceDispatched(cycleToken!!)
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, result.x, result.y, 0
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            val downAccepted = webView.dispatchTouchEvent(down)
            down.recycle()
            webView.postDelayed({
                // Always balance DOWN with UP. Voice can navigate the page between them;
                // abandoning UP would leave the gesture incomplete and trigger a new search.
                if (!isValidCycle() || webDestroyed) {
                    voiceTapInFlight = false
                    callback(WebVoiceTapResult.UNAVAILABLE)
                    return@postDelayed
                }
                val upTime = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(
                    downTime, upTime, MotionEvent.ACTION_UP, result.x, result.y, 0
                ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                webView.dispatchTouchEvent(up)
                up.recycle()
                voiceTapInFlight = false
                callback(
                    if (downAccepted) WebVoiceTapResult.DISPATCHED
                    else WebVoiceTapResult.BLOCKED
                )
            }, 50L)
        }
    }

    private fun inspectVoiceState(callback: (WebVoicePageState) -> Unit) {
        if (!isValidCycle() || !resumed || !hasWindowFocus() || webDestroyed ||
            !automationPageReady || !::webView.isInitialized) {
            callback(WebVoicePageState.UNAVAILABLE)
            return
        }
        val generation = documentGeneration
        webView.evaluateJavascript(voiceControlLocationScript()) { rawResult ->
            if (!isValidCycle() || webDestroyed || generation != documentGeneration) {
                callback(WebVoicePageState.UNAVAILABLE)
                return@evaluateJavascript
            }
            val state = runCatching {
                val encoded = JSONTokener(rawResult).nextValue() as? String
                    ?: return@runCatching WebVoicePageState.NOT_FOUND
                val value = JSONObject(encoded)
                if (value.optString("state") == "active") {
                    if (value.optBoolean("inputTrackLive") && !voiceInputTrackLogged) {
                        voiceInputTrackLogged = true
                        DiagnosticLog.record(this@WebChatActivity, DiagnosticLog.Event.VOICE_INPUT_TRACK_LIVE, cycleToken)
                    }
                    if (value.optBoolean("outputPlaying") && !voiceMediaPlayingLogged) {
                        voiceMediaPlayingLogged = true
                        DiagnosticLog.record(this@WebChatActivity, DiagnosticLog.Event.VOICE_MEDIA_PLAYING, cycleToken)
                    }
                    WebVoicePageState.ACTIVE
                }
                else if (value.has("x") && value.has("y") &&
                    value.optString("strategy") == "semantic") WebVoicePageState.START_READY
                else WebVoicePageState.NOT_FOUND
            }.getOrDefault(WebVoicePageState.NOT_FOUND)
            callback(state)
        }
    }

    private fun sendGreetingToVoice(callback: (VoiceGreetingResult) -> Unit) {
        if (!isValidCycle() || webDestroyed) { callback(VoiceGreetingResult.UNAVAILABLE); return }
        if (!resumed || !hasWindowFocus() || !automationPageReady) {
            callback(VoiceGreetingResult.NOT_READY)
            return
        }
        val generation = documentGeneration
        webView.evaluateJavascript(FILL_GREETING_SCRIPT) { rawResult ->
            if (!isValidCycle() || webDestroyed || generation != documentGeneration) {
                callback(VoiceGreetingResult.UNAVAILABLE)
                return@evaluateJavascript
            }
            when (parseGreetingResult(rawResult)) {
                "submitted" -> callback(VoiceGreetingResult.SENT)
                "filled" -> webView.evaluateJavascript(SUBMIT_GREETING_SCRIPT) { submitRaw ->
                    if (!isValidCycle() || webDestroyed || generation != documentGeneration) {
                        callback(VoiceGreetingResult.UNAVAILABLE)
                        return@evaluateJavascript
                    }
                    callback(when (parseGreetingResult(submitRaw)) {
                        "submitted_button", "submitted" -> VoiceGreetingResult.SENT
                        "send_not_ready" -> VoiceGreetingResult.NOT_READY
                        else -> VoiceGreetingResult.UNAVAILABLE
                    })
                }
                "composer_missing", "composer_waiting", "composer_unavailable" -> callback(VoiceGreetingResult.NOT_READY)
                "composer_not_empty" -> callback(VoiceGreetingResult.COMPOSER_UNAVAILABLE)
                else -> callback(VoiceGreetingResult.UNAVAILABLE)
            }
        }
    }

    private fun parseGreetingResult(rawResult: String?): String = runCatching {
        val encoded = JSONTokener(rawResult).nextValue() as? String ?: return@runCatching "unavailable"
        JSONObject(encoded).optString("result", "unavailable")
    }.getOrDefault("unavailable")

    private fun resetVoiceCandidate() {
        lastVoiceCandidateX = Float.NaN
        lastVoiceCandidateY = Float.NaN
        stableVoiceCandidateCount = 0
    }

    private fun failLoading(message: String) {
        if (cycleToken != null) VoiceCycle.cancel(this, cycleToken!!, message)
        else { State.setAttempt(this, message); Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun releaseWebView() {
        if (webDestroyed || !::webView.isInitialized) return
        webDestroyed = true
        if (active.get() === this) automationPageReady = false
        pendingMicrophoneRequest?.let { runCatching { it.deny() } }
        pendingMicrophoneRequest = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    // API 31-32 fallback only; API 33+ uses OnBackInvokedDispatcher above.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    @Deprecated("Legacy back callback for API 31-32")
    override fun onBackPressed() {
        if (cycleToken != null) VoiceCycle.cancel(this, cycleToken!!, "ChatGPT chiuso. Ascolto Hey Jarvis riattivato.")
        else super.onBackPressed()
    }

    override fun onDestroy() {
        releaseWebView()
        if (active.get() === this) { active.clear(); automationPageReady = false }
        if (!isChangingConfigurations && VoiceCycle.matches(cycleToken)) {
            VoiceCycle.cancel(this, cycleToken!!, "Schermata ChatGPT chiusa. Ascolto Hey Jarvis riattivato.")
        }
        super.onDestroy()
    }

}

private data class WebVoiceDomResult(
    val x: Float = 0f,
    val y: Float = 0f,
    val active: Boolean = false,
    val semantic: Boolean = false
)

enum class WebVoiceTapResult { DISPATCHED, BLOCKED, ACTIVE, NOT_FOUND, UNAVAILABLE }
enum class WebVoicePageState { ACTIVE, START_READY, NOT_FOUND, UNAVAILABLE }
enum class VoiceGreetingResult { SENT, NOT_READY, COMPOSER_UNAVAILABLE, UNAVAILABLE }
