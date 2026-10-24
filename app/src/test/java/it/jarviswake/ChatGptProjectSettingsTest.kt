package it.jarviswake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptProjectSettingsTest {
    @Test fun acceptsTheProjectAddressProvidedByTheUser() {
        assertTrue(ChatGptProjectSettings.isValidProjectUrl(ChatGptProjectSettings.DEFAULT_URL))
    }

    @Test fun acceptsProjectUrlsWithAnOptionalSlugAndTrailingSlash() {
        assertTrue(ChatGptProjectSettings.isValidProjectUrl(
            "https://chatgpt.com/g/g-p-0123456789abcdef0123456789abcdef-jarvisruntime/project/"
        ))
    }

    @Test fun rejectsNonHttpsUrls() {
        assertFalse(ChatGptProjectSettings.isValidProjectUrl(
            "http://chatgpt.com/g/g-p-0123456789abcdef0123456789abcdef/project"
        ))
    }

    @Test fun rejectsOtherHostsAndNonProjectPaths() {
        assertFalse(ChatGptProjectSettings.isValidProjectUrl(
            "https://example.com/g/g-p-0123456789abcdef0123456789abcdef/project"
        ))
        assertFalse(ChatGptProjectSettings.isValidProjectUrl("https://chatgpt.com/"))
    }

    @Test fun rejectsPortsCredentialsQueriesAndFragments() {
        val base = "https://chatgpt.com/g/g-p-0123456789abcdef0123456789abcdef/project"
        listOf(
            "https://user@chatgpt.com/g/g-p-0123456789abcdef0123456789abcdef/project",
            "https://chatgpt.com:443/g/g-p-0123456789abcdef0123456789abcdef/project",
            "$base?source=external",
            "$base#fragment"
        ).forEach { assertFalse(ChatGptProjectSettings.isValidProjectUrl(it)) }
    }
}
