package it.jarviswake

import android.app.Application
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class OpeningMessageSettingsTest {
    private val ledgerField=PreparedChat::class.java.getDeclaredField("ledger").apply {isAccessible=true}
    private val cycleField=VoiceCycle::class.java.getDeclaredField("state").apply {isAccessible=true}
    private val previousLedger=ledgerField.get(null)
    private val previousCycle=cycleField.get(null)
    private lateinit var app:Application
    private lateinit var ledger:PreparedChatLedger
    @Before fun setup() {
        app=RuntimeEnvironment.getApplication();cycleField.set(null,VoiceCycleState())
        ledger=PreparedChatLedger(null) {};ledgerField.set(null,ledger)
        app.getSharedPreferences("opening_message",0).edit().clear().commit()
        app.getSharedPreferences("prepared_chat_settings",0).edit().putBoolean("enabled",false).commit()
    }
    @After fun clean() {PreparedChat.stop(app);ledgerField.set(null,previousLedger);cycleField.set(null,previousCycle)}
    @Test fun customMultilineTextIsPersistedSeparatelyFromTheGreetingAndCanBeReset() {
        assertEquals(OpeningMessagePolicy.TEXT,OpeningMessageSettings.get(app))
        val greeting=VoiceGreetingSettings.get(app)
        assertTrue(OpeningMessageSettings.save(app,"  Prima riga\r\nSeconda riga: \"Jarvis\"  "))
        assertEquals("Prima riga\nSeconda riga: \"Jarvis\"",OpeningMessageSettings.get(app.applicationContext))
        assertEquals(greeting,VoiceGreetingSettings.get(app))
        assertTrue(OpeningMessageSettings.reset(app));assertEquals(OpeningMessagePolicy.TEXT,OpeningMessageSettings.get(app))
    }
    @Test fun blankTextIsPersistedButChangesDuringAnActiveCycleRemainRejected() {
        assertTrue(OpeningMessageSettings.save(app,"Messaggio personalizzato"))
        assertTrue(OpeningMessageSettings.save(app," \n "));assertEquals("",OpeningMessageSettings.get(app))
        cycleField.set(null,VoiceCycleState().apply {begin("active",0)})
        assertFalse(OpeningMessageSettings.save(app,"Altro testo"));assertFalse(OpeningMessageSettings.reset(app))
        assertEquals("",OpeningMessageSettings.get(app))
    }
    @Test fun bothEmptyChoicesSurviveReadsAndOnlyExplicitResetRestoresTheirDefaults() {
        assertTrue(OpeningMessageSettings.save(app," \n\t "))
        assertTrue(VoiceGreetingSettings.save(app," \n\t "))
        assertEquals("",OpeningMessageSettings.get(app.applicationContext))
        assertEquals("",VoiceGreetingSettings.get(app.applicationContext))
        assertTrue(OpeningMessageSettings.reset(app));assertEquals(OpeningMessagePolicy.TEXT,OpeningMessageSettings.get(app))
        assertEquals("",VoiceGreetingSettings.get(app))
        VoiceGreetingSettings.reset(app);assertEquals(VoiceGreetingSettings.DEFAULT_PHRASE,VoiceGreetingSettings.get(app))
    }
    @Test fun savingTheUnchangedTextKeepsAnExistingPreparation() {
        val project=ChatGptProjectSettings.getUrl(app)
        val a=ledger.begin(project,NativeChatSettings.project(app),System.currentTimeMillis())!!
        assertTrue(OpeningMessageSettings.save(app,"  ${OpeningMessagePolicy.TEXT}  "))
        assertEquals(a.attempt,ledger.record!!.attempt)
        assertTrue(OpeningMessageSettings.save(app,"Nuovo messaggio"));assertNull(ledger.record)
    }
}
