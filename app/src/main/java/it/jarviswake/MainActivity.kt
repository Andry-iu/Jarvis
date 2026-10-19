package it.jarviswake

import android.Manifest
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
import android.widget.*

class MainActivity : Activity() {
    private val assistantPermissionRequest = 2
    private val assistantRoleRequest = 3
    private val diagnosticsExportRequest = 4
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var attemptStatus: TextView
    private lateinit var coverButton: Button
    private val update = object : Runnable {
        override fun run() {
            status.text = (if (State.running) "● " else "○ ") + State.get(this@MainActivity)
            attemptStatus.text = State.getAttempt(this@MainActivity)
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            startActivity(Intent(this, WebChatActivity::class.java))
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
        setContentView(ScrollView(this).apply { addView(layout) })
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
        try { startForegroundService(Intent(this, WakeService::class.java)) }
        catch (e: RuntimeException) { State.set(this, "Avvio rifiutato: ${e.javaClass.simpleName}") }
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
        Thread({
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
        }, "JarvisDiagnosticExport").start()
    }

    private fun updateCoverButton() {
        coverButton.text = if (CoverSettings.isOpaque(this)) {
            "Copertura: opaca\nPassa a semitrasparente"
        } else {
            "Copertura: semitrasparente\nPassa a opaca"
        }
    }

    override fun onResume() { super.onResume(); updateCoverButton(); handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update); super.onPause() }
}
