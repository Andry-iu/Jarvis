package it.jarviswake

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Genuine, expiring linked-device QR, displayed only in this non-exported secure Activity. */
class WhatsAppQrActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var state:TextView
    private lateinit var notice:TextView
    private lateinit var generate:android.widget.Button
    private lateinit var reconnect:android.widget.Button
    private lateinit var image:ImageView
    private var lastQr=""
    private val update=object:Runnable { override fun run() { showState();handler.postDelayed(this,1000) } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);window.setDecorFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val menu=SplitMenuScreen(this,"Collega WhatsApp","Collega il tuo account come dispositivo aggiuntivo tramite QR.") { finish() }
        state=menu.text("Preparazione collegamento WhatsApp…")
        notice=menu.text("")
        image=ImageView(this).apply { contentDescription="QR di collegamento WhatsApp";adjustViewBounds=true;visibility=View.GONE }
        menu.addControl(image)
        generate=menu.button("Genera QR WhatsApp","Genera un QR reale da scansionare in WhatsApp → Dispositivi collegati. Il codice cambia alla scadenza. Non inviarlo nelle chat.") { notice.text="";PhoneWhatsApp.connect(this,true) }
        reconnect=menu.button("Riconnetti WhatsApp","Riprende la sessione già collegata e conservata sul telefono. Non crea una nuova sessione se non è stato eseguito l’abbinamento.") { notice.text="";PhoneWhatsApp.connect(this,false) }
        menu.button("Scollega WhatsApp","Chiede conferma per scollegare il dispositivo JarvisWake dal tuo WhatsApp. Non cancella il collegamento JarvisWake Link o gli appunti.") {
            AlertDialog.Builder(this).setTitle("Scollegare WhatsApp?").setMessage("JarvisWake non potrà più inviare messaggi da questa sessione.")
                .setNegativeButton("Annulla",null).setPositiveButton("Scollega") { _,_ ->
                    PhoneWhatsApp.unlink(this) { code -> handler.post { if(!isFinishing && !isDestroyed) notice.text=if(code=="unlinked") "WhatsApp scollegato." else "Scollegamento non confermato. Verifica Dispositivi collegati in WhatsApp." } }
                }.showLarge()
        }
        menu.text("Premi Genera QR. In WhatsApp apri Dispositivi collegati → Collega un dispositivo e scansiona il QR. Per usare lo stesso telefono puoi fotografare il codice con un altro dispositivo e scansionare quella foto, mentre il codice è ancora valido.\n\nSessione conservata solo sul telefono. Connessione non ufficiale Meta; nessun messaggio viene inviato durante l’abbinamento.",15f)
        setContentView(menu);PhoneWhatsApp.connect(this,false)
    }
    private fun showState() {
        val value=PhoneWhatsApp.state(true);val s=value.optString("state")
        state.text=when(s) {
            "connected" -> "WhatsApp collegato e connesso."
            "paired" -> "Abbinamento riuscito; attendo connessione…"
            "qr_ready" -> "Scansiona il QR: valido ancora "+maxOf(0,(value.optLong("expires")-System.currentTimeMillis())/1000)+" secondi."
            "connecting" -> "Connessione WhatsApp…"
            "qr_expired" -> "QR scaduto. Premi Genera QR per riprovare."
            "not_linked" -> "WhatsApp non collegato. Premi Genera QR."
            "disconnected" -> "WhatsApp disconnesso. Premi Riconnetti."
            "not_started" -> "Preparazione motore WhatsApp…"
            else -> "Collegamento non riuscito. Verifica la rete e riprova dal pulsante."
        }
        generate.isEnabled=s in setOf("not_linked","qr_expired","pairing_failed","connection_failed","disconnected","engine_unavailable")
        reconnect.isEnabled=s in setOf("disconnected","connection_failed")
        val qr=value.optString("qr").takeIf { s=="qr_ready" && value.optLong("expires")>System.currentTimeMillis() }.orEmpty()
        if(qr!=lastQr) {
            lastQr=qr;image.visibility=if(qr.isEmpty()) View.GONE else View.VISIBLE
            if(qr.isEmpty()) image.setImageDrawable(null)
            else runCatching {
                val matrix=QRCodeWriter().encode(qr,BarcodeFormat.QR_CODE,512,512,mapOf(EncodeHintType.MARGIN to 3))
                val pixels=IntArray(512*512) { i -> if(matrix[i%512,i/512]) Color.BLACK else Color.WHITE }
                image.setImageBitmap(Bitmap.createBitmap(pixels,512,512,Bitmap.Config.ARGB_8888))
            }.onFailure { image.visibility=View.GONE;state.text="QR non disponibile. Premi Genera QR." }
        }
    }
    override fun onResume() { super.onResume();handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update);super.onPause() }
}
