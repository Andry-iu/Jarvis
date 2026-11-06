package it.jarviswake

import android.app.Activity
import android.app.NotificationManager
import android.Manifest
import android.provider.Settings
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real Activity/View construction, which the pure navigation tests do not execute. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class MenuActivityStartupTest {
    @After fun resetServiceHint() { State.running=false }
    private fun mainActivity():org.robolectric.android.controller.ActivityController<MainActivity> {
        NativeChatSettings.enable(RuntimeEnvironment.getApplication(),true)
        return Robolectric.buildActivity(MainActivity::class.java).create()
    }
    private fun buttons(view:View):List<Button> = (if(view is Button) listOf(view) else emptyList())+
        if(view is ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) } else emptyList()

    @Test fun openingMessageHasItsOwnMultilineEditorSaveAndResetWithoutChangingTheGreeting() {
        val field=PreparedChat::class.java.getDeclaredField("ledger").apply {isAccessible=true};val old=field.get(null)
        val cycle=VoiceCycle::class.java.getDeclaredField("state").apply {isAccessible=true};val previous=cycle.get(null)
        field.set(null,PreparedChatLedger(null) {});cycle.set(null,VoiceCycleState())
        val controller=mainActivity();val a=controller.get()
        try {
            buttons(a.window.decorView).single {it.text.toString()=="Configurazione"}.performClick()
            buttons(a.window.decorView).single {it.text.toString()=="Voce e saluto"}.performClick()
            fun inputs(v:View):List<android.widget.EditText> = (if(v is android.widget.EditText) listOf(v) else emptyList())+
                if(v is ViewGroup) (0 until v.childCount).flatMap {inputs(v.getChildAt(it))} else emptyList()
            val editors=inputs(a.window.decorView);assertEquals(2,editors.size)
            val greeting=editors[1].text.toString()
            editors[0].setText("Messaggio personalizzato\nSeconda riga")
            buttons(a.window.decorView).single {it.text.toString()=="Salva messaggio prima di Voice"}.performClick()
            assertEquals("Messaggio personalizzato\nSeconda riga",OpeningMessageSettings.get(a))
            assertEquals(greeting,VoiceGreetingSettings.get(a))
            editors[0].setText("  ")
            buttons(a.window.decorView).single {it.text.toString()=="Salva messaggio prima di Voice"}.performClick()
            assertNull(editors[0].error);assertEquals("",OpeningMessageSettings.get(a))
            buttons(a.window.decorView).single {it.text.toString()=="Ripristina messaggio predefinito"}.performClick()
            assertEquals(OpeningMessagePolicy.TEXT,OpeningMessageSettings.get(a))
            assertEquals(greeting,VoiceGreetingSettings.get(a))
            editors[1].setText(" \n ")
            buttons(a.window.decorView).single {it.text.toString()=="Salva frase"}.performClick()
            assertEquals("",VoiceGreetingSettings.get(a));assertNull(editors[1].error)
            assertEquals(OpeningMessagePolicy.TEXT,OpeningMessageSettings.get(a))
        } finally {controller.destroy();field.set(null,old);cycle.set(null,previous)}
    }
    @Test fun phonebookIsReachableFromTheMainLinkMenuAndStartsWithDedicatedConsentOff() {
        val c=RuntimeEnvironment.getApplication()
        PhoneContactExport.enable(c,false)
        val main=mainActivity();val a=main.get()
        buttons(a.window.decorView).single {it.text.toString()=="Configurazione"}.performClick()
        buttons(a.window.decorView).single {it.text.toString()==a.getString(R.string.menu_link)}.performClick()
        buttons(a.window.decorView).single {it.text.toString()=="Rubrica verso workspace"}.performClick()
        val intent=shadowOf(a).nextStartedActivity
        assertEquals("contacts",intent.getStringExtra("section"))
        val setup=Robolectric.buildActivity(BridgeSetupActivity::class.java,intent).create()
        assertFalse(PhoneContactExport.enabled(c))
        assertTrue(buttons(setup.get().window.decorView).any {it.text.toString()=="Autorizza sola lettura rubrica"})
        setup.destroy();main.destroy()
    }
    @Test fun uncreatedDecorMustBeCheckedBeforeAskingForTheInsetsController() {
        val a=Robolectric.buildActivity(Activity::class.java).get()
        assertNull(a.window.peekDecorView())
        assertThrows(NullPointerException::class.java) { a.window.insetsController }
        a.window.hideMenuKeyboard()
        assertNull(a.window.peekDecorView())
    }

    @Test fun guideColdStartBuildsIndexBeforeTheWindowIsShown() {
        val controller=Robolectric.buildActivity(GuideActivity::class.java).create()
        val activity=controller.get()
        assertTrue(buttons(activity.window.decorView).any { it.text.toString()=="Esporta guida completa" })
        controller.destroy()
    }
    @Test fun notesColdStartBuildsLocalListBeforeTheWindowIsShown() {
        val controller=Robolectric.buildActivity(LocalNotesActivity::class.java).create()
        val activity=controller.get()
        assertTrue(buttons(activity.window.decorView).any { it.text.toString()=="Nuovo appunto" })
        controller.destroy()
    }
    @Test fun guideTopicCanBeOpenedAndReturnedToIndexAfterAttachment() {
        val controller=Robolectric.buildActivity(GuideActivity::class.java).create().start().resume().visible()
        val a=controller.get()
        buttons(a.window.decorView).single { it.text.toString()=="Parola wake: pronuncia e ascolto" }.performClick()
        buttons(a.window.decorView).single { it.text.toString()=="Indice e ricerca" }.performClick()
        assertTrue(buttons(a.window.decorView).any { it.text.toString()=="Cerca" })
        controller.pause().stop().destroy()
    }
    @Test fun noteEditorCanOpenFromTheListWithoutLosingItsExit() {
        val controller=Robolectric.buildActivity(LocalNotesActivity::class.java).create()
        val a=controller.get()
        buttons(a.window.decorView).single { it.text.toString()=="Nuovo appunto" }.performClick()
        assertTrue(buttons(a.window.decorView).any { it.text.toString()=="Salva appunto" })
        buttons(a.window.decorView).single { it.text.toString()=="Indietro" }.performClick()
        assertTrue(buttons(a.window.decorView).any { it.text.toString()=="Nuovo appunto" })
        controller.destroy()
    }
    @Test fun everyConfigurationCategoryBuildsAndReturnsThroughTheRealButtons() {
        val controller=mainActivity();val a=controller.get()
        buttons(a.window.decorView).single { it.text.toString()=="Configurazione" }.performClick()
        for(label in listOf("Permessi e assistente","ChatGPT e progetto","Voce e saluto","Audio di avvio","Cover","Collegamento e azioni")) {
            buttons(a.window.decorView).single { it.text.toString()==label }.performClick()
            buttons(a.window.decorView).single { it.text.toString()=="Indietro" }.performClick()
            assertTrue(buttons(a.window.decorView).any { it.text.toString()=="Configurazione guidata" })
        }
        controller.destroy()
    }
    @Test fun manualVoiceWithMissingAccessibilityExplainsTheBlockBeforeStartingCover() {
        val app=RuntimeEnvironment.getApplication()
        Settings.Secure.putString(app.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"")
        val controller=mainActivity();val a=controller.get()
        shadowOf(app).clearNextStartedActivities()
        buttons(a.window.decorView).single { it.text.toString()=="Parla con Jarvis" }.performClick()
        assertFalse(VoiceCycle.active)
        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(State.getAttempt(a).contains("Automazione Voice non abilitata"))
        controller.destroy()
    }
    @Test fun manualWakeWithStaleRunningHintStillRequestsTheActualService() {
        val app=RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        State.running=true // A hint alone must not suppress an explicit start with no service instance.
        val controller=mainActivity();val a=controller.get()
        shadowOf(app).clearStartedServices()
        buttons(a.window.decorView).single { it.text.toString()=="Avvia ascolto Hey Jarvis" }.performClick()
        assertEquals(WakeService::class.java.name,shadowOf(app).nextStartedService?.component?.className)
        controller.destroy()
    }
    @Test fun backIsTheLastNormalControlInSectionsAndWizard() {
        val controller=mainActivity();val a=controller.get()
        buttons(a.window.decorView).single { it.text.toString()=="Configurazione" }.performClick()
        fun verifyLastBack() {
            val back=buttons(a.window.decorView).single { it.text.toString()=="Indietro" }
            val card=back.parent as View;val parent=card.parent as ViewGroup
            assertSame(card,parent.getChildAt(parent.childCount-1))
            assertTrue(parent.parent is android.widget.ScrollView)
        }
        verifyLastBack()
        buttons(a.window.decorView).single { it.text.toString()=="Configurazione guidata" }.performClick()
        verifyLastBack()
        buttons(a.window.decorView).single { it.text.toString()=="Avanti" }.performClick()
        verifyLastBack()
        controller.destroy()
    }
}
