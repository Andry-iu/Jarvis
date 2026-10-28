package it.jarviswake

import android.webkit.WebSettings

/** Desktop layout shared by login and Voice, retaining the real WebView identity. */
internal object DesktopWeb {
    fun configure(settings: WebSettings) {
        // Leave the runtime UA and Client Hints intact, including during handoff.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    }

    // A mobile meta viewport would otherwise keep the responsive phone layout even
    // on the real Android runtime. Change layout only; Voice reads live CSS sizes.
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
