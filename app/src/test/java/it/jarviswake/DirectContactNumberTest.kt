package it.jarviswake

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Base64
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
import java.security.MessageDigest

/** Synthetic provider, real search/revalidation/collector. Never writes Android contacts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class DirectContactNumberTest {
    private lateinit var c:Context
    private lateinit var people:People
    private var refs=JSONObject()
    private val oldRead=BridgeContacts.readStore;private val oldWrite=BridgeContacts.writeStore
    private val oldFavoritesRead=BridgeContactFavorites.read;private val oldFavoritesWrite=BridgeContactFavorites.write
    private val oldCacheRead=BridgeContactCache.read;private val oldCacheWrite=BridgeContactCache.write
    @Before fun setup() {
        c=RuntimeEnvironment.getApplication();shadowOf(c as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        BridgeSettings.enable(c,true);BridgeSettings.enableActions(c,false);PhoneContactExport.enable(c,true)
        BridgeContacts.readStore={JSONObject(refs.toString())};BridgeContacts.writeStore={_,j->refs=JSONObject(j.toString())}
        BridgeContactFavorites.read={JSONObject()};BridgeContactFavorites.write={_,_->}
        BridgeContactCache.read={JSONObject()};BridgeContactCache.write={_,_->}
        people=People();ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,people)
    }
    @After fun reset() {
        BridgeContacts.readStore=oldRead;BridgeContacts.writeStore=oldWrite
        BridgeContactFavorites.read=oldFavoritesRead;BridgeContactFavorites.write=oldFavoritesWrite
        BridgeContactCache.read=oldCacheRead;BridgeContactCache.write=oldCacheWrite
        PhoneContactExport.enable(c,false);BridgeSettings.enable(c,false)
    }
    class People:ContentProvider() {
        data class Row(val id:Long,val name:String,val number:String)
        val rows=mutableListOf(Row(1,"Example Person","+390000001"))
        var emailReads=0;var writes=0;var mutateAtVerification=false;var resolveReads=0
        override fun onCreate()=true
        override fun getType(uri:Uri)="test/contacts"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):MatrixCursor {
            val phone=uri.toString().contains("phones")
            if(uri.toString().contains("emails")) {emailReads++;error("email query forbidden")}
            if(phone && selection==Phone._ID+"=?" && projection!!.contains(Phone.DISPLAY_NAME)) {
                resolveReads++
                if(mutateAtVerification && resolveReads>1) rows[0]=rows[0].copy(number="+390000099")
            }
            val selected=if(selection!=null) rows.filter {it.id.toString()==args!!.single()} else rows
            return MatrixCursor(projection!!).apply {
                for(row in selected) addRow(projection.map {column -> when(column) {
                    Phone._ID -> row.id
                    Phone.DISPLAY_NAME -> row.name
                    Phone.NUMBER -> row.number
                    Phone.TYPE -> Phone.TYPE_MOBILE
                    Phone.LABEL -> ""
                    Phone.CONTACT_ID -> row.id
                    ContactsContract.Contacts.LOOKUP_KEY -> "lookup-${row.id}"
                    ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP -> "1"
                    else -> error("unexpected projection")
                }}.toTypedArray())
            }
        }
        override fun insert(uri:Uri,values:ContentValues?):Uri? {writes++;error("write forbidden")}
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int {writes++;error("write forbidden")}
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int {writes++;error("write forbidden")}
    }
    private fun params(name:String="Example Person",ref:String="",offset:Int=0)=JSONObject().put("name",name).put("contact_ref",ref).put("offset",offset)
    private fun get(p:JSONObject=params(),allowed:()->Boolean={true})=PhoneContactExport.execute(c,"device","get_contact",p,allowed=allowed)
    @Test fun uniqueNameReturnsVerifiedNumberAndHashWithoutExportSnapshotOrEmailRead() {
        val out=get();assertEquals("contact_number",out.getString("code"))
        val raw=Base64.decode(out.getString("data_base64"),Base64.NO_WRAP)
        assertEquals(raw.size,out.getInt("bytes"))
        assertEquals(MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)},out.getString("sha256"))
        val body=JSONObject(String(raw,Charsets.UTF_8));assertEquals("number",body.getString("mode"))
        val rows=body.getJSONArray("contacts");assertEquals(1,rows.length())
        assertEquals("+390000001",rows.getJSONObject(0).getJSONArray("phones").getJSONObject(0).getString("number"))
        assertEquals(0,rows.getJSONObject(0).getJSONArray("emails").length());assertEquals(0,people.emailReads);assertEquals(0,people.writes)
        assertEquals(2,people.resolveReads)
    }
    @Test fun duplicateNamesReturnOnlyMaskedMetadataUntilExplicitReferenceSelection() {
        people.rows.add(People.Row(2,"Example Person","+390000002"))
        val found=get();assertEquals("contacts_found",found.getString("code"));assertFalse(found.has("data_base64"))
        assertEquals(2,found.getJSONArray("contacts").length());assertEquals(0,people.resolveReads)
        val ref=found.getJSONArray("contacts").getJSONObject(1).getString("contact_ref")
        val out=get(params("",ref));assertEquals("contact_number",out.getString("code"))
        val body=JSONObject(String(Base64.decode(out.getString("data_base64"),Base64.NO_WRAP),Charsets.UTF_8))
        assertEquals("+390000002",body.getJSONArray("contacts").getJSONObject(0).getJSONArray("phones").getJSONObject(0).getString("number"))
    }
    @Test fun paginationNeverTreatsTheLastSingleCandidateAsAnUnambiguousWholeSearch() {
        for(i in 2..21) people.rows.add(People.Row(i.toLong(),"Example Person","+3900000"+i.toString().padStart(3,'0')))
        val first=get();assertEquals(20,first.getJSONArray("contacts").length());assertTrue(first.getBoolean("truncated"))
        val last=get(params(offset=20));assertEquals("contacts_found",last.getString("code"))
        assertEquals(1,last.getJSONArray("contacts").length());assertFalse(last.has("data_base64"));assertEquals(0,people.resolveReads)
    }
    @Test fun noMatchReturnsAnEmptySearchWithoutInventingOrSavingANumber() {
        people.rows.clear();val out=get()
        assertEquals("contacts_found",out.getString("code"));assertEquals(0,out.getJSONArray("contacts").length())
        assertFalse(out.has("data_base64"));assertEquals(0,people.writes)
    }
    @Test fun changedRowCannotLeakTheOldOrNewNumber() {
        people.mutateAtVerification=true
        val out=get();assertEquals("contact_changed",out.getString("code"));assertEquals(1,out.length())
    }
    @Test fun directReadRequiresCopyConsentPermissionAndLiveGuard() {
        PhoneContactExport.enable(c,false);assertEquals("contacts_export_disabled",get().getString("code"))
        PhoneContactExport.enable(c,true);shadowOf(c as Application).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals("permission_required",get().getString("code"))
        shadowOf(c as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals("cancelled",get(allowed={false}).getString("code"))
        assertEquals("invalid_export_mode",get(params("Example",java.util.UUID.randomUUID().toString())).getString("code"))
    }
    @Test fun directLookupUsesReadOnlyConsentWithoutEnablingPhoneActions() {
        val command=JSONObject().put("operation","get_contact").put("requestId",java.util.UUID.randomUUID().toString()).put("params",params())
        val out=BridgeActions.execute(c,"device",command,SystemClock.elapsedRealtime()+25000){true}
        assertEquals("contact_number",out.getString("code"));assertFalse(BridgeSettings.actionsEnabled(c));assertEquals(0,people.writes)
    }
    @Test fun existingSnapshotSurvivesIndependentDirectLookup() {
        val initial=get();val body=JSONObject(String(Base64.decode(initial.getString("data_base64"),Base64.NO_WRAP),Charsets.UTF_8))
        val manifest=PhoneContactExport.freeze("device",body){true}
        assertEquals("contact_number",get().getString("code"))
        assertEquals("contacts_export_page",PhoneContactExport.page("device",manifest.getString("snapshot_id"),0){true}.getString("code"))
    }
}
