package it.jarvisprobe

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Experimental standalone WebView probe. No automatic sending or transcript logging. */
class ProbeActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        result = TextView(this).apply { text = "Accedere a ChatGPT e aprire manualmente una conversazione." }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        val inspect = Button(this).apply {
            text = "Leggi messaggi visibili"
            setOnClickListener { inspectConversation() }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(inspect)
            addView(result)
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(layout)
        web.loadUrl("https://chatgpt.com/")
    }

    private fun inspectConversation() {
        if (web.url?.startsWith("https://chatgpt.com/") != true) {
            result.text = "Pagina non autorizzata."
            return
        }
        val script = """
            (() => JSON.stringify({
              url: location.href,
              title: document.title,
              messages: [...document.querySelectorAll('[data-message-author-role]')]
                .map(el => ({role: el.getAttribute('data-message-author-role'),
                  text: (el.innerText || '').trim()}))
                .filter(x => x.text).slice(-12)
            }))()
        """.trimIndent()
        web.evaluateJavascript(script) { encoded ->
            val parsed = runCatching {
                val payload = org.json.JSONTokener(encoded).nextValue() as String
                JSONObject(payload)
            }.getOrNull()
            result.text = if (parsed == null) "Estrazione non riuscita." else {
                val messages = parsed.optJSONArray("messages")
                "URL: ${parsed.optString("url")}\nTitolo: ${parsed.optString("title")}\n" +
                    "Messaggi DOM caricati: ${messages?.length() ?: 0}\n" +
                    (0 until (messages?.length() ?: 0)).joinToString("\n---\n") { i ->
                        val item = messages!!.optJSONObject(i)
                        "${item?.optString("role")}: ${item?.optString("text")}"
                    }
            }
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
