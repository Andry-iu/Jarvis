package it.jarviswake

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class ContactFavoritesUiTest {
    private val oldRead=BridgeContactFavorites.read;private val oldWrite=BridgeContactFavorites.write
    private val cacheRead=BridgeContactCache.read;private val cacheWrite=BridgeContactCache.write
    private var saved=JSONObject();private var cache=JSONObject()
    @Before fun setup() {
        BridgeContactFavorites.read={JSONObject(saved.toString())};BridgeContactFavorites.write={_,j->saved=JSONObject(j.toString())}
        BridgeContactCache.read={JSONObject(cache.toString())};BridgeContactCache.write={_,j->cache=JSONObject(j.toString())}
    }
    @After fun reset() {BridgeContactFavorites.read=oldRead;BridgeContactFavorites.write=oldWrite;BridgeContactCache.read=cacheRead;BridgeContactCache.write=cacheWrite}
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap {all(v.getChildAt(it))} else emptyList()
    private fun click(a:Activity,text:String) {all(a.window.decorView).filterIsInstance<Button>().single {it.text.toString()==text}.performClick()}
    @Test fun manualEditorValidatesWithoutLosingFieldsAndSavesToFavoritesOnly() {
        val c=Robolectric.buildActivity(ContactCacheActivity::class.java).setup().visible();val a=c.get()
        click(a,"Preferiti");click(a,"Nome e numero manuali")
        val fields=all(a.window.decorView).filterIsInstance<EditText>()
        val name=fields.single {it.hint=="Nome del preferito"};val number=fields.single {it.hint=="Numero di telefono"}
        name.setText("Home Test");number.setText("*123#");click(a,"Salva preferito")
        assertTrue(BridgeContactFavorites.entries(a).isEmpty());assertEquals("Home Test",name.text.toString())
        number.setText("+39 000 000 001");click(a,"Salva preferito")
        assertEquals("+39000000001",BridgeContactFavorites.entries(a).single().getString("number"))
        assertTrue(BridgeContactCache.entries(a).isEmpty())
        assertTrue(all(a.window.decorView).filterIsInstance<Button>().any {it.text.toString().startsWith("Home Test\nManuale")})
        val back=ContactCacheActivity::class.java.getDeclaredField("backCallback").apply {isAccessible=true}.get(a) as android.window.OnBackInvokedCallback
        back.onBackInvoked()
        assertTrue(all(a.window.decorView).filterIsInstance<Button>().any {it.text=="Cache frequenti"})
        c.pause().stop().destroy()
    }
    @Test fun bookPickerRequestsANumberAndItsResultCreatesAFavoriteWithoutImportingAllContacts() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS)
        val provider=Robolectric.buildContentProvider(BridgeDirectContactTest.Provider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        val c=Robolectric.buildActivity(ContactCacheActivity::class.java).setup().visible();val a=c.get()
        click(a,"Preferiti");click(a,"Aggiungi dalla rubrica")
        val picked=shadowOf(a).nextStartedActivityForResult
        assertEquals(Intent.ACTION_PICK,picked.intent.action);assertEquals(92,picked.requestCode)
        val handler=ContactCacheActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply {isAccessible=true}
        handler.invoke(a,92,Activity.RESULT_OK,Intent().setData(Uri.parse("content://com.android.contacts/data/1")))
        assertEquals(1,BridgeContactFavorites.entries(a).size);assertEquals("book",BridgeContactFavorites.entries(a).single().getString("kind"))
        assertEquals(1,provider.rows.size);assertTrue(BridgeContactCache.entries(a).isEmpty())
        c.pause().stop().destroy()
    }
    @Test fun conflictAlertAndContextActionsStayLocalWhileRemoteRefreshPreservesDrafts() {
        val app=RuntimeEnvironment.getApplication();shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        val provider=Robolectric.buildContentProvider(BridgeDirectContactTest.Provider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        val f=BridgeContactFavorites.save(app,"Test Contact","+39000000001",1)
        BridgeContactFavorites.change(app,f.getString("id"),f.getInt("revision"),number="+39000000009")
        val c=Robolectric.buildActivity(ContactCacheActivity::class.java).setup().visible();val a=c.get();click(a,"Preferiti")
        fun await(check:()->Boolean) {val until=System.nanoTime()+3_000_000_000L;do {shadowOf(android.os.Looper.getMainLooper()).idle();if(check())return;Thread.sleep(10)}while(System.nanoTime()<until);assertTrue(check())}
        await {all(a.window.decorView).filterIsInstance<Button>().any {it.text.toString().contains("⚠")}}
        val card=all(a.window.decorView).filterIsInstance<Button>().single {it.text.toString().contains("⚠")}
        all(card.parent as View).filterIsInstance<Button>().single {it.text=="⋮"}.performClick()
        val popup=org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
        assertEquals(listOf("Aiuto","Rinomina","Cambia numero","Elimina"),(0 until popup.menu.size()).map {popup.menu.getItem(it).title.toString()})
        shadowOf(popup).onMenuItemClickListener.onMenuItemClick(popup.menu.getItem(1))
        val field=all(a.window.decorView).filterIsInstance<EditText>().single();field.setText("New Alias")
        click(a,"Salva modifica")
        assertEquals("New Alias",BridgeContactFavorites.find(app,f.getString("id"))!!.getString("name"));assertEquals(0,provider.writes)
        click(a,"Nome e numero manuali")
        val draft=all(a.window.decorView).filterIsInstance<EditText>().single {it.hint=="Nome del preferito"};draft.setText("Draft")
        BridgeContactFavorites.save(app,"Remote Example","+39000000008")
        shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("Draft",draft.text.toString());assertTrue(draft.isAttachedToWindow)
        click(a,"Indietro");assertTrue(all(a.window.decorView).filterIsInstance<Button>().any {it.text.toString().startsWith("Remote Example")})
        c.pause().stop().destroy()
    }

}
