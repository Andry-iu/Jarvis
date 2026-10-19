package it.jarviswake

object WakePhrase {
    fun matches(text: String): Boolean = text.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z ]"), " ").trim().replace(Regex(" +"), " ") == "hey jarvis"
}
