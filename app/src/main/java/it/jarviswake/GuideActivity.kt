package it.jarviswake

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import org.json.JSONObject
import java.text.Normalizer

internal object GuideSearch {
    fun tokens(query:String)=Normalizer.normalize(query.lowercase(),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"),"").split(Regex("[^a-z0-9]+"))
        .filter { it.length>=3 }.distinct()
    fun score(query:String,title:String,keywords:String,text:String):Int {
        fun normalized(s:String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
        val heading=normalized(title+" "+keywords);val body=normalized(text)
        return tokens(query).sumOf { token -> (if(heading.contains(token)) 4 else 0)+(if(body.contains(token)) 1 else 0) }
    }
}

class GuideActivity:Activity() {
    private lateinit var guide:JSONObject
    private var topic=""
    private var query=""
    private var backCallback:OnBackInvokedCallback?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        guide=JSONObject(assets.open("jarvis-guide.json").bufferedReader().use { it.readText() })
        topic=savedInstanceState?.getString("topic") ?: intent.getStringExtra("topic").orEmpty()
        query=savedInstanceState?.getString("query").orEmpty()
        render()
        if(Build.VERSION.SDK_INT>=33) {
            val cb=OnBackInvokedCallback { back() };backCallback=cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,cb)
        }
    }
    private fun sections():List<JSONObject> { val a=guide.getJSONArray("sections");return (0 until a.length()).map { a.getJSONObject(it) } }
    private fun render() {
        window.hideMenuKeyboard()
        val chosen=sections().find { it.getString("id")==topic }
        val instructions=chosen?.getString("text") ?: "Scegli un argomento o cerca parole nella guida. I risultati compaiono sopra; selezionane uno per leggere i passaggi nella pagina. La stessa guida è interrogabile nel plugin JarvisWake Link anche con telefono offline. Usa Esporta guida completa per allegare il file ad altre chat."
        val menu=SplitMenuScreen(this,chosen?.getString("title") ?: "Guida completa · "+guide.getString("version"),instructions) { back() }
        if(chosen==null) {
            val input=menu.field("Cerca nella guida",query,"Scrivi una domanda o parole come wake, saluto, swipe, volume, appunti o plugin, poi Cerca.")
            input.isSaveEnabled=false
            menu.button("Cerca") { query=input.text.toString().take(300);render() }
            menu.button("Tutti gli argomenti") { query="";render() }
            val all=sections()
            val found=if(query.isBlank()) all else all.map { it to GuideSearch.score(query,it.getString("title"),it.getString("keywords"),it.getString("text")) }
                .filter { it.second>0 }.sortedByDescending { it.second }.map { it.first }
            if(found.isEmpty()) menu.text("Nessun argomento trovato. Prova altre parole.")
            for(s in found) menu.button(s.getString("title"),"Apre questo argomento della guida per leggerne tutti i passaggi.") { topic=s.getString("id");render() }
        } else {
            menu.text("Guida "+guide.getString("version")+" · contenuto statico",14f)
            menu.text(chosen.getString("text"))
            menu.button("Indice e ricerca") { topic="";render() }
        }
        menu.button("Esporta guida completa") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="text/markdown";putExtra(Intent.EXTRA_TITLE,"JarvisWake-guida-"+guide.getString("version")+".md")
            },1)
        }
        setContentView(menu);menu.requestFocus()
    }
    private fun markdown()="# "+guide.getString("title")+" — "+guide.getString("version")+" / "+guide.getInt("versionCode")+"\n\n"+
        sections().joinToString("\n\n") { "## "+it.getString("title")+"\n\n"+it.getString("text") }+"\n"
    private fun back() { if(topic.isNotEmpty()) { topic="";render() } else finish() }
    @Deprecated("Back also registered for Android 13+")
    @SuppressLint("GestureBackNavigation") // API31 fallback; API33+ uses OnBackInvokedCallback above.
    override fun onBackPressed() { back() }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("topic",topic);outState.putString("query",query);super.onSaveInstanceState(outState) }
    @Deprecated("Document picker result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=1 || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        Thread({
            val ok=runCatching { contentResolver.openOutputStream(uri,"w")?.bufferedWriter()?.use { it.write(markdown()) } ?: error("unavailable") }.isSuccess
            Handler(Looper.getMainLooper()).post { if(!isFinishing && !isDestroyed) Toast.makeText(this,if(ok) "Guida esportata." else "Esportazione guida non riuscita.",Toast.LENGTH_LONG).show() }
        },"Jarvis-guide-export").start()
    }
    override fun onDestroy() { if(Build.VERSION.SDK_INT>=33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) };super.onDestroy() }
}
