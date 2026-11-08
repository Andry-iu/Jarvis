package it.jarviswake.cube

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class CubeGameActivity : Activity() {
    private val game = CubeGame()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "CubeStatus").apply { isDaemon = true } }
    private var generation = 0
    private var resumed = false
    private lateinit var cube: CubeView
    private lateinit var goal: TextView
    private lateinit var state: TextView
    private var feedback = "Trascina il cubo per allineare la faccia richiesta"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { game.restore(it.getDouble("yaw"), it.getDouble("pitch"), it.getInt("score"), it.getInt("target", 4), it.getBoolean("manual")) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(7,12,18))
            val pad = (12 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
        }
        fun label(size: Float) = TextView(this).apply { textSize = size; setTextColor(Color.WHITE); setPadding(0, 2, 0, 2); body.addView(this) }
        goal = label(20f)
        state = label(14f)
        cube = CubeView(this, game).apply { contentDescription = "Cubo tridimensionale da ruotare"; changed = { refreshGoal() } }
        body.addView(cube, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this)
        fun button(label: String, action: () -> Unit) {
            controls.addView(Button(this).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        button("Conferma") {
            feedback = if (game.confirm()) "Corretto! Ora cerca la prossima faccia" else "Ruota ancora: la faccia richiesta deve essere dritta davanti"
            refreshGoal(); cube.invalidate()
        }
        button("Mescola") { game.shuffle(); feedback = "Trascina il cubo per allineare la faccia richiesta"; refreshGoal(); cube.invalidate() }
        button("Chiudi") { finish() }
        body.addView(controls)
        setContentView(body)
        refreshGoal()
    }
    private fun refreshGoal() { goal.text = "JARVIS CUBE · faccia ${game.target} · punti ${game.score}" }
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed) return
            val epoch = generation
            worker.execute {
                val value = CubeSyncClient.read(applicationContext)
                main.post {
                    if (!resumed || epoch != generation) return@post
                    cube.updateLink(value)
                    val screen = if ((cube.display?.displayId ?: Display.DEFAULT_DISPLAY) == Display.DEFAULT_DISPLAY) "Anteprima telefono" else "Display esterno"
                    val status = when {
                        value.code == "linked" && value.visible && value.muted -> "Jarvis in mute · animazione ferma"
                        value.code == "linked" && value.visible -> "Cover Jarvis visibile · sincronizzato"
                        value.code == "linked" -> "Cover chiusa · gioco autonomo"
                        value.code == "missing" -> "Installa JarvisWake .123 per sincronizzare la cover"
                        else -> "Collegamento Jarvis non disponibile (${value.code})"
                    }
                    state.text = "$screen\n$status\n$feedback"
                    main.postDelayed(this, 250L)
                }
            }
        }
    }
    override fun onResume() { super.onResume(); resumed = true; generation++; cube.running = true; main.post(poll) }
    override fun onPause() { resumed = false; generation++; main.removeCallbacks(poll); cube.running = false; super.onPause() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putDouble("yaw", game.yaw); outState.putDouble("pitch", game.pitch)
        outState.putInt("score", game.score); outState.putInt("target", game.target); outState.putBoolean("manual", game.manual)
        super.onSaveInstanceState(outState)
    }
}
