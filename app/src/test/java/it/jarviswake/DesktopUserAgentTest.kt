package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class DesktopUserAgentTest {
    @Test fun preservesInstalledEngineAndRemovesMobileWebViewIdentity() {
        val original = "Mozilla/5.0 (Linux; Android 16; SM-S926B Build/TEST; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.0 Mobile Safari/537.36"
        val desktop = DesktopUserAgent.from(original)
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.7390.0 Safari/537.36", desktop)
        assertEquals(desktop, DesktopUserAgent.from(desktop))
    }

    @Test fun handlesReducedAndroidUserAgent() {
        val desktop = DesktopUserAgent.from("Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36")
        assertTrue(desktop.contains("Chrome/140.0.0.0"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
    }
}
