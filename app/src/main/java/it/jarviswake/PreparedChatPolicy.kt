package it.jarviswake

import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal object PreparedChatPolicy {
    const val READY_TTL_MS = 24 * 60 * 60_000L
    const val PREPARE_TIMEOUT_MS = 5 * 60_000L
    private val chat = Regex("^/(?:g/([^/]+)/)?c/([0-9a-fA-F-]{36})/?$")
    fun conversationUrl(raw: String, project: String): String? = runCatching {
        if(!ChatGptProjectSettings.isValidProjectUrl(project)) return null
        val uri=URI(raw)
        val queryAllowed=uri.rawQuery==null || uri.rawQuery!!.matches(Regex("model=[A-Za-z0-9_.-]{1,128}"))
        if (uri.scheme!="https" || uri.host!="chatgpt.com" || uri.port!=-1 || uri.rawUserInfo!=null ||
            !queryAllowed || uri.rawFragment!=null || uri.rawPath!=uri.path) return null
        val match=chat.matchEntire(uri.path) ?: return null
        val id=match.groupValues[2]
        if (UUID.fromString(id).toString()!=id.lowercase()) return null
        val expected=URI(project).path.split('/').getOrNull(2) ?: return null
        if (match.groupValues[1].isNotEmpty() && match.groupValues[1]!=expected) return null
        "https://chatgpt.com${uri.path.trimEnd('/')}"
    }.getOrNull()
    fun normalized(text: String)=text.replace(Regex("\\s+")," ").trim()
    fun digest(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun proof(text: String): Pair<String,Int>? {
        val prefix=normalized(text).takeLast(64)
        return if (prefix.length<24) null else digest(prefix) to prefix.length
    }
    fun matches(hash: String, length: Int, texts: List<String>): Boolean {
        if (!hash.matches(Regex("[a-f0-9]{64}")) || length !in 24..64) return false
        return texts.take(1800).any { text ->
            (sequenceOf(text)+text.lineSequence()).any { part ->
                val value=normalized(part)
                value.length>=length && (digest(value.take(length))==hash || digest(value.takeLast(length))==hash)
            }
        }
    }
}

internal enum class PreparationPhase { STARTED, READY, CLAIMED, FAILED }
internal data class PreparedConversation(
    val attempt: String, val project: String, val projectName: String, val createdAt: Long,
    val phase: PreparationPhase = PreparationPhase.STARTED, val url: String = "", val hash: String = "",
    val proofLength: Int = 0, val readyAt: Long = 0, val cycle: String = "", val error: String = "", val linkOnly: Boolean = false, val submittedAt: Long = -1L
)

/** Persistent consumption precedes opening another app; uncertain preparation is never replayed. */
internal class PreparedChatLedger(initial: PreparedConversation?, private val persist: (PreparedConversation?)->Unit) {
    var record=initial
        private set
    private fun set(value: PreparedConversation?) { persist(value);record=value }
    fun recover() {
        record?.takeIf {it.phase==PreparationPhase.STARTED}?.let {set(it.copy(phase=PreparationPhase.FAILED,error="interrupted"))}
    }
    fun reset() = set(null)
    fun begin(project: String, name: String, now: Long): PreparedConversation? {
        if (record?.phase in setOf(PreparationPhase.STARTED,PreparationPhase.READY,PreparationPhase.FAILED)) return null
        val next=PreparedConversation(UUID.randomUUID().toString(),project,name,now)
        set(next);return next
    }
    fun fail(attempt: String, reason: String) {
        record?.takeIf {it.attempt==attempt && it.phase==PreparationPhase.STARTED}?.let {
            set(it.copy(phase=PreparationPhase.FAILED,error=reason))
        }
    }
    fun markSubmitted(attempt:String,now:Long):Boolean {
        val current=record?.takeIf {it.attempt==attempt && it.phase==PreparationPhase.STARTED} ?: return false
        if(now-current.createdAt !in 0..PreparedChatPolicy.PREPARE_TIMEOUT_MS) return false
        if(current.submittedAt>=0L) return true
        set(current.copy(submittedAt=now));return true
    }
    fun captureLink(attempt:String,url:String,now:Long):Boolean {
        val current=record?.takeIf {it.attempt==attempt && it.phase==PreparationPhase.STARTED} ?: return false
        if(current.submittedAt<current.createdAt || current.submittedAt>now ||
            now-current.createdAt !in 0..PreparedChatPolicy.PREPARE_TIMEOUT_MS) return false
        val destination=PreparedChatPolicy.conversationUrl(url,current.project) ?: return false
        set(current.copy(phase=PreparationPhase.READY,url=destination,hash="",proofLength=0,readyAt=now,linkOnly=true))
        return true
    }
    fun complete(attempt: String, url: String, reply: String, now: Long): Boolean {
        val current=record?.takeIf {it.attempt==attempt && it.phase==PreparationPhase.STARTED} ?: return false
        if (now-current.createdAt !in 0..PreparedChatPolicy.PREPARE_TIMEOUT_MS) return false
        val destination=PreparedChatPolicy.conversationUrl(url,current.project) ?: return false
        val proof=PreparedChatPolicy.proof(reply) ?: return false
        set(current.copy(phase=PreparationPhase.READY,url=destination,hash=proof.first,proofLength=proof.second,readyAt=now))
        return true
    }
    fun claim(cycle: String, project: String, name: String, now: Long): PreparedConversation? {
        val current=record?.takeIf {it.phase==PreparationPhase.READY && it.project==project && it.projectName==name} ?: return null
        val linkCapture=current.linkOnly && current.submittedAt>=current.createdAt && current.readyAt>=current.submittedAt
        val oldProof=!current.linkOnly && current.proofLength in 24..64 && current.hash.matches(Regex("[a-f0-9]{64}"))
        if (now-current.readyAt !in 0..PreparedChatPolicy.READY_TTL_MS ||
            PreparedChatPolicy.conversationUrl(current.url,project)==null || !(linkCapture || oldProof)) return null
        val claimed=current.copy(phase=PreparationPhase.CLAIMED,cycle=cycle)
        set(claimed);return claimed
    }
    fun rejectClaim(cycle:String) {
        record?.takeIf {it.phase==PreparationPhase.CLAIMED && it.cycle==cycle}?.let {
            set(it.copy(phase=PreparationPhase.FAILED,error="handoff_unconfirmed"))
        }
    }
    fun forCycle(cycle: String)=record?.takeIf {it.phase==PreparationPhase.CLAIMED && it.cycle==cycle}
}
