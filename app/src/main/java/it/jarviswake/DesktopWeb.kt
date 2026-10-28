package it.jarviswake

import android.webkit.WebSettings

/** One desktop request policy for the preloader, login and Voice surfaces. */
internal object DesktopWeb {
    fun configure(settings: WebSettings) {
        val desktopAgent = DesktopUserAgent.from(settings.userAgentString)
        // Avoid reloading the handed-off WebView by setting an identical UA again.
        if (settings.userAgentString != desktopAgent) settings.userAgentString = desktopAgent
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    }

    // A mobile meta viewport would otherwise keep the responsive phone layout even
    // with a desktop UA. Change layout only; the Voice locator reads live CSS sizes.
    val viewportScript = """
        (() => {
          if (!document.head) return;
          let viewport = document.querySelector('meta[name="viewport"]');
          if (!viewport) {
            viewport = document.createElement('meta');
            viewport.name = 'viewport';
            document.head.appendChild(viewport);
          }
          viewport.content = 'width=980';
        })();
    """.trimIndent()
}
