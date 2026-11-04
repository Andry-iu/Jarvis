package it.jarviswake

import java.net.URI
import java.security.MessageDigest
import java.util.UUID

internal object PreparedChatPolicy {
    const val READY_TTL_MS = 24 * 60 * 60_000L
    const val PREPARE_TIMEOUT_MS = 5 * 60_000L
    private val chat = Regex("^/(?:g/([^/]+)/)?c/([0-9a-fA-F-]{36})/?$")
    internal data class LinkCheck(val url:String?=null,val error:String="")
    // A project may carry a decorative slug; compare its complete 32-hex identity, never a prefix.
    private val projectIdentity=Regex("^g-p-([0-9a-f]{32})(?:-[a-z0-9]+(?:-[a-z0-9]+)*)?$",RegexOption.IGNORE_CASE)
    private fun sameProject(actual:String,expected:String):Boolean {
        if(actual==expected) return true
        val a=projectIdentity.matchEntire(actual)?.groupValues?.get(1) ?: return false
        val b=projectIdentity.matchEntire(expected)?.groupValues?.get(1) ?: return false
        return a.equals(b,ignoreCase=true)
    }
    fun conversationUrl(raw:String,project:String):String?=checkConversationUrl(raw,project).url
    /** Native dispatch uses the direct conversation path after validating the captured project link. */
    fun nativeConversationUrl(raw:String,project:String):String? {
        val validated=conversationUrl(raw,project) ?: return null
        val id=URI(validated).path.substringAfterLast('/').lowercase()
        return "https://chatgpt.com/c/$id"
    }
    fun checkConversationUrl(raw:String,project:String):LinkCheck = runCatching {
        if(!ChatGptProjectSettings.isValidProjectUrl(project)) return LinkCheck(error="link_project_settings")
        val uri=URI(raw)
        if(uri.scheme!="https" || uri.host!="chatgpt.com" || uri.port!=-1 || uri.rawUserInfo!=null)
            return LinkCheck(error="link_origin")
        val queryAllowed=uri.rawQuery==null || uri.rawQuery!!.matches(Regex("model=[A-Za-z0-9_.-]{1,128}"))
        if(!queryAllowed || uri.rawFragment!=null) return LinkCheck(error="link_parameters")
        if(uri.rawPath!=uri.path) return LinkCheck(error="link_encoding")
        val match=chat.matchEntire(uri.path) ?: return LinkCheck(error="link_path")
        val id=match.groupValues[2]
        if(runCatching {UUID.fromString(id).toString()==id.lowercase()}.getOrDefault(false)!=true)
            return LinkCheck(error="link_id")
        val expected=URI(project).path.split('/').getOrNull(2) ?: return LinkCheck(error="link_project_settings")
        if(match.groupValues[1].isNotEmpty() && !sameProject(match.groupValues[1],expected))
            return LinkCheck(error="link_project")
        LinkCheck(url="https://chatgpt.com${uri.path.trimEnd('/')}")
    }.getOrDefault(LinkCheck(error="link_syntax"))
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
    val proofLength: Int = 0, val readyAt: Long = 0, val cycle: String = "", val error: String = "", val linkOnly: Boolean = false, val submittedAt: Long = -1L, val openingText:String = OpeningMessagePolicy.TEXT
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
    fun begin(project: String, name: String, now: Long, openingText:String=OpeningMessagePolicy.TEXT): PreparedConversation? {
        if(openingText.isBlank()) return null
        if (record?.phase in setOf(PreparationPhase.STARTED,PreparationPhase.READY,PreparationPhase.FAILED)) return null
        val next=PreparedConversation(UUID.randomUUID().toString(),project,name,now,openingText=openingText)
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
    fun captureLink(attempt:String,url:String,now:Long):Boolean=captureLinkResult(attempt,url,now)==null
    /** null means persisted; all failures are fixed codes, never URL/content or exception messages. */
    fun captureLinkResult(attempt:String,url:String,now:Long):String? {
        val current=record?.takeIf {it.attempt==attempt && it.phase==PreparationPhase.STARTED} ?: return "attempt_mismatch"
        if(current.submittedAt<current.createdAt) return "submission_missing"
        if(current.submittedAt>now || now-current.createdAt !in 0..PreparedChatPolicy.PREPARE_TIMEOUT_MS)
            return "submission_time"
        val checked=PreparedChatPolicy.checkConversationUrl(url,current.project)
        val destination=checked.url ?: return checked.error
        set(current.copy(phase=PreparationPhase.READY,url=destination,hash="",proofLength=0,readyAt=now,linkOnly=true))
        return null
    }
    fun complete(attempt: String, url: String, reply: String, now: Long): Boolean {
        val current=record?.takeIf {it.attempt==attempt && it.phase==PreparationPhase.STARTED} ?: return false
        if (now-current.createdAt !in 0..PreparedChatPolicy.PREPARE_TIMEOUT_MS) return false
        val destination=PreparedChatPolicy.conversationUrl(url,current.project) ?: return false
        val proof=PreparedChatPolicy.proof(reply) ?: return false
        set(current.copy(phase=PreparationPhase.READY,url=destination,hash=proof.first,proofLength=proof.second,readyAt=now))
        return true
    }
    fun claim(cycle: String, project: String, name: String, now: Long, openingText:String=OpeningMessagePolicy.TEXT): PreparedConversation? {
        val current=record?.takeIf {it.phase==PreparationPhase.READY && it.project==project && it.projectName==name && it.openingText==openingText} ?: return null
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
