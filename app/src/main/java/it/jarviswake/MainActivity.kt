package it.jarviswake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.role.RoleManager
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.*
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    companion object {
        @Volatile private var instance = java.lang.ref.WeakReference<MainActivity>(null)

        /** Uses an already-existing activity; never launches UI from the background. */
        fun preloadForScreenOn(): Boolean {
            val activity = instance.get() ?: return false
            if (!WakeScreenPolicy.shouldPreloadForScreenOn(
                    mainActivityAvailable = true, voiceCycleActive = VoiceCycle.active))
                return false
            activity.handler.post {
                if (!activity.isFinishing && !activity.isDestroyed &&
                    WakeScreenPolicy.shouldPreloadForScreenOn(true, VoiceCycle.active) &&
                    activity::contentRoot.isInitialized) {
                    activity.prepareChatGpt()
                }
            }
            return true
        }
    }

    private val assistantPermissionRequest = 2
    private val assistantRoleRequest = 3
    private val diagnosticsExportRequest = 4
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var attemptStatus: TextView
    private lateinit var coverButton: Button
    private lateinit var greetingInput: EditText
    private lateinit var contentRoot: FrameLayout
    private lateinit var homeScreen: ScrollView
    private var chatSettingsLayer: FrameLayout? = null
    private var settingsBackCallback: OnBackInvokedCallback? = null
    private val update = object : Runnable {
        override fun run() {
            status.text = (if (State.running) "● " else "○ ") + State.get(this@MainActivity)
            attemptStatus.text = State.getAttempt(this@MainActivity)
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = java.lang.ref.WeakReference(this)
        JarvisRecognitionService.rememberSystemRecognizer(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.rgb(13, 24, 36))
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(32 + bars.left, 32 + bars.top, 32 + bars.right, 24 + bars.bottom); insets
            }
        }
        fun text(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0, 12, 0, 20); layout.addView(this)
        }
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        text("J.A.R.V.I.S.", 28f)
        text(getString(R.string.main_subtitle, appVersionName()), 13f)
        status = text("", 17f)
        attemptStatus = text("", 13f)
        button("Avvia ciclo completo: ChatGPT Voice") { startVoiceCycle() }
        button("Avvia ascolto Hey Jarvis") { requestStart() }
        button("Ferma ascolto") {
            startService(Intent(this, WakeService::class.java).setAction("STOP"))
        }
        button("Imposta Jarvis come assistente del telefono") { requestAssistantRole() }
        button("Configura accesso a ChatGPT") {
            ChatGptPreloader.discard()
            startActivity(Intent(this, WebChatActivity::class.java))
        }
        button("Imposta modello e livello ChatGPT") { openChatSettings() }
        text("Frase da inviare dopo l’attivazione di Voice", 16f)
        greetingInput = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = VoiceGreetingSettings.DEFAULT_PHRASE
            setText(VoiceGreetingSettings.get(this@MainActivity))
            contentDescription = "Frase inviata all’attivazione di Voice"
        }
        layout.addView(greetingInput, LinearLayout.LayoutParams(-1, -2))
        text("La frase salvata viene usata alla prossima attivazione. Lascia il campo vuoto per mantenere quella attuale.", 13f)
        button("Salva frase") {
            if (VoiceGreetingSettings.save(this, greetingInput.text.toString())) {
                greetingInput.setText(VoiceGreetingSettings.get(this))
                Toast.makeText(this, "Frase salvata.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Inserisci una frase prima di salvare.", Toast.LENGTH_SHORT).show()
            }
        }
        button("Ripristina «ciao jarvis»") {
            VoiceGreetingSettings.reset(this)
            greetingInput.setText(VoiceGreetingSettings.get(this))
            Toast.makeText(this, "Frase predefinita ripristinata.", Toast.LENGTH_SHORT).show()
        }
        button("Abilita automazione Voice") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Consenti HUD sopra le altre app") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        coverButton = Button(this).apply {
            setOnClickListener {
                CoverSettings.setOpaque(this@MainActivity, !CoverSettings.isOpaque(this@MainActivity))
                updateCoverButton()
            }
        }
        layout.addView(coverButton)
        updateCoverButton()
        button("Esporta diagnostica") { exportDiagnostics() }
        button("Permessi e batteria") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        text("Jarvis riconosce ‘Hey Jarvis’ sul telefono e avvia una nuova chat ChatGPT con Voice. Per iniziare, accedi a ChatGPT e abilita i due permessi richiesti per l’automazione: Accessibilità e HUD.", 14f)
        text("Per l’ascolto al riavvio, imposta Jarvis come assistente predefinito. Questa scelta sostituisce l’assistente attuale per i richiami di sistema.", 14f)
        contentRoot = FrameLayout(this)
        homeScreen = ScrollView(this).apply { addView(layout) }
        contentRoot.addView(homeScreen,
            FrameLayout.LayoutParams(-1, -1))
        setContentView(contentRoot)
        handler.post { requestStart() }
    }

    private fun appVersionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""

    private fun requestStart() {
        if (State.running) {
            prepareChatGpt()
            WakeService.resumeExisting()
            return
        }
        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing += Manifest.permission.POST_NOTIFICATIONS
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        else startListening()
    }
    private fun startListening() {
        if (Build.VERSION.SDK_INT >= 33 && !getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) {
            State.set(this, "Abilita le notifiche: Android le richiede per il servizio microfono continuo."); return
        }
        try {
            prepareChatGpt()
            startForegroundService(Intent(this, WakeService::class.java))
        }
        catch (e: RuntimeException) { State.set(this, "Avvio rifiutato: ${e.javaClass.simpleName}") }
    }

    private fun prepareChatGpt() {
        if (::contentRoot.isInitialized) ChatGptPreloader.prepare(this, contentRoot)
    }

    private fun openChatSettings() {
        if (chatSettingsLayer != null) return
        if (VoiceCycle.active) {
            Toast.makeText(this, "Termina prima il ciclo Voice per modificare la chat.", Toast.LENGTH_SHORT).show()
            return
        }
        ChatGptPreloader.prepare(this, contentRoot)
        val layer = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(2, 8, 15))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        if (!ChatGptPreloader.showForConfiguration(layer)) {
            Toast.makeText(this, "La pagina ChatGPT non è pronta. Riprova tra poco.", Toast.LENGTH_SHORT).show()
            return
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(12), dp(4))
            setBackgroundColor(Color.rgb(13, 24, 36))
            elevation = dp(6).toFloat()
        }
        toolbar.addView(Button(this).apply {
            text = "‹ Jarvis"
            contentDescription = "Torna alla schermata Jarvis mantenendo aperta la sessione ChatGPT"
            setOnClickListener { closeChatSettings() }
        }, LinearLayout.LayoutParams(-2, -2))
        toolbar.addView(TextView(this).apply {
            text = "Modello e livello ChatGPT"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Impostazioni ChatGPT"
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(12) })
        layer.addView(toolbar, FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP))
        homeScreen.visibility = View.GONE
        contentRoot.addView(layer, FrameLayout.LayoutParams(-1, -1))
        chatSettingsLayer = layer
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { closeChatSettings() }
            settingsBackCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        }
        State.setAttempt(this, "Impostazioni aperte nel WebView usato dal ciclo wake; torna a Jarvis senza chiudere la pagina.")
    }

    private fun closeChatSettings() {
        val layer = chatSettingsLayer ?: return
        if (Build.VERSION.SDK_INT >= 33) settingsBackCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        settingsBackCallback = null
        if (!ChatGptPreloader.hideConfiguration(contentRoot)) {
            Toast.makeText(this, "Non riesco a ripristinare la pagina ChatGPT; lascio la schermata aperta.", Toast.LENGTH_SHORT).show()
            return
        }
        contentRoot.removeView(layer)
        chatSettingsLayer = null
        homeScreen.visibility = View.VISIBLE
        State.setAttempt(this, "Pagina ChatGPT mantenuta nel preloader; il prossimo ciclo wake riutilizza questo WebView.")
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (chatSettingsLayer != null) closeChatSettings() else super.onBackPressed()
    }

    private fun startVoiceCycle() {
        VoiceCycle.start(this)
    }

    private fun requestAssistantRole() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            val missing = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                missing += Manifest.permission.RECORD_AUDIO
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                missing += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(missing.toTypedArray(), assistantPermissionRequest)
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            State.set(this, "Questo dispositivo non espone la selezione di un assistente personalizzato.")
            return
        }
        JarvisRecognitionService.rememberSystemRecognizer(this)
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), assistantRoleRequest)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == assistantPermissionRequest) {
            val accepted = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
            if (accepted) requestAssistantRole()
            else State.set(this, "Microfono e notifiche servono prima di configurare l’assistente Jarvis.")
            return
        }
        if (code == 1 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) startListening()
        else State.set(this, "Microfono e notifiche richiesti per questa prova.")
    }
    @Deprecated("Android RoleManager returns its selection through an activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == diagnosticsExportRequest) {
            if (resultCode == RESULT_OK) data?.data?.let { writeDiagnostics(it) }
            return
        }
        if (requestCode != assistantRoleRequest) return
        val roles = getSystemService(RoleManager::class.java)
        when {
            roles.isRoleHeld(RoleManager.ROLE_ASSISTANT) -> {
                State.set(this, "Jarvis è l’assistente predefinito. Avvio l’ascolto wake word.")
                startListening()
            }
            else -> State.set(this, "Android non ha assegnato a Jarvis il ruolo assistente. Riprova dalle impostazioni Assistente digitale.")
        }
    }
    private fun exportDiagnostics() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "jarvis-diagnostics.zip")
        }
        startActivityForResult(intent, diagnosticsExportRequest)
    }

    private fun writeDiagnostics(uri: Uri) {
        DiagnosticLog.afterPendingWrites {
            val result = runCatching {
                val files = java.io.File(filesDir, "diagnostics").listFiles()
                    ?.filter { it.isFile && it.name.matches(Regex("events-[0-9]+\\.log")) }
                    ?.sortedBy { it.name }.orEmpty()
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    java.util.zip.ZipOutputStream(output).use { zip ->
                        for (file in files) {
                            zip.putNextEntry(java.util.zip.ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Destinazione non disponibile")
            }
            runOnUiThread {
                State.set(this, if (result.isSuccess) "Diagnostica esportata." else
                    "Esportazione diagnostica non riuscita: ${result.exceptionOrNull()?.javaClass?.simpleName}")
            }
        }
    }

    private fun updateCoverButton() {
        coverButton.text = if (CoverSettings.isOpaque(this)) {
            "Copertura: opaca\nPassa a semitrasparente"
        } else {
            "Copertura: semitrasparente\nPassa a opaca"
        }
    }

    override fun onResume() {
        super.onResume()
        updateCoverButton()
        if (::contentRoot.isInitialized && !VoiceCycle.active) prepareChatGpt()
        handler.post(update)
    }
    override fun onPause() {
        handler.removeCallbacks(update)
        if (VoiceCycle.active && chatSettingsLayer != null) closeChatSettings()
        super.onPause()
    }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) settingsBackCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        if (!isChangingConfigurations) ChatGptPreloader.discard()
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }
}
