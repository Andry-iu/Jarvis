package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class WebNavigationPolicyTest {
    @Test fun permitsInternalVerificationFramesButNotExternalSchemes() {
        for (url in listOf("about:blank", "about:srcdoc")) {
            assertTrue(WebNavigationPolicy.allowed(url, false))
            assertFalse(WebNavigationPolicy.allowed(url, true))
        }
        for (url in listOf("javascript:alert(1)", "file:///data/file", "intent://app", "http://example.com", "about:config")) {
            assertFalse(WebNavigationPolicy.allowed(url, false))
        }
        assertTrue(WebNavigationPolicy.allowed("https://challenges.cloudflare.com/", false))
        assertTrue(WebNavigationPolicy.allowed("https://chatgpt.com/", true))
    }
    @Test fun requiresExplicitChallengeHeaderNotJust403Status() {
        assertTrue(WebNavigationPolicy.challenged(mapOf("CF-Mitigated" to "challenge")))
        assertFalse(WebNavigationPolicy.challenged(mapOf("Server" to "cloudflare")))
        assertFalse(WebNavigationPolicy.challenged(mapOf("cf-mitigated" to "other")))
        assertFalse(WebNavigationPolicy.challenged(null))
    }
}
