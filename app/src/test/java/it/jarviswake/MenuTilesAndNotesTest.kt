package it.jarviswake

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
class MenuTilesAndNotesTest {
    private val repositoryField=PhoneNotes::class.java.getDeclaredField("repository").apply { isAccessible=true }
    @After fun reset() { repositoryField.set(PhoneNotes,null);RuntimeEnvironment.setFontScale(1f) }
    private fun descendants(v:View):List<View> = listOf(v)+
        if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun button(v:View,label:String)=descendants(v).filterIsInstance<Button>().single { it.text.toString()==label }
    private fun measure(v:View,width:Int=400) {
        val d=v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((width*d).toInt(),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((1000*d).toInt(),View.MeasureSpec.EXACTLY))
        v.layout(0,0,v.measuredWidth,v.measuredHeight)
    }
    @Test fun pairedTilesAndOddLastTileKeepHalfWidthWithFullWidthBackLast() {
        val menu=SplitMenuScreen(RuntimeEnvironment.getApplication(),"Sezione","Istruzioni") {}
        val one=menu.button("Uno") {};val two=menu.button("Due") {};val odd=menu.button("Tre") {}
        measure(menu)
        assertSame(one.parent.parent,two.parent.parent);assertNotSame(one.parent.parent,odd.parent.parent)
        assertTrue(kotlin.math.abs(one.width-two.width)<=1);assertTrue(kotlin.math.abs(one.width-odd.width)<=1)
        assertTrue(one.width>0);assertTrue(kotlin.math.abs(one.width-one.height)<=1)
        assertEquals(one.height,two.height);assertTrue((two.parent as View).left>=(one.parent as View).right)
        val back=button(menu,"Indietro");val card=back.parent as View;val parent=card.parent as ViewGroup
        assertSame(card,parent.getChildAt(parent.childCount-1));assertTrue(parent.parent is android.widget.ScrollView)
        assertTrue(back.width>one.width*1.9);assertTrue(back.height<back.width)
        assertTrue(odd.parent.parent is ViewGroup)
    }
    @Test fun longEnlargedLabelsExpandWithoutClippingOrLosingTwoColumns() {
        RuntimeEnvironment.setFontScale(2f)
        val menu=SplitMenuScreen(RuntimeEnvironment.getApplication(),"Sezione","Istruzioni") {}
        val a=menu.button("Consenti cover sopra le altre app e autorizzazioni del telefono") {}
        val b=menu.button("Apri") {};measure(menu,320)
        assertSame(a.parent.parent,b.parent.parent);assertTrue(kotlin.math.abs(a.width-b.width)<=1);assertEquals(a.height,b.height)
        assertTrue(a.height>=a.width)
        assertTrue(a.height>=a.layout.height+a.compoundPaddingTop+a.compoundPaddingBottom)
        assertTrue((b.parent as View).left>=(a.parent as View).right)
    }
    @Test fun fieldBreaksTileGroupAndWizardNextRemainsBeforeWideBack() {
        val menu=SplitMenuScreen(RuntimeEnvironment.getApplication(),"Sezione","Istruzioni") {}
        val first=menu.button("Prima") {};val input=menu.field("Testo","","Istruzioni")
        val second=menu.button("Salva") {};menu.navigation("Indietro",{},"Avanti",{})
        measure(menu)
        assertNotSame(first.parent.parent,second.parent.parent);assertSame(second.parent.parent,button(menu,"Avanti").parent.parent)
        assertTrue(input.width>second.width*1.9)
        val back=button(menu,"Indietro");assertSame(back.parent,menu.controls.getChildAt(menu.controls.childCount-1))
    }
    private class Memory:NotesStorage {
        var notes=listOf<LocalNote>();var writes=0;var drop=false
        override fun load()=notes.toList()
        override fun store(notes:List<LocalNote>) { writes++;if(!drop) this.notes=notes.toList() }
    }
    private fun install(s:Memory)=LocalNotesRepository(s,{123456L}) { UUID.randomUUID().toString() }
        .also { repositoryField.set(PhoneNotes,it) }
    private fun await(check:()->Boolean) {
        val deadline=System.nanoTime()+3_000_000_000L
        do { shadowOf(Looper.getMainLooper()).idle();if(check()) return;Thread.sleep(10) } while(System.nanoTime()<deadline)
        assertTrue("Background notes operation did not complete",check())
    }
    @Test fun existingNoteHasDeleteCancelThenConfirmedDeletePreservesOtherNote() {
        val s=Memory();val r=install(s);val n=r.save("","Prova locale","Contenuto",0)
        val other=r.save("","Conservare","Altro",0)
        val c=Robolectric.buildActivity(LocalNotesActivity::class.java).create();val a=c.get()
        await { descendants(a.window.decorView).filterIsInstance<Button>().any { it.text.toString()==n.title } }
        button(a.window.decorView,n.title).performClick()
        await { descendants(a.window.decorView).filterIsInstance<Button>().any { it.text.toString()=="Elimina appunto" } }
        assertSame(button(a.window.decorView,"Salva appunto").parent.parent,button(a.window.decorView,"Elimina appunto").parent.parent)
        button(a.window.decorView,"Elimina appunto").performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(2,s.writes);assertEquals(n,r.read(n.id))
        button(a.window.decorView,"Elimina appunto").performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { descendants(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Appunti locali: 1" } }
        assertEquals(listOf(other),r.list(0).first);assertEquals(3,s.writes)
        c.destroy()
    }
    @Test fun confirmationRejectsConcurrentUpdateAndDoesNotRetry() {
        val s=Memory();val r=install(s);val n=r.save("","Prova locale","Prima",0)
        val c=Robolectric.buildActivity(LocalNotesActivity::class.java).create();val a=c.get()
        await { descendants(a.window.decorView).filterIsInstance<Button>().any { it.text.toString()==n.title } }
        button(a.window.decorView,n.title).performClick()
        await { descendants(a.window.decorView).filterIsInstance<Button>().any { it.text.toString()=="Elimina appunto" } }
        button(a.window.decorView,"Elimina appunto").performClick()
        val updated=r.save(n.id,n.title,"Dopo",1)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { descendants(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().startsWith("L’appunto è cambiato.") } }
        assertEquals(updated,r.read(n.id));assertEquals(2,s.writes)
        assertFalse(button(a.window.decorView,"Elimina appunto").isEnabled)
        assertFalse(button(a.window.decorView,"Salva appunto").isEnabled)
        button(a.window.decorView,"Indietro").performClick()
        assertTrue(button(a.window.decorView,"Nuovo appunto").isEnabled)
        c.destroy()
    }
    @Test fun unsavedNewNoteHasNoDelete() {
        install(Memory());val c=Robolectric.buildActivity(LocalNotesActivity::class.java).create()
        val root=c.get().window.decorView;button(root,"Nuovo appunto").performClick()
        assertTrue(descendants(root).filterIsInstance<Button>().none { it.text.toString()=="Elimina appunto" })
        assertNotNull(button(root,"Indietro"));c.destroy()
    }
}
