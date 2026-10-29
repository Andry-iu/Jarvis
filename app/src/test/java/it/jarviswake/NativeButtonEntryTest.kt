package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class NativeButtonEntryTest {
    @Test fun editableOrPasswordAncestorRedactsEveryLabelButKeepsStructure() {
        val row = NativeButtonEntry("node=7 actions=16", true, true, "secret description", "secret draft", listOf("secret action")).render()
        assertFalse(row.contains("secret"))
        assertTrue(row.contains("node=7 actions=16"))
        assertTrue(row.contains("[redacted]"))
    }
    @Test fun genericClickablePageTextIsExcludedEvenWhenControlIsEnumerated() {
        val row = NativeButtonEntry("clickable=true", false, false, "Menu", "private conversation", listOf("16:Click")).render()
        assertTrue(row.contains("Menu"))
        assertTrue(row.contains("16:Click"))
        assertFalse(row.contains("private conversation"))
    }
    @Test fun unnamedButtonIsNotDroppedAndActionIdRemainsAvailable() {
        val row = NativeButtonEntry("node=9 enabled=false actions=16", false, true, null, null, listOf("16:")).render()
        assertTrue(row.contains("node=9 enabled=false"))
        assertTrue(row.contains("description=[]"))
        assertTrue(row.contains("actionLabels=[16:]"))
    }
    @Test fun buttonLabelsAreSingleLineAndBounded() {
        val row = NativeButtonEntry("node=1", false, true, "Invia\nmsg", "x".repeat(500), listOf("16:\tClick")).render()
        assertFalse(row.contains('\n'))
        assertFalse(row.contains('\t'))
        assertTrue(row.contains("Invia msg"))
        assertFalse(row.contains("x".repeat(241)))
    }
}
