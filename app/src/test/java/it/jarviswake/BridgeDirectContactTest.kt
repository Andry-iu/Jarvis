package it.jarviswake

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
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
class BridgeDirectContactTest {
    class Provider:ContentProvider() {
        var writes=0
        val rows=mutableListOf(arrayOf<Any>(1L,"Test Contact","+39000000001",2,"mobile"))
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor = MatrixCursor(projection).apply {
            rows.filter {(args==null || it[0].toString()==args[0]) && (!uri.path.orEmpty().contains("/filter/") || it[1].toString().contains(uri.lastPathSegment.orEmpty(),ignoreCase=true))}.forEach {r->val cols=listOf(android.provider.ContactsContract.CommonDataKinds.Phone._ID,android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,android.provider.ContactsContract.CommonDataKinds.Phone.TYPE,android.provider.ContactsContract.CommonDataKinds.Phone.LABEL);addRow(projection!!.map {r[cols.indexOf(it)]}.toTypedArray())}
        }
        override fun getType(uri:Uri)="vnd.android.cursor.dir/phone"
        override fun insert(uri:Uri,values:ContentValues?):Uri? {writes++;return null}
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int {writes++;return 0}
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int {writes++;return 0}
    }
    private val favoriteRead=BridgeContactFavorites.read;private val favoriteWrite=BridgeContactFavorites.write
    private var favoritesStore=JSONObject()
    private val oldRead=BridgeContacts.readStore;private val oldWrite=BridgeContacts.writeStore
    private val oldCacheRead=BridgeContactCache.read;private val oldCacheWrite=BridgeContactCache.write
    private var store=JSONObject();private var cache=JSONObject()
    private lateinit var provider:Provider
    private val app get()=RuntimeEnvironment.getApplication()
    private val device="4ac0e8f0-19b7-413c-aed8-5e49089e5f18"
    @Before fun setup() {
        BridgeContactFavorites.read={JSONObject(favoritesStore.toString())};BridgeContactFavorites.write={_,j->favoritesStore=JSONObject(j.toString())}
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        BridgeSettings.enable(app,true);BridgeSettings.enableActions(app,true)
        BridgeContacts.readStore={JSONObject(store.toString())};BridgeContacts.writeStore={_,v->store=JSONObject(v.toString())}
        BridgeContactCache.read={JSONObject(cache.toString())};BridgeContactCache.write={_,v->cache=JSONObject(v.toString())}
        provider=Robolectric.buildContentProvider(Provider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        shadowOf(app).clearNextStartedActivities()
    }
    @After fun reset() {BridgeContactFavorites.read=favoriteRead;BridgeContactFavorites.write=favoriteWrite;BridgeContacts.readStore=oldRead;BridgeContacts.writeStore=oldWrite;BridgeContactCache.read=oldCacheRead;BridgeContactCache.write=oldCacheWrite}
    @Test fun ambiguousCombinedCommandReturnsChoicesWithoutStartingAnyApp() {
        provider.rows.add(arrayOf(2L,"Test Contact","+39000000002",2,"mobile"))
        BridgeContactFavorites.save(app,"Test Contact","+39000000001",1,"mobile")
        val command=JSONObject().put("requestId",device).put("operation","call_by_name").put("params",JSONObject().put("name","Test Contact"))
        val result=BridgeActions.execute(app,device,command,SystemClock.elapsedRealtime()+25000){true}
        assertEquals("contacts_found",result.getString("code"));assertEquals(2,result.getJSONArray("contacts").length())
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun changedCachedNumberIsDiscardedAndOnlyCurrentAddressBookNumberCanResolve() {
        BridgeContactCache.remember(app,BridgeContact("Test Contact","+39000000009",null,true,1,"Test Contact","mobile"))
        val r=BridgeContacts.directRef(app,device,"Test Contact",BridgeCallTiming(app,device))
        assertTrue(BridgeContactCache.entries(app).isEmpty())
        val ref=r.getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        assertEquals("+39000000001",BridgeContacts.resolve(app,device,ref).number)
        assertFalse(r.toString().contains("+39000000001"))
        provider.rows[0][2]="+39000000003"
        val failure=assertThrows(BridgeFailure::class.java) {BridgeContacts.resolve(app,device,ref)}
        assertEquals("contact_changed",failure.code)
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun uniqueCombinedResultStillRequiresCallPermissionsBeforeOpeningOrStoppingAnything() {
        val command=JSONObject().put("requestId",device).put("operation","call_by_name").put("params",JSONObject().put("name","Test Contact"))
        val r=BridgeActions.execute(app,device,command,SystemClock.elapsedRealtime()+25000){true}
        assertEquals("permission_required",r.getString("code"))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun manualFavoriteResolvesWithoutCreatingAContactAndRemovalInvalidatesItsReference() {
        val favorite=BridgeContactFavorites.save(app,"Manual Home","+39000000009")
        val r=BridgeContacts.search(app,device,"Manual Home")
        assertEquals(1,r.getJSONArray("contacts").length())
        assertFalse(r.toString().contains("+39000000009"))
        val ref=r.getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        val resolved=BridgeContacts.resolve(app,device,ref)
        assertEquals("+39000000009",resolved.number);assertEquals(-1L,resolved.row)
        BridgeContactFavorites.remove(app,favorite.getString("id"),favorite.getInt("revision"))
        assertEquals("contact_changed",assertThrows(BridgeFailure::class.java) {BridgeContacts.resolve(app,device,ref)}.code)
        assertEquals(1,provider.rows.size);assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun manualAndBookHomonymsReturnChoicesButAnIdenticalNumberIsDeduplicated() {
        val f=BridgeContactFavorites.save(app,"Test Contact","+39000000009")
        val command=JSONObject().put("requestId",device).put("operation","call_by_name").put("params",JSONObject().put("name","Test Contact"))
        val r=BridgeActions.execute(app,device,command,SystemClock.elapsedRealtime()+25000){true}
        assertEquals("contacts_found",r.getString("code"));assertEquals(2,r.getJSONArray("contacts").length());assertNull(shadowOf(app).nextStartedActivity)
        BridgeContactFavorites.remove(app,f.getString("id"),f.getInt("revision"))
        BridgeContactFavorites.save(app,"Test Contact","+39000000001")
        assertEquals(1,BridgeContacts.search(app,device,"Test Contact").getJSONArray("contacts").length())
    }
    @Test fun selectingAnyBookNumberAddsFavoriteWithoutTouchingTheCacheOrAddressBook() {
        val value=BridgeContacts.addFavoriteFromUri(app,Uri.parse("content://com.android.contacts/data/1"))
        assertEquals("book",value.getString("kind"));assertEquals(1,BridgeContactFavorites.entries(app).size)
        assertTrue(BridgeContactCache.entries(app).isEmpty());assertEquals(1,provider.rows.size)
        assertThrows(IllegalArgumentException::class.java) {BridgeContacts.addFavoriteFromUri(app,Uri.parse("content://other.provider/1"))}
    }

    @Test fun explicitManualFavoriteCanResolveWithoutReadingThePhoneAddressBook() {
        BridgeContactFavorites.save(app,"Manual Home","+39000000009")
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        val r=BridgeContacts.search(app,device,"Manual Home")
        val ref=r.getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        assertEquals("+39000000009",BridgeContacts.resolve(app,device,ref).number)
        assertEquals(1,provider.rows.size);assertNull(shadowOf(app).nextStartedActivity)
    }

}
