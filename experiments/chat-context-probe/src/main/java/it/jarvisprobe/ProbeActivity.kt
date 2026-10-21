package it.jarvisprobe

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.app.AlertDialog
import org.json.JSONObject

/** Experimental standalone WebView probe. No automatic sending or transcript logging. */
class ProbeActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var result: TextView
    private lateinit var question: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        result = TextView(this).apply { text = "Accedere a ChatGPT e aprire manualmente una conversazione." }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        question = EditText(this).apply { hint = "Domanda di prova (solo chat aperta)" }
        val send = Button(this).apply {
            text = "Invia domanda dopo conferma"
            setOnClickListener { confirmSend() }
        }
        val inspect = Button(this).apply {
            text = "Leggi messaggi visibili"
            setOnClickListener { inspectConversation() }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(inspect)
            addView(question)
            addView(send)
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

    private fun confirmSend() {
        val message = question.text.toString().trim()
        if (message.isEmpty()) { result.text = "Inserire una domanda."; return }
        val url = web.url ?: ""
        if (!url.startsWith("https://chatgpt.com/c/") &&
            !url.startsWith("https://chatgpt.com/g/")) {
            result.text = "Aprire prima una conversazione ChatGPT esistente."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Inviare nella chat corrente?")
            .setMessage("La domanda sarà aggiunta alla conversazione aperta. Conferma?")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Invia") { _, _ -> submit(message, url) }
            .show()
    }

    private fun submit(message: String, originalUrl: String) {
        val escaped = JSONObject.quote(message)
        val script = """
            (() => {
              const editor = [...document.querySelectorAll(
                'textarea,[contenteditable="true"],[role="textbox"]'
              )].find(el => el.getClientRects().length &&
                !el.disabled && el.getAttribute('aria-disabled') !== 'true');
              if (!editor) return 'editor_missing';
              if ((editor.value || editor.innerText || '').trim()) return 'editor_occupied';
              const message = __MESSAGE__;
              if (editor.isContentEditable) {
                editor.focus();
                editor.textContent = message;
                editor.dispatchEvent(new InputEvent('input', {
                  bubbles: true, inputType: 'insertText', data: message
                }));
              } else {
                const prototype = editor instanceof HTMLTextAreaElement
                  ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
                if (!setter) return 'editor_unsupported';
                setter.call(editor, message);
                editor.dispatchEvent(new InputEvent('input', {
                  bubbles: true, inputType: 'insertText', data: message
                }));
              }
              if ((editor.value || editor.innerText || '').trim() !== message) return 'fill_failed';
              const form = editor.closest('form');
              const buttons = [...(form || document).querySelectorAll('button')]
                .filter(el => !el.disabled && el.getClientRects().length &&
                  (el.getAttribute('data-testid') === 'send-button' ||
                   /^(send|invia)( message| messaggio)?$/i.test(el.getAttribute('aria-label') || '')));
              if (buttons.length !== 1) return 'send_ambiguous';
              buttons[0].click();
              return 'clicked';
            })()
        """.trimIndent().replace("__MESSAGE__", escaped)
        if (web.url != originalUrl) { result.text = "Conversazione cambiata: invio annullato."; return }
        web.evaluateJavascript(script) { response ->
            result.text = "Esito invio: " + response + ". Premere Leggi messaggi visibili dopo la risposta."
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
