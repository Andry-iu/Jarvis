package it.jarviswake

import android.Manifest
import android.app.KeyguardManager
import android.os.SystemClock
import android.provider.ContactsContract
import org.json.JSONObject
import org.junit.*
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
class FavoriteOperationsTest {
    private val fr=BridgeContactFavorites.read;private val fw=BridgeContactFavorites.write
    private val cr=BridgeContactCache.read;private val cw=BridgeContactCache.write
    private val rr=BridgeContacts.readStore;private val rw=BridgeContacts.writeStore
    private var saved=JSONObject();private var cached=JSONObject();private var refs=JSONObject()
    private lateinit var provider:BridgeDirectContactTest.Provider
    private val app get()=RuntimeEnvironment.getApplication()
    private val device="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
    @Before fun setup() {
        BridgeContactFavorites.read={JSONObject(saved.toString())};BridgeContactFavorites.write={_,v->saved=JSONObject(v.toString())}
        BridgeContactCache.read={JSONObject(cached.toString())};BridgeContactCache.write={_,v->cached=JSONObject(v.toString())}
        BridgeContacts.readStore={JSONObject(refs.toString())};BridgeContacts.writeStore={_,v->refs=JSONObject(v.toString())}
        BridgeSettings.enable(app,true);BridgeSettings.enableActions(app,false);BridgeContactFavorites.enableRemote(app,true)
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        provider=Robolectric.buildContentProvider(BridgeDirectContactTest.Provider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        shadowOf(app).clearNextStartedActivities()
    }
    @After fun reset() {BridgeContactFavorites.read=fr;BridgeContactFavorites.write=fw;BridgeContactCache.read=cr;BridgeContactCache.write=cw;BridgeContacts.readStore=rr;BridgeContacts.writeStore=rw}
    private fun run(op:String,p:JSONObject,permitted:Boolean=true):JSONObject = BridgeActions.execute(app,device,JSONObject().put("requestId",device).put("operation",op).put("params",p),SystemClock.elapsedRealtime()+25000){permitted}
    private fun args(f:JSONObject)=JSONObject().put("favorite_id",f.getString("favorite_id")).put("expected_revision",f.getInt("revision"))
    @Test fun contactCanBeAddedRenamedAndRenumberedWithoutAnyAndroidContactWrites() {
        val ref=BridgeContacts.search(app,device,"Test Contact").getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        val first=run("add_favorite",JSONObject().put("contact_ref",ref)).getJSONObject("favorite")
        saved.getJSONObject("items").getJSONObject(first.getString("favorite_id")).remove("book_name") // Existing .90 record.
        val rename=run("rename_favorite",args(first).put("name","Home Alias")).getJSONObject("favorite")
        val named=BridgeContacts.search(app,device,"Home Alias").getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        val contact=BridgeContacts.resolve(app,device,named)
        assertEquals("Home Alias",contact.name);assertEquals("+39000000001",contact.number)
        BridgeContactCache.remember(app,contact)
        val changed=run("change_favorite_number",args(rename).put("number","+39000000009"))
        assertEquals("favorite_updated",changed.getString("code"));assertEquals("cleared",changed.getString("cache_status"))
        val value=changed.getJSONObject("favorite");assertEquals("different",value.getString("book_conflict"))
        assertTrue(BridgeContactCache.entries(app).isEmpty());assertEquals("+39000000001",provider.rows[0][2]);assertEquals(0,provider.writes)
        assertEquals("contact_changed",assertThrows(BridgeFailure::class.java) {BridgeContacts.resolve(app,device,named)}.code)
        val current=BridgeContacts.search(app,device,"Home Alias").getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        assertEquals("+39000000009",BridgeContacts.resolve(app,device,current).number)
        BridgeContacts.importFavorites(app);assertEquals("+39000000009",BridgeContactFavorites.find(app,value.getString("favorite_id"))!!.getString("number"))
        assertFalse(changed.toString().contains("+39000000009"));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun changedNumberWithSameNameRemainsDistinctFromTheOldBookNumberAndNeedsAChoice() {
        val f=BridgeContactFavorites.save(app,"Test Contact","+39000000001",1)
        val metadata=FavoriteOperations.metadata(app,f)
        run("change_favorite_number",args(metadata).put("number","+39000000009"))
        BridgeSettings.enableActions(app,true)
        val result=run("call_by_name",JSONObject().put("name","Test Contact"))
        assertEquals("contacts_found",result.getString("code"));assertEquals(2,result.getJSONArray("contacts").length())
        val labels=(0..1).map {result.getJSONArray("contacts").getJSONObject(it).getString("label")}
        assertTrue(labels.any {it.startsWith("Rubrica")});assertTrue(labels.contains("Preferito JarvisWake"))
        assertNull(shadowOf(app).nextStartedActivity);assertEquals(0,provider.writes)
    }
    @Test fun consentLockLeaseAndRevisionPreventUnexpectedChanges() {
        val p=JSONObject().put("name","Manual").put("number","+39000000009")
        BridgeContactFavorites.enableRemote(app,false);assertEquals("favorites_disabled",run("create_favorite",p).getString("code"))
        BridgeContactFavorites.enableRemote(app,true)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        assertEquals("locked",run("create_favorite",p).getString("code"))
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        assertEquals("expired_before_dispatch",run("create_favorite",p,false).getString("code"))
        val f=run("create_favorite",p).getJSONObject("favorite")
        val changed=run("rename_favorite",args(f).put("name","Renamed")).getJSONObject("favorite")
        assertEquals("favorite_conflict",run("delete_favorite",args(f)).getString("code"))
        assertEquals("favorite_deleted",run("delete_favorite",args(changed)).getString("code"))
        assertTrue(BridgeContactFavorites.entries(app).isEmpty());assertEquals(0,provider.writes)
    }
    @Test fun cacheFailureDoesNotHideTheCommittedNumberChangeOrCauseAnotherWrite() {
        val f=BridgeContactFavorites.save(app,"Manual","+39000000009")
        BridgeContactCache.write={_,_->throw IllegalStateException("test cache unavailable")}
        val response=run("change_favorite_number",args(FavoriteOperations.metadata(app,f)).put("number","+39000000008"))
        assertEquals("favorite_updated",response.getString("code"));assertEquals("unconfirmed",response.getString("cache_status"))
        assertEquals("+39000000008",BridgeContactFavorites.find(app,f.getString("id"))!!.getString("number"))
        assertEquals("favorite_conflict",run("change_favorite_number",args(FavoriteOperations.metadata(app,f)).put("number","+39000000007")).getString("code"))
    }
}
