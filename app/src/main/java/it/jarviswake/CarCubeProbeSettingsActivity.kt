package it.jarviswake

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup for the real, separately packaged parked game. No automatic launch. */
class CarCubeProbeSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CarCubeProbe.disarm()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(body) })
        fun label(value: String) = TextView(this).apply { text = value; textSize = 18f; setPadding(0,12,0,12); body.addView(this) }
        label("Jarvis Cube · gioco 3D per Android Auto")
        label("Installa sul telefono anche Jarvis Cube .123, firmato come JarvisWake. Non installare nulla sull’Audi e non disinstallare JarvisWake per aggiornarlo.")
        label("Ad auto parcheggiata apri il menu delle app di Android Auto e scegli Jarvis Cube. Trascina il cubo per allineare la faccia richiesta e premi Conferma: ogni allineamento corretto aggiunge un punto.")
        label("Quando il gioco è già aperto, la cover di Jarvis lo colora di celeste; il mute lo rende grigio e ferma la rotazione. La chiusura della cover lascia il gioco autonomo. Nessuna chiamata, audio o testo della chat viene passato al gioco.")
        label("Per un APK di prova può servire Origini sconosciute nelle impostazioni sviluppatore di Android Auto, sul telefono. Se l’icona non compare, questo è un esito da diagnosticare: il collegamento con Maps non prova che il gioco sia disponibile.")
        label("L’apertura automatica sopra Maps non è abilitata da questa versione. La compatibilità e la visualizzazione sull’Audi richiedono la prova reale.")
        val result = label("")
        body.addView(Button(this).apply {
            text = "Apri anteprima sul telefono"
            setOnClickListener {
                result.text = runCatching {
                    if (packageManager.checkSignatures(packageName, "it.jarviswake.cube") != PackageManager.SIGNATURE_MATCH) "Jarvis Cube assente o firma diversa: installa l’APK del gioco fornito con questa versione."
                    else {
                        startActivity(Intent().setComponent(ComponentName("it.jarviswake.cube", "it.jarviswake.cube.CubeGameActivity")))
                        "Anteprima richiesta sul telefono; nessun avvio sull’auto."
                    }
                }.getOrDefault("Jarvis Cube non disponibile sul telefono.")
            }
        })
        body.addView(Button(this).apply { text = "Indietro"; setOnClickListener { finish() } })
    }
}
