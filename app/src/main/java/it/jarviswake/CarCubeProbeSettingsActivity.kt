package it.jarviswake

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CarCubeProbeSettingsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var messageOverride: String? = null
    private val refresh = object : Runnable {
        override fun run() { status.text = messageOverride ?: CarCubeProbe.status; handler.postDelayed(this, 500L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeCarConnection.start(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(body) })
        fun text(value: String) = TextView(this).apply {
            text = value; textSize = 18f; setPadding(0, 12, 0, 12); body.addView(this)
        }
        fun button(label: String, action: () -> Unit) {
            body.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        text("Cubo 3D · prova Android Auto")
        text("Solo ad auto parcheggiata. La prova dura 60 secondi e richiede il telefono sbloccato. " +
            "All’apertura della cover Jarvis richiede il display scelto. Android Auto può rifiutarlo; " +
            "questa schermata non certifica la compatibilità con l’Audi.")
        status = text(CarCubeProbe.status)
        button("Auto ferma · abilita prova 60 s") {
            val targets = CarCubeProbe.targets(this)
            if (targets.isEmpty()) {
                messageOverride = "Android Auto non espone un display secondario autorizzato. " +
                    "Collega il telefono, attendi la connessione e riprova. Nessun avvio è stato eseguito."
                status.text = messageOverride
                AlertDialog.Builder(this).setMessage(status.text).setPositiveButton("OK", null).show()
            } else {
                AlertDialog.Builder(this).setTitle("Scegli il display di prova")
                    .setItems(targets.map { "Display $it" }.toTypedArray()) { _, index ->
                        messageOverride = null
                        CarCubeProbe.arm(this, targets[index]); status.text = CarCubeProbe.status
                    }.setNegativeButton("Annulla", null).show()
            }
        }
        button("Disattiva e chiudi cubo") { messageOverride = null; CarCubeProbe.disarm(); status.text = CarCubeProbe.status }
        button("Indietro") { finish() }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
}
