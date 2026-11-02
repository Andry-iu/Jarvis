package it.jarviswake

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class MenuRefreshAndHelpTest {
    private val repositoryField=PhoneNotes::class.java.getDeclaredField("repository").apply { isAccessible=true }
    @After fun reset() { repositoryField.set(PhoneNotes,null) }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(v:View,label:String)=views(v).filterIsInstance<Button>().single { it.text.toString()==label }
    private fun has(v:View,label:String)=views(v).filterIsInstance<TextView>().any { it.text.toString()==label }
    private fun await(check:()->Boolean) {
        val deadline=System.nanoTime()+3_000_000_000L
        do { shadowOf(Looper.getMainLooper()).idle();if(check()) return;Thread.sleep(10) } while(System.nanoTime()<deadline)
        assertTrue("Notes operation did not complete",check())
    }
    private fun measure(v:View,width:Int=400) {
        val d=v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((width*d).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((800*d).toInt(),View.MeasureSpec.EXACTLY))
        v.layout(0,0,v.measuredWidth,v.measuredHeight)
    }
    private fun repository():LocalNotesRepository {
        val memory=object:NotesStorage {
            var notes=listOf<LocalNote>()
            override fun load()=notes.toList()
            override fun store(notes:List<LocalNote>) { this.notes=notes.toList() }
        }
        return LocalNotesRepository(memory,{123456L}) { UUID.randomUUID().toString() }.also { repositoryField.set(PhoneNotes,it) }
    }
    @Test fun helpOpensOnlyFromItsQuestionAndBlocksUnderlyingActionsUntilX() {
        val c=Robolectric.buildActivity(Activity::class.java).create().start().resume().visible();val a=c.get()
        val menu=SplitMenuScreen(a,"Menu","Intera sezione da non mostrare") {}
        var calls=0;val action=menu.button("Azione A","Solo informazioni A") { calls++ }
        menu.button("Azione B","Solo informazioni B") { calls+=10 };a.setContentView(menu);measure(menu)
        assertFalse(menu.helpShowing);assertFalse(has(menu,"Intera sezione da non mostrare"))
        val question=views(action.parent as View).filterIsInstance<Button>().single { it.text.toString()=="?" }
        question.performClick();assertTrue(menu.helpShowing);assertEquals(0,calls)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val root=dialog.window!!.decorView
        assertTrue(has(root,"Solo informazioni A"));assertFalse(has(root,"Solo informazioni B"))
        assertFalse(has(root,"Intera sezione da non mostrare"))
        assertEquals(0,dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        assertTrue(dialog.window!!.attributes.width<a.resources.displayMetrics.widthPixels)
        action.performClick();assertEquals(0,calls)
        button(root,"X").performClick();assertFalse(menu.helpShowing)
        action.performClick();assertEquals(1,calls);c.pause().stop().destroy()
    }
    @Test fun focusAndSwitchNeverOpenHelpAndDetachingClosesExistingDialog() {
        val c=Robolectric.buildActivity(Activity::class.java).create().start().resume().visible();val a=c.get()
        val menu=SplitMenuScreen(a,"Menu","Sezione") {}
        val input=menu.field("Campo","","Descrizione campo");val toggle=menu.toggle("Opzione",false,"Descrizione opzione") {}
        val action=menu.button("Comando","Aiuto comando") {};a.setContentView(menu)
        input.requestFocus();toggle.isChecked=true;assertFalse(menu.helpShowing)
        button(action.parent as View,"?").performClick();assertTrue(menu.helpShowing)
        a.setContentView(android.widget.LinearLayout(a));assertFalse(menu.helpShowing)
        c.pause().stop().destroy()
    }
    @Test fun refreshedNotesHaveTheirOwnRowsBelowRefreshAndNewAfterAsyncLayout() {
        val r=repository();repeat(5) { r.save("","Nota $it","Testo",0) }
        val c=Robolectric.buildActivity(LocalNotesActivity::class.java).create().start().resume().visible();val a=c.get()
        await { has(a.window.decorView,"Appunti locali: 5") };measure(a.window.decorView)
        val refresh=button(a.window.decorView,"Aggiorna appunti");val new=button(a.window.decorView,"Nuovo appunto")
        assertSame(refresh.parent.parent,new.parent.parent)
        assertTrue((refresh.parent as View).left<(new.parent as View).left)
        for(i in 0..4) {
            val n=button(a.window.decorView,"Nota $i")
            assertTrue("note=$i button=${n.width}x${n.height} card=${(n.parent as View).width}x${(n.parent as View).height} row=${(n.parent.parent as View).width}x${(n.parent.parent as View).height}",n.width>0 && n.height>0);assertNotSame(refresh.parent.parent,n.parent.parent)
        }
        val back=button(a.window.decorView,"Indietro");val card=back.parent as View;val parent=card.parent as ViewGroup
        assertSame(card,parent.getChildAt(parent.childCount-1));assertTrue(back.width>new.width*1.9)
        button(a.window.decorView,"Aggiorna appunti").performClick();await { button(a.window.decorView,"Aggiorna appunti").isEnabled }
        assertEquals(5,views(a.window.decorView).filterIsInstance<Button>().count { it.text.startsWith("Nota ") })
        c.pause().stop().destroy()
    }
    @Test fun returningFromAnotherAppRereadsBothNotesWithoutReplacingFirst() {
        val r=repository();val one=r.save("","Primo","Testo uno",0)
        val c=Robolectric.buildActivity(LocalNotesActivity::class.java).create().start().resume().visible();val a=c.get()
        await { has(a.window.decorView,"Appunti locali: 1") };c.pause().stop()
        val two=r.save("","Secondo","Testo due",0)
        c.start().resume().visible();await { has(a.window.decorView,"Appunti locali: 2") }
        assertNotNull(button(a.window.decorView,one.title));assertNotNull(button(a.window.decorView,two.title))
        assertEquals(2,r.snapshot().size);c.pause().stop().destroy()
    }
    @Test fun foregroundRemoteChangeRefreshesListButNeverOverwritesAnEditorDraft() {
        val r=repository();r.save("","Primo","Testo",0)
        val c=Robolectric.buildActivity(LocalNotesActivity::class.java).create().start().resume().visible();val a=c.get()
        await { has(a.window.decorView,"Appunti locali: 1") }
        r.save("","Secondo","Altro",0);await { has(a.window.decorView,"Appunti locali: 2") }
        button(a.window.decorView,"Nuovo appunto").performClick()
        val body=views(a.window.decorView).filterIsInstance<EditText>().single { it.contentDescription=="Testo dell’appunto" }
        body.setText("Bozza da conservare")
        r.save("","Terzo","Remoto",0);shadowOf(Looper.getMainLooper()).idle()
        c.pause().stop().start().resume().visible()
        assertEquals("Bozza da conservare",body.text.toString());assertTrue(body.isAttachedToWindow)
        button(a.window.decorView,"Indietro").performClick();await { has(a.window.decorView,"Appunti locali: 3") }
        c.pause().stop().destroy()
    }
    @Test fun homeStartsWithNotesAndContactsTogetherAndGreetingWrapsFiveRowsAndSavesAllLines() {
        val app=RuntimeEnvironment.getApplication();NativeChatSettings.enable(app,true)
        val c=Robolectric.buildActivity(MainActivity::class.java).create();val a=c.get();val root=a.window.decorView
        assertEquals("Appunti locali",views(root).filterIsInstance<Button>().first().text.toString())
        val notes=button(root,"Appunti locali");val contacts=button(root,"Preferiti e cache contatti")
        measure(root)
        val notesCard=notes.parent as View;val contactsCard=contacts.parent as View
        assertSame(notesCard.parent,contactsCard.parent)
        assertEquals(notesCard.top,contactsCard.top);assertTrue(notesCard.right<=contactsCard.left)
        assertTrue(notesCard.width>0 && contactsCard.width>0)
        shadowOf(app).clearNextStartedActivities();contacts.performClick()
        assertEquals(ContactCacheActivity::class.java.name,shadowOf(app).nextStartedActivity?.component?.className)
        val setup=Robolectric.buildActivity(BridgeSetupActivity::class.java,android.content.Intent(app,BridgeSetupActivity::class.java).putExtra("section","actions")).create()
        assertFalse(views(setup.get().window.decorView).filterIsInstance<Button>().any {it.text.toString()=="Preferiti e cache contatti"})
        setup.destroy()
        button(root,"Configurazione").performClick();button(root,"Voce e saluto").performClick()
        val field=views(root).filterIsInstance<EditText>().single { it.contentDescription=="Frase del saluto" }
        assertTrue(field.minLines>=5)
        val text=(1..7).joinToString("\n") { "Riga $it con testo del saluto abbastanza lungo per verificare il ritorno a capo" }
        field.setText(text);measure(root,360)
        assertTrue(field.width>0);assertTrue(field.layout.lineCount>=7)
        assertTrue(field.width<root.width);assertTrue(field.maxLines>=7)
        button(root,"Salva frase").performClick();assertEquals(text,VoiceGreetingSettings.get(a))
        assertFalse(VoiceCycle.active);c.destroy()
    }
}
