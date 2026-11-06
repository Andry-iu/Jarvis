package it.jarviswake

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class ContactSearchPaginationTest {
    private lateinit var c:Context
    private var refs=JSONObject()
    private val read=BridgeContacts.readStore;private val write=BridgeContacts.writeStore
    private val favoritesRead=BridgeContactFavorites.read;private val favoritesWrite=BridgeContactFavorites.write
    private val cacheRead=BridgeContactCache.read;private val cacheWrite=BridgeContactCache.write
    @Before fun setup() {
        c=RuntimeEnvironment.getApplication();shadowOf(c as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        BridgeContacts.readStore={JSONObject(refs.toString())};BridgeContacts.writeStore={_,j->refs=JSONObject(j.toString())}
        BridgeContactFavorites.read={JSONObject()};BridgeContactFavorites.write={_,_->}
        BridgeContactCache.read={JSONObject()};BridgeContactCache.write={_,_->}
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,People())
    }
    @After fun reset() {
        BridgeContacts.readStore=read;BridgeContacts.writeStore=write
        BridgeContactFavorites.read=favoritesRead;BridgeContactFavorites.write=favoritesWrite
        BridgeContactCache.read=cacheRead;BridgeContactCache.write=cacheWrite
    }
    class People:ContentProvider() {
        override fun onCreate()=true
        override fun getType(uri:Uri)="test/people"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?) =
            MatrixCursor(projection!!).apply {
                val indices=if(selection!=null) listOf(args!!.single().toInt()) else (1..45).toList()
                for(i in indices) addRow(arrayOf<Any>(i.toLong(),"Same name","+3900000"+i.toString().padStart(4,'0'),2,""))
            }
        override fun insert(uri:Uri,values:ContentValues?):Uri?=error("unexpected write")
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=error("unexpected write")
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=error("unexpected write")
    }
    @Test fun all45MatchesRemainAvailableAcrossThreePagesAndEarlierReferencesStillResolve() {
        val first=BridgeContacts.search(c,"device","Same name")
        val firstRef=first.getJSONArray("contacts").getJSONObject(0).getString("contact_ref")
        assertEquals(20,first.getJSONArray("contacts").length());assertEquals(20,first.getInt("next_offset"))
        val second=BridgeContacts.search(c,"device","Same name",20)
        assertEquals(20,second.getJSONArray("contacts").length());assertEquals(40,second.getInt("next_offset"))
        val last=BridgeContacts.search(c,"device","Same name",40)
        assertEquals(5,last.getJSONArray("contacts").length());assertTrue(last.isNull("next_offset"));assertFalse(last.getBoolean("truncated"))
        assertEquals(45,refs.length());assertFalse(BridgeContacts.resolve(c,"device",firstRef).uniqueName)
        val repeated=BridgeContacts.search(c,"device","Same name",20)
        assertEquals(second.toString(),repeated.toString())
        val failure=assertThrows(BridgeFailure::class.java) {BridgeContacts.search(c,"other device","Same name",20)}
        assertEquals("contact_expired",failure.code)
    }
}
