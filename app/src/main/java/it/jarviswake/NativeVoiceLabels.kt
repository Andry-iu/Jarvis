package it.jarviswake

import java.util.Locale

/** Conservative candidates, matched exactly; actual app labels must be checked on device. */
internal object NativeVoiceLabels {
    fun normalize(value: String) = value.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    val start = setOf("voice", "voice mode", "start voice", "start voice mode", "start voice conversation", "avvia modalità vocale", "modalità vocale", "avvia conversazione vocale")
    val stop = setOf("end voice", "end voice chat", "end voice conversation", "exit voice mode", "close voice chat", "termina conversazione vocale", "chiudi modalità vocale", "esci dalla modalità vocale")
    val mute = setOf("mute microphone", "mute", "disattiva microfono", "disattiva il microfono")
    val unmute = setOf("unmute microphone", "unmute", "attiva microfono", "attiva il microfono", "riattiva microfono")
}
