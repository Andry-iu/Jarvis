package it.jarviswake

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import it.jarviswake.watch.WatchProtocol

/** Public URI is inert without a one-use ticket delivered through the authenticated Data Layer. */
class WatchEntryActivity:Activity() {
    private var ticket=""
    private var pending=false
    private var done=false
    private var autoRequested=false
    private lateinit var output:TextView
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true);setTurnScreenOn(true)
        val uri=intent.data
        if(uri==null || !uri.isHierarchical || uri.scheme!="jarviswake" || uri.host!="watch" || uri.path!="/start" || uri.queryParameterNames!=setOf("id")) {finish();return}
        ticket=uri.getQueryParameter("id").orEmpty()
        if(!WatchTickets.transition(this,ticket,"prepared","accepted") && !(b!=null && WatchTickets.transition(this,ticket,"accepted","accepted"))) {finish();return}
        val m=SplitMenuScreen(this,"Avvio dal Watch","Lo sblocco è deciso da Android. Con stato attendibile può essere automatico; altrimenti usa PIN o biometria sul telefono."){finish()}
        output=m.text("Attendo lo sblocco Android…")
        m.button("Sblocca e avvia Jarvis","Richiede lo sblocco al sistema, senza leggere o memorizzare PIN."){unlock()}
        setContentView(m)
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus && ::output.isInitialized && !autoRequested) {autoRequested=true;unlock()}}
    private fun unlock() {
        if(pending || done || isFinishing || !hasWindowFocus()) return
        val k=getSystemService(KeyguardManager::class.java)
        if(!k.isKeyguardLocked && !k.isDeviceLocked) {launch();return}
        pending=true
        k.requestDismissKeyguard(this,object:KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {pending=false;if(!isFinishing && !isDestroyed) launch()}
            override fun onDismissCancelled() {pending=false;output.text="Sblocco annullato. Nessun ciclo avviato."}
            override fun onDismissError() {pending=false;output.text="Android richiede lo sblocco sul telefono. Nessun ciclo avviato."}
        })
    }
    private fun launch() {
        if(done || !WatchTickets.transition(this,ticket,"accepted","ready")) {output.text="Richiesta scaduta o già usata. Ripeti dal Watch.";return}
        done=true
        startActivity(Intent(this,MainActivity::class.java).putExtra(WatchProtocol.EXTRA,ticket));finish()
    }
}
