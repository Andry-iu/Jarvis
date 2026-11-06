package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class ContactExportDiagnosticsTest {
    @Test fun diagnosticsRetainStageTimingAndCountsButRejectArbitraryPrivateText() {
        val safe=ContactExportDiagnostics.metadata(ContactExportDiagnostics.Stage.RESULT_READY,code="contacts_export_page",
            phase="freezing",offset=262144,bytes=163483,base64Chars=217980,chunkBytes=262144,elapsedMs=1500)
        assertTrue(safe.contains("stage=result_ready"));assertTrue(safe.contains("elapsed_ms=1500"))
        assertTrue(safe.contains("offset=262144"));assertTrue(safe.contains("bytes=163483"))
        val rejected=ContactExportDiagnostics.metadata(ContactExportDiagnostics.Stage.GUARD_REJECTED,code="PRIVATE_NAME_+39000000001",
            phase="PRIVATE_JSON",offset=-1,bytes=Int.MAX_VALUE,base64Chars=Int.MAX_VALUE,chunkBytes=1000000,
            elapsedMs=-1,remainingMs=25001,reason=ContactExportDiagnostics.Reason.SCREEN_LOCKED,httpStatus=999)
        assertFalse(rejected.contains("PRIVATE"));assertFalse(rejected.contains("39000000001"))
        assertTrue(rejected.contains("reason=screen_locked"));assertTrue(rejected.contains("bytes=-"));assertTrue(rejected.contains("http=-"))
    }
    @Test fun networkFailuresAreClassifiedWithoutExceptionMessages() {
        assertEquals(ContactExportDiagnostics.Reason.TIMEOUT,ContactExportDiagnostics.reason(java.net.SocketTimeoutException("private")))
        assertEquals(ContactExportDiagnostics.Reason.DNS,ContactExportDiagnostics.reason(java.net.UnknownHostException("private")))
        assertEquals(ContactExportDiagnostics.Reason.INTERRUPTED,ContactExportDiagnostics.reason(InterruptedException("private")))
    }
}
