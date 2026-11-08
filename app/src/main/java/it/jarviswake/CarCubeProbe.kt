package it.jarviswake

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Public display-API probe, not a navigation app or a claim of Android Auto compatibility. */
internal object CarCubeProbe {
    const val EXTRA_CYCLE = "it.jarviswake.car_probe.CYCLE"
    const val EXTRA_GENERATION = "it.jarviswake.car_probe.GENERATION"
    private val main = Handler(Looper.getMainLooper())
    internal val state = CarCubeProbeState()
    private val covers = WeakHashMap<View, Boolean>()
    private var application: Context? = null
    private var cube = WeakReference<CarCubeProbeActivity>(null)
    var status = "Prova disattivata"
        private set

    private fun intent(c: Context) = Intent(c, CarCubeProbeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    fun targets(c: Context): List<Int> {
        if (Build.VERSION.SDK_INT < 35 || !BridgeCarConnection.keepMapsCover()) return emptyList()
        val displays = c.getSystemService(DisplayManager::class.java) ?: return emptyList()
        val manager = c.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return runCatching { displays.displays.filter {
            it.displayId != Display.DEFAULT_DISPLAY && it.isValid &&
                manager.isActivityStartAllowedOnDisplay(c, it.displayId, intent(c))
        }.map { it.displayId } }.getOrDefault(emptyList())
    }

    fun arm(c: Context, displayId: Int): Boolean {
        if (VoiceCycle.active || displayId !in targets(c)) {
            status = "Nessun display di prova autorizzato; nessun avvio eseguito"
            log(c, "probe_unavailable")
            return false
        }
        disarm()
        application = c.applicationContext
        state.arm(displayId, SystemClock.elapsedRealtime())
        status = "Prova attiva per 60 secondi: apri la cover del telefono"
        log(c, "probe_armed")
        schedule()
        return true
    }

    fun disarm(reason: String = "Prova disattivata") {
        state.disarm()
        main.removeCallbacks(tick)
        cube.get()?.finish()
        cube.clear()
        status = reason
    }

    /** These hooks only enqueue work; closing the phone HUD must never wait for this experiment. */
    fun coverChanged(view: View, attached: Boolean) {
        CarCubeSync.coverChanged(view, attached)
        main.post {
            if (attached) covers[view] = true else covers.remove(view)
            if (state.armed(SystemClock.elapsedRealtime())) schedule()
        }
    }

    private fun schedule() { main.removeCallbacks(tick); main.post(tick) }
    private val tick = object : Runnable {
        override fun run() {
            runCatching { reconcile() }.onFailure {
                application?.let { log(it, "probe_internal_error") }
                disarm()
            }
            if (state.armed(SystemClock.elapsedRealtime())) main.postDelayed(this, 200L)
        }
    }

    private fun reconcile() {
        val c = application ?: return
        val now = SystemClock.elapsedRealtime()
        if (!state.armed(now)) { disarm("Prova scaduta"); return }
        if (!BridgeCarConnection.keepMapsCover() || c.getSystemService(DisplayManager::class.java)
                ?.getDisplay(state.displayId ?: -1)?.isValid != true) {
            log(c, "probe_disconnected"); disarm(); return
        }
        if (c.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false) {
            log(c, "phone_locked"); disarm("Telefono bloccato: prova terminata"); return
        }
        val token = VoiceCycle.id
        val visible = covers.keys.any { it.isAttachedToWindow && it.isShown }
        if (token == null || !visible) {
            if (state.attempted && !state.closed) closeCycle()
            return
        }
        // Activity/view recreation, swapping launch/web/accessibility cover, and mute updates
        // never authorize a second dispatch for the same VoiceCycle.
        if (state.cycle == token && state.attempted) {
            cube.get()?.setMuted(microphoneMuted())
            return
        }
        val target = state.displayId ?: return
        val launch = intent(c).putExtra(EXTRA_CYCLE, token).putExtra(EXTRA_GENERATION, state.generation)
        val manager = c.getSystemService(ActivityManager::class.java)
        val allowed = manager?.isActivityStartAllowedOnDisplay(c, target, launch) == true
        val unlocked = c.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
        val result = state.consume(token, now, BridgeCarConnection.keepMapsCover(), unlocked, allowed)
        if (result != "dispatch") { status = "Apertura non eseguita: $result"; log(c, result); return }
        cube.get()?.finish()
        cube.clear()
        log(c, "probe_dispatch")
        status = "Apertura richiesta; comparsa sul display non ancora confermata"
        try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
            c.startActivity(launch, options.toBundle())
        } catch (_: RuntimeException) {
            closeCycle()
            status = "Apertura rifiutata dal sistema; nessun nuovo tentativo automatico"
            log(c, "probe_rejected")
        }
    }

    private fun closeCycle() {
        state.closeCycle()
        cube.get()?.finish()
        cube.clear()
    }

    fun accepts(activity: CarCubeProbeActivity): Boolean = state.accepts(
        activity.intent.getStringExtra(EXTRA_CYCLE),
        activity.intent.getLongExtra(EXTRA_GENERATION, -1L),
        activity.display?.displayId ?: Display.DEFAULT_DISPLAY,
        SystemClock.elapsedRealtime()
    ) && BridgeCarConnection.keepMapsCover() && VoiceCycle.id == state.cycle &&
        covers.keys.any { it.isAttachedToWindow && it.isShown }

    fun microphoneMuted(): Boolean = covers.keys.filterIsInstance<HolographicOverlayView>()
        .firstOrNull { it.isAttachedToWindow && it.isShown }?.probeMicrophoneMuted ?: true

    fun resumed(activity: CarCubeProbeActivity) {
        cube = WeakReference(activity)
        log(activity, "probe_resumed")
    }

    fun firstFrame(activity: CarCubeProbeActivity) {
        if (!accepts(activity) || !activity.hasWindowFocus()) return
        if (state.frame(activity.intent.getStringExtra(EXTRA_CYCLE),
                activity.intent.getLongExtra(EXTRA_GENERATION, -1L),
                activity.display?.displayId ?: Display.DEFAULT_DISPLAY, SystemClock.elapsedRealtime())) {
            status = "Primo frame sul display selezionato; visibilità sull’Audi da confermare"
            log(activity, "probe_frame")
        }
    }

    fun closed(activity: CarCubeProbeActivity) {
        if (cube.get() !== activity) return
        cube.clear()
        state.closeCycle()
        status = "Schermata chiusa; nessuna riapertura nello stesso ciclo"
        log(activity, "probe_closed")
    }

    private fun log(c: Context, code: String) = DiagnosticLog.record(
        c, DiagnosticLog.Event.CAR_CUBE_PROBE, monitorStatus = code
    ) // Fixed codes only; no display names, IDs, chat data, audio, or credentials.
}
