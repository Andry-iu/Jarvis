package it.jarviswake

import android.content.Context
import android.os.SystemClock

/** Technical metadata only. No names, numbers, contact IDs, JSON, Base64, hashes or exception messages. */
internal object ContactExportDiagnostics {
    enum class Stage { POLLED, CLAIM_BEGIN, CLAIMED, REPLAY, STARTED, PROGRESS, HEARTBEAT, RESULT_READY,
        RESULT_QUEUED, GUARD_REJECTED, SEND_BEGIN, SEND_ACK, SEND_DEFERRED, RESULT_REVOKED, WORKER_REFRESH, WORKER_STOPPED }
    enum class Reason { GENERATION_CHANGED, LINK_DISABLED, COPY_DISABLED, CONTACT_PERMISSION, DEADLINE, SCREEN_LOCKED,
        NETWORK, TIMEOUT, DNS, TLS, INTERRUPTED, PROTOCOL_OR_STORAGE, RESULT_AGE, HTTP }
    private val phases=setOf("starting","contacts","phones","emails","verifying","freezing")
    private val codes=setOf("contacts_export_ready","contacts_export_page","contacts_export_disabled","contacts_export_expired",
        "contacts_export_unavailable","contacts_export_changed","contacts_export_too_large","contacts_export_busy","invalid_export_offset",
        "invalid_export_mode","permission_required","locked","cancelled","expired_before_dispatch","outcome_uncertain","unsupported",
        "contact_changed","contact_expired","unavailable","claimed","already_claimed","saved","uncertain","provider_cancelled")
    internal fun metadata(stage:Stage,code:String?=null,phase:String?=null,offset:Int?=null,bytes:Int?=null,
        base64Chars:Int?=null,chunkBytes:Int?=null,elapsedMs:Long?=null,remainingMs:Long?=null,
        reason:Reason?=null,httpStatus:Int?=null):String = listOf(
        "stage=${stage.name.lowercase()}","code=${code?.takeIf {it in codes} ?: "-"}",
        "phase=${phase?.takeIf {it in phases} ?: "-"}","offset=${offset?.takeIf {it in 0..PhoneContactExport.MAX_BYTES} ?: "-"}",
        "bytes=${bytes?.takeIf {it in 0..PhoneContactExport.MAX_BYTES} ?: "-"}",
        "base64_chars=${base64Chars?.takeIf {it in 0..(4*((PhoneContactExport.CHUNK+2)/3))} ?: "-"}",
        "chunk_bytes=${chunkBytes?.takeIf {it in setOf(6144,262144)} ?: "-"}",
        "elapsed_ms=${elapsedMs?.takeIf {it>=0} ?: "-"}","remaining_ms=${remainingMs?.takeIf {it in 0L..25000L} ?: "-"}",
        "reason=${reason?.name?.lowercase() ?: "-"}","http=${httpStatus?.takeIf {it in 100..599} ?: "-"}"
    ).joinToString(" ")
    fun record(c:Context,id:String?,stage:Stage,code:String?=null,phase:String?=null,offset:Int?=null,bytes:Int?=null,
        base64Chars:Int?=null,chunkBytes:Int?=null,elapsedMs:Long?=null,remainingMs:Long?=null,
        reason:Reason?=null,httpStatus:Int?=null) {
        runCatching {
            val token=id?.takeIf(BridgeProtocol::validId) ?: "-"
            NativeRuntimeSnapshot.save(c,token,"contact_export","t_ms=${SystemClock.elapsedRealtime()} "+
                metadata(stage,code,phase,offset,bytes,base64Chars,chunkBytes,elapsedMs,remainingMs,reason,httpStatus))
        }
    }
    fun reason(error:Exception)=when(error) {
        is java.net.SocketTimeoutException -> Reason.TIMEOUT
        is java.net.UnknownHostException -> Reason.DNS
        is javax.net.ssl.SSLException -> Reason.TLS
        is java.io.IOException -> Reason.NETWORK
        is InterruptedException -> Reason.INTERRUPTED
        else -> Reason.PROTOCOL_OR_STORAGE
    }
}
