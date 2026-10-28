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
    private val waitingAudioRequest = 5
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var attemptStatus: TextView
    private lateinit var coverButton: Button
    private lateinit var greetingInput: EditText
    private lateinit var projectUrlInput: EditText
    private lateinit var waitingAudioName: TextView
    private lateinit var contentRoot: FrameLayout
    private lateinit var homeScreen: ScrollView
    private var chatSettingsLayer: FrameLayout? = null
    private var settingsBackCallback: OnBackInvokedCallback? = null
    private val update = object : Runnable {
        override fun run() {
            status.text = (if (State.running) "● " else "○ ") + State.get(this@MainActivity)
            attemptStatus.text = State.getAttempt(this@MainActivity)
            if (::waitingAudioName.isInitialized) updateWaitingAudioName()
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
        layout.addView(Switch(this).apply {
            text = getString(R.string.waiting_audio_enabled)
            setTextColor(Color.WHITE)
            isChecked = WaitingAudioSettings.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                WaitingAudioSettings.setEnabled(this@MainActivity, checked)
            }
        })
        waitingAudioName = text("", 13f)
        updateWaitingAudioName()
        button(getString(R.string.waiting_audio_choose)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }, waitingAudioRequest)
        }
        text(getString(R.string.waiting_audio_description), 13f)
        val audioVolumeLabel = text(getString(R.string.waiting_audio_volume,
            WaitingAudioSettings.volume(this)), 16f)
        layout.addView(SeekBar(this).apply {
            id = View.generateViewId()
            audioVolumeLabel.labelFor = id
            max = 100
            progress = WaitingAudioSettings.volume(this@MainActivity)
            contentDescription = audioVolumeLabel.text
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    audioVolumeLabel.text = getString(R.string.waiting_audio_volume, value)
                    seekBar.contentDescription = audioVolumeLabel.text
                    if (fromUser) {
                        WaitingAudioSettings.setVolume(this@MainActivity, value)
                        WaitingAudio.preview(this@MainActivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(-1, -2))
        text(getString(R.string.waiting_audio_volume_preview), 13f)
        val audioMinimumLabel = text(getString(R.string.waiting_audio_minimum,
            WaitingAudioSettings.minimumSeconds(this)), 16f)
        layout.addView(SeekBar(this).apply {
            id = View.generateViewId()
            audioMinimumLabel.labelFor = id
            max = WaitingAudioMinimum.MAX_SECONDS
            progress = WaitingAudioSettings.minimumSeconds(this@MainActivity)
            contentDescription = audioMinimumLabel.text
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    audioMinimumLabel.text = getString(R.string.waiting_audio_minimum, value)
                    seekBar.contentDescription = audioMinimumLabel.text
                    if (fromUser) WaitingAudioSettings.setMinimumSeconds(this@MainActivity, value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(-1, -2))
        text(getString(R.string.waiting_audio_minimum_description), 13f)
        button("Avvia ascolto Hey Jarvis") { requestStart() }
        button("Ferma ascolto") {
            startService(Intent(this, WakeService::class.java).setAction("STOP"))
        }
        button("Imposta Jarvis come assistente del telefono") { requestAssistantRole() }
        val destination = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val webOption = RadioButton(this).apply { id = View.generateViewId(); text = "ChatGPT nel WebView" }
        val nativeOption = RadioButton(this).apply { id = View.generateViewId(); text = "App ChatGPT del telefono (shortcut)" }
        destination.addView(webOption)
        destination.addView(nativeOption)
        destination.check(if (NativeChatSettings.enabled(this)) nativeOption.id else webOption.id)
        destination.setOnCheckedChangeListener { _, checked ->
            if (VoiceCycle.active) {
                destination.check(if (NativeChatSettings.enabled(this)) nativeOption.id else webOption.id)
                Toast.makeText(this, "Termina prima il ciclo attivo.", Toast.LENGTH_SHORT).show()
            } else NativeChatSettings.enable(this, checked == nativeOption.id)
        }
        layout.addView(destination)
        button("Seleziona collegamento ChatGPT") { selectNativeShortcut() }
        button("Prova collegamento app ChatGPT (senza Voice)") {
            if (VoiceCycle.active) Toast.makeText(this, "Termina prima il ciclo attivo.", Toast.LENGTH_SHORT).show()
            else runCatching { NativeChatSettings.launch(this) }.onFailure {
                Toast.makeText(this, it.message ?: "Collegamento non disponibile.", Toast.LENGTH_LONG).show()
            }
        }
        button("Configura accesso a ChatGPT") {
            if (NativeChatSettings.enabled(this)) {
                runCatching { NativeChatSettings.launch(this) }.onFailure {
                    Toast.makeText(this, it.message ?: "Collegamento non disponibile.", Toast.LENGTH_LONG).show()
                }
            } else {
                ChatGptPreloader.discard()
                startActivity(Intent(this, WebChatActivity::class.java))
            }
        }
        button("Imposta modello e livello ChatGPT") { openChatSettings() }
        button(getString(R.string.chatgpt_webview_clear_cache)) {
            if (ChatGptPreloader.clearResourceCache(this, contentRoot)) {
                Toast.makeText(this, getString(R.string.chatgpt_webview_cache_cleared), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.chatgpt_webview_cache_clear_failed), Toast.LENGTH_SHORT).show()
            }
        }
        button(getString(R.string.chatgpt_webview_clear_data)) {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.chatgpt_webview_clear_data_title)
                .setMessage(R.string.chatgpt_webview_clear_data_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chatgpt_webview_clear_data_confirm) { _, _ ->
                    val accepted = ChatGptPreloader.clearChatGptData(this, contentRoot) { success ->
                        val message = if (success) R.string.chatgpt_webview_data_cleared
                            else R.string.chatgpt_webview_data_clear_failed
                        Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
                    }
                    if (!accepted) {
                        Toast.makeText(this, getString(R.string.chatgpt_webview_data_clear_failed), Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        }
        text(getString(R.string.chatgpt_project_url_label), 16f)
        projectUrlInput = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setHorizontallyScrolling(true)
            hint = getString(R.string.chatgpt_project_url_hint)
            setText(ChatGptProjectSettings.getUrl(this@MainActivity))
            contentDescription = "Indirizzo del progetto ChatGPT usato da Jarvis"
        }
        layout.addView(projectUrlInput, LinearLayout.LayoutParams(-1, -2))
        text(getString(R.string.chatgpt_project_url_description), 13f)
        button(getString(R.string.chatgpt_project_url_save)) { saveChatGptProjectUrl() }
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

    private fun selectNativeShortcut() {
        if (VoiceCycle.active) {
            Toast.makeText(this, "Termina prima il ciclo attivo.", Toast.LENGTH_LONG).show()
            return
        }
        val shortcuts = try { NativeChatSettings.shortcuts(this) } catch (e: RuntimeException) {
            android.app.AlertDialog.Builder(this).setMessage(e.message ?: "Shortcut non accessibili.")
                .setPositiveButton("OK", null).show()
            return
        }
        if (shortcuts.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setMessage("ChatGPT non espone collegamenti accessibili a Jarvis. Crea il collegamento al progetto nell'app ChatGPT e verifica che Jarvis sia l'assistente selezionato. Nessun altro collegamento verrà aperto al suo posto.")
                .setPositiveButton("OK", null).show()
            return
        }
        android.app.AlertDialog.Builder(this).setTitle("Collegamento al progetto ChatGPT")
            .setItems(shortcuts.map { it.shortLabel?.toString().orEmpty() }.toTypedArray()) { _, index ->
                val selected = shortcuts[index]
                val projectName = EditText(this).apply {
                    setSingleLine(true)
                    setText(NativeChatSettings.project(this@MainActivity))
                    hint = "Nome esatto del progetto nella pagina ChatGPT"
                }
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Conferma nome progetto")
                    .setMessage("Jarvis avvierà Voice soltanto quando questo nome è visibile nella pagina ChatGPT. Il collegamento selezionato rimane nell'app nativa.")
                    .setView(projectName).setNegativeButton("Annulla", null).setPositiveButton("Salva", null).create()
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (projectName.text.toString().isBlank()) projectName.error = "Inserisci il nome del progetto"
                        else {
                            NativeChatSettings.select(this, selected, projectName.text.toString())
                            Toast.makeText(this, "Collegamento salvato. Seleziona App ChatGPT per usarlo alla wake.", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }.setNegativeButton("Annulla", null).show()
    }

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
        if (NativeChatSettings.enabled(this)) return
        if (::contentRoot.isInitialized) ChatGptPreloader.prepare(this, contentRoot)
    }

    private fun saveChatGptProjectUrl() {
        val url = projectUrlInput.text?.toString().orEmpty().trim()
        if (!ChatGptProjectSettings.isValidProjectUrl(url)) {
            projectUrlInput.error = getString(R.string.chatgpt_project_url_invalid)
            return
        }
        val changed = url != ChatGptProjectSettings.getUrl(this)
        if (!ChatGptProjectSettings.save(this, url)) return
        projectUrlInput.error = null
        if (changed) {
            ChatGptPreloader.discard()
            prepareChatGpt()
        }
        Toast.makeText(
            this,
            if (changed) getString(R.string.chatgpt_project_url_saved_changed)
            else getString(R.string.chatgpt_project_url_saved),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openChatSettings() {
        WaitingAudio.stopPreview()
        if (NativeChatSettings.enabled(this)) {
            if (!VoiceCycle.active) runCatching { NativeChatSettings.launch(this) }.onFailure {
                Toast.makeText(this, it.message ?: "Collegamento non disponibile.", Toast.LENGTH_LONG).show()
            }
            return
        }
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
        if (requestCode == waitingAudioRequest) {
            if (resultCode == RESULT_OK) data?.data?.let { uri ->
                Toast.makeText(this, R.string.waiting_audio_importing, Toast.LENGTH_SHORT).show()
                WaitingAudioSettings.import(this, uri) { success ->
                    if (!isDestroyed && !isFinishing) {
                        updateWaitingAudioName()
                        Toast.makeText(this, if (success) R.string.waiting_audio_saved
                            else R.string.waiting_audio_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }
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

    private fun updateWaitingAudioName() {
        waitingAudioName.text = if (WaitingAudioSettings.file(this).isFile)
            getString(R.string.waiting_audio_file, WaitingAudioSettings.name(this)
                ?: getString(R.string.waiting_audio_selected))
        else getString(R.string.waiting_audio_none)
    }

    override fun onResume() {
        super.onResume()
        updateCoverButton()
        if (::contentRoot.isInitialized && !VoiceCycle.active) prepareChatGpt()
        handler.post(update)
    }
    override fun onPause() {
        WaitingAudio.stopPreview()
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
