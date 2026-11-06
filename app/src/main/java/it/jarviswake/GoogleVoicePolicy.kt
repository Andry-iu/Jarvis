package it.jarviswake

import java.net.URI
import kotlin.math.sqrt

/** PCM16 mono 16 kHz. RAM-only buffer; the VAD starts Google streaming with preroll at onset. */
internal class LocalSpeechBuffer(
    private val silenceMs: Int = 1200,
    private val maximumMs: Int = 50000,
    private val threshold: Int = 600,
    preRollMs: Int = 400, private val onsetMs: Int = 60
) {
    init { require(silenceMs in 400..3000 && maximumMs in 5000..50000 && threshold in 100..10000 && preRollMs in 100..1000 && onsetMs in 20..400) }
    private val ring = ShortArray(preRollMs * 16)
    private var cursor = 0
    private var filled = 0
    private var onset = 0
    private var silence = 0
    private var count = 0
    private val phrase = ShortArray(maximumMs * 16)
    var speaking = false
        private set
    var finished = false
        private set
    fun preRoll(): ShortArray = phrase.copyOf(count)

    fun accept(samples: ShortArray, size: Int): ShortArray? {
        require(size in 0..samples.size)
        if (finished || size == 0) return null
        val voiced = sqrt((0 until size).sumOf { samples[it].toDouble() * samples[it] } / size) >= threshold
        if (!speaking) {
            for (i in 0 until size) { ring[cursor] = samples[i]; cursor = (cursor + 1) % ring.size; filled = (filled + 1).coerceAtMost(ring.size) }
            onset = if (voiced) onset + size else 0
            if (onset < onsetMs*16) return null // 60 ms avoids single transient triggers.
            speaking = true
            for (i in 0 until filled) phrase[count++] = ring[(cursor - filled + i + ring.size) % ring.size]
        } else {
            val take = size.coerceAtMost(phrase.size - count)
            samples.copyInto(phrase, count, 0, take); count += take
        }
        silence = if (voiced) 0 else silence + size
        if (silence < silenceMs * 16 && count < phrase.size) return null
        finished = true
        return phrase.copyOf(count).also { clear() }
    }
    fun clear() { ring.fill(0); phrase.fill(0); cursor = 0; filled = 0; count = 0; onset = 0; silence = 0 }
}

internal object GoogleConversation {
    /** Bind to an existing project conversation, never a generic project/new-chat page. */
    fun valid(value: String): Boolean = runCatching {
        val u = URI(value)
        u.scheme == "https" && u.host == "chatgpt.com" && u.port == -1 && u.rawUserInfo == null &&
            u.rawQuery == null && u.rawFragment == null &&
            Regex("/g/g-p-[A-Za-z0-9_-]+/c/[0-9a-fA-F-]{36}/?").matches(u.rawPath) &&
            java.util.UUID.fromString(u.path.trimEnd('/').substringAfterLast('/')).toString().equals(u.path.trimEnd('/').substringAfterLast('/'), true)
    }.getOrDefault(false)
}

/** Destination comes from the live page of this session, never a saved "next chat" slot. */
internal object GoogleSessionRoute {
    fun projectKey(project: String): String? = runCatching {
        if (!ChatGptProjectSettings.isValidProjectUrl(project)) return null
        val name = URI(project).path.split('/')[2]
        Regex("g-p-([a-fA-F0-9]{32})(?:-[A-Za-z0-9-]+)?").matchEntire(name)?.groupValues?.get(1)?.lowercase() ?: name
    }.getOrNull()
    fun clean(raw: String): String? = runCatching {
        val u=URI(raw)
        if(u.scheme!="https" || u.host!="chatgpt.com" || u.port!=-1 || u.rawUserInfo!=null || u.rawFragment!=null || u.rawPath!=u.path) return null
        if(u.rawQuery!=null && !u.rawQuery!!.matches(Regex("model=[A-Za-z0-9_.-]{1,128}"))) return null
        "https://chatgpt.com"+u.path.trimEnd('/')
    }.getOrNull()
    fun projectPage(raw: String, project: String): Boolean {
        val url=clean(raw) ?: return false
        return projectKey(url)==projectKey(project) && projectKey(project)!=null
    }
    fun conversation(raw: String, project: String, allowDirect: Boolean=false): String? {
        val url=clean(raw) ?: return null
        val u=URI(url)
        val match=Regex("/(?:g/(g-p-[A-Za-z0-9_-]+)/)?c/([0-9a-fA-F-]{36})").matchEntire(u.path) ?: return null
        if(runCatching { java.util.UUID.fromString(match.groupValues[2]).toString().equals(match.groupValues[2],true) }.getOrDefault(false)!=true) return null
        val name=match.groupValues[1]
        if(name.isEmpty()) return url.takeIf {allowDirect}
        return url.takeIf {projectKey("https://chatgpt.com/g/$name/project")==projectKey(project) && projectKey(project)!=null}
    }
}

/** A turn is consumed before submission and before accepting speech. No retry after uncertainty. */
internal class GoogleTurnState(val session: String) {
    var turn: String? = null
        private set
    var phase = "ready"
        private set
    var closed = false
        private set
    fun begin(id: String): Boolean {
        if (closed || phase != "ready") return false
        turn = id; phase = "transcribing"; return true
    }
    fun submitting(): Boolean { if (closed || phase != "transcribing") return false; phase = "submitting"; return true }
    fun dispatched() { if (!closed && phase == "submitting") phase = "send_dispatched" }
    fun observed() { if (!closed && phase == "send_dispatched") phase = "waiting_reply" }
    fun acceptReply(sessionId: String, turnId: String): Boolean {
        if (closed || session != sessionId || turn != turnId || phase !in setOf("send_dispatched", "waiting_reply")) return false
        phase = "synthesizing"; return true
    }
    fun ready() { if (!closed && phase == "synthesizing") phase = "ready" }
    fun fail() { if (!closed) phase = "error" }
    fun close() { closed = true; phase = "closed" }
}

/** Conservative known route pairs. Unknown car/USB/projection paths keep playback half duplex. */
internal object GoogleEchoRoutePolicy {
    fun compatible(input:Int?,output:Int?):Boolean {
        if(input==null || output==null) return false
        return (input==android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC && output in setOf(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)) ||
            (input==output && input in setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,android.media.AudioDeviceInfo.TYPE_USB_HEADSET))
    }
}

/** Secondary echo guard after STT; it never feeds a suspected copy of the spoken reply back to ChatGPT. */
internal object GoogleEchoTextGuard {
    fun matches(transcript:String,spoken:String):Boolean {
        fun normalized(s:String)=s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
        val t=normalized(transcript);val s=normalized(spoken)
        return t.isNotEmpty() && (t==s || (t.length>=8 && s.contains(t)))
    }
}
