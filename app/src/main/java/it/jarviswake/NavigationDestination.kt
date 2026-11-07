package it.jarviswake

import java.text.Normalizer
import java.util.Locale

/** Resolves only an exact local address label. All other place searches belong to Maps. */
internal object NavigationDestination {
    data class Plan(val query:String,val note:LocalNote?=null)
    private fun normalized(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim().replace(Regex("\\s+")," ")
    private fun label(value:String):String {
        val text=normalized(value).removePrefix("indirizzo ").removePrefix("di ")
        return when(text) { "casa mia","mia casa","la mia casa","a casa" -> "casa";else -> text }
    }
    private fun personal(value:String)=value=="casa" || value.startsWith("casa di ") || value=="lavoro" || value=="ufficio"
    private val street=Regex("\\b(via|viale|vicolo|piazza|piazzale|corso|strada|contrada|largo|street|road|avenue|lane|boulevard)\\b",RegexOption.IGNORE_CASE)
    private val coordinates=Regex("^[+-]?\\d{1,3}(?:\\.\\d+)?\\s*,\\s*[+-]?\\d{1,3}(?:\\.\\d+)?$")
    private fun concrete(value:String):Boolean {
        if(coordinates.matches(value)) {
            val parts=value.split(',').map { it.trim().toDouble() }
            return parts[0] in -90.0..90.0 && parts[1] in -180.0..180.0
        }
        return street.containsMatchIn(value) && value.any { it.isDigit() }
    }
    private fun valid(value:String)=BridgeProtocol.validText(value,300) && value.none {it=='\n' || it=='\r' || it=='\t'} && !value.contains("://")
    fun resolve(input:String,notesEnabled:Boolean,notes:()->List<LocalNote>):Plan {
        val query=input.trim()
        if(!valid(query)) throw BridgeFailure("destination_invalid")
        if(concrete(query)) return Plan(query)
        val wanted=label(query)
        if(!notesEnabled) {
            if(personal(wanted)) throw BridgeFailure("notes_disabled")
            return Plan(query)
        }
        val matches=notes().filter {label(it.title)==wanted}
        if(matches.size>1) throw BridgeFailure("destination_ambiguous")
        val note=matches.singleOrNull() ?: run {
            if(personal(wanted)) throw BridgeFailure("destination_not_found")
            return Plan(query)
        }
        // Notes remain location data, never executable instructions or arbitrary intents.
        val prefix=Regex("^(?:indirizzo\\s*:\\s*|(?:${Regex.escape(wanted)}|${Regex.escape(note.title)})\\s*(?:è\\s+(?:in|a)\\s+|:\\s*))",RegexOption.IGNORE_CASE)
        val address=note.text.trim().replaceFirst(prefix,"").trim().removeSuffix(".")
        if(!valid(address) || !concrete(address)) throw BridgeFailure("destination_invalid")
        return Plan(address,note)
    }
}
