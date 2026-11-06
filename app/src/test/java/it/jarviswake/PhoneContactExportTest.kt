package it.jarviswake

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class PhoneContactExportTest {
    private lateinit var c:Context
    private var clock=100L
    private val read=BridgeContacts.readStore;private val write=BridgeContacts.writeStore
    private var refs=JSONObject()
    private lateinit var provider:FakeContacts
    @Before fun setup() {
        BridgeContacts.readStore={JSONObject(refs.toString())};BridgeContacts.writeStore={_,j->refs=JSONObject(j.toString())}
        c=RuntimeEnvironment.getApplication();PhoneContactExport.now={clock};PhoneContactExport.clear()
        provider=FakeContacts();ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,provider)
        PhoneContactExport.enable(c,false)
    }
    @After fun reset() { BridgeContacts.readStore=read;BridgeContacts.writeStore=write;PhoneContactExport.enable(c,false);PhoneContactExport.now=android.os.SystemClock::elapsedRealtime }
    class FakeContacts:ContentProvider() {
        var writes=0;var reads=0;var changed=false;var headers=0;var unavailable=false;var emailQueries=0
        override fun onCreate()=true
        override fun getType(uri:Uri)="test/contacts"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor? {
            reads++;if(unavailable) return null
            val cols=projection!!
            return MatrixCursor(cols).apply {
                if(cols[0]=="_id" && cols.size==5) { addRow(arrayOf<Any?>("11","Same name","+39 000001",2,null)) }
                else if(cols.size==1) { addRow(arrayOf("1")) }
                else if(cols.contains("contact_last_updated_timestamp")) {
                    headers++
                    addRow(arrayOf("1","lookup1","Same name",if(changed && headers>1) "2" else "1"))
                    if(selection==null) { addRow(arrayOf("2","lookup2","Same name","1"));addRow(arrayOf("3","lookup3","Only name","1")) }
                } else if(uri.toString().contains("phones")) {
                    addRow(arrayOf<Any?>("1","11","+39 000 001",2,null))
                    if(selection==null) { addRow(arrayOf<Any?>("1","12","123;45",1,null));addRow(arrayOf<Any?>("2","21","+39 000 001",2,null)) }
                } else {
                    emailQueries++
                    addRow(arrayOf<Any?>("1","31","test@example.invalid",1,null));addRow(arrayOf<Any?>("1","32","other@example.invalid",2,null))
                }
            }
        }
        override fun insert(uri:Uri,values:ContentValues?):Uri? {writes++;error("write forbidden")}
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int {writes++;error("write forbidden")}
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int {writes++;error("write forbidden")}
    }
    private fun body()=PhoneContactExport.collect(c,null){true}
    private fun failure(code:String,action:()->Unit) {try {action();fail("expected $code")} catch(e:BridgeFailure){assertEquals(code,e.code)}}
    @Test fun allAccessibleRowsIncludingDuplicateNamesAndNumbersAndNamelessPhoneRecordsArePreservedReadOnly() {
        val b=body();val rows=b.getJSONArray("contacts")
        assertEquals(3,rows.length());assertEquals(2,rows.getJSONObject(0).getJSONArray("phones").length())
        assertEquals("123;45",rows.getJSONObject(0).getJSONArray("phones").getJSONObject(1).getString("number"))
        assertEquals(2,rows.getJSONObject(0).getJSONArray("emails").length())
        assertEquals(0,rows.getJSONObject(2).getJSONArray("phones").length());assertEquals(0,provider.writes)
    }
    @Test fun frozenChunksReassembleExactlyAndOtherDeviceExpiredOffsetAndCancellationAreRejected() {
        val b=body();b.getJSONArray("contacts").getJSONObject(0).put("name","à".repeat(8000))
        val manifest=PhoneContactExport.freeze("device",b){true};val id=manifest.getString("snapshot_id")
        val out=java.io.ByteArrayOutputStream();var offset=0
        do {val p=PhoneContactExport.page("device",id,offset){true};out.write(android.util.Base64.decode(p.getString("data_base64"),0))
            offset=if(p.isNull("next_offset")) -1 else p.getInt("next_offset") } while(offset>=0)
        assertEquals(b.toString(),out.toString("UTF-8"));assertEquals(manifest.getInt("bytes"),out.size())
        failure("contacts_export_expired"){PhoneContactExport.page("other",id,0){true}}
        failure("invalid_export_offset"){PhoneContactExport.page("device",id,1){true}}
        failure("cancelled"){PhoneContactExport.page("device",id,0){false}}
        clock+=PhoneContactExport.TTL
        failure("contacts_export_expired"){PhoneContactExport.page("device",id,0){true}}
    }
    @Test fun changedOrUnavailableProviderAndRevokedConsentNeverProducePartialExport() {
        provider.changed=true;failure("contacts_export_changed"){body()}
        provider.unavailable=true;failure("contacts_export_unavailable"){body()}
        provider.unavailable=false;provider.changed=false
        val id=PhoneContactExport.freeze("d",body()){true}.getString("snapshot_id")
        PhoneContactExport.enable(c,false)
        failure("contacts_export_expired"){PhoneContactExport.page("d",id,0){true}}
        val result=PhoneContactExport.execute(c,"d","contacts_export_start",JSONObject().put("contact_ref","")){true}
        assertEquals("contacts_export_disabled",result.getString("code"))
    }
    @Test fun cancellationDuringProviderIterationNeverFreezesOrWritesAnything() {
        var permitted=true
        failure("cancelled"){PhoneContactExport.collect(c,null){val value=permitted;permitted=false;value}}
        assertEquals(0,provider.writes)
    }
    @Test fun specificNumberExportDoesNotReadOtherPhonesOrAnyEmails() {
        val selected=BridgeContact("Same name","+39000001",null,true,11)
        val b=PhoneContactExport.collect(c,selected){true};val rows=b.getJSONArray("contacts")
        assertEquals("number",b.getString("mode"));assertEquals(1,rows.length())
        assertEquals(1,rows.getJSONObject(0).getJSONArray("phones").length())
        assertEquals(0,rows.getJSONObject(0).getJSONArray("emails").length());assertEquals(0,provider.emailQueries)
        assertEquals(0,provider.writes)
    }
    @Test fun permissionAndLockGuardsBlockTheRealDispatcherWithoutProviderReads() {
        val app=c as android.app.Application
        BridgeSettings.enable(c,true);PhoneContactExport.enable(c,true)
        val command=JSONObject().put("operation","contacts_export_start").put("params",JSONObject().put("contact_ref",""))
        org.robolectric.Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.READ_CONTACTS)
        assertEquals("permission_required",BridgeActions.execute(c,"d",command,Long.MAX_VALUE){true}.getString("code"))
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val keyguard=org.robolectric.Shadows.shadowOf(c.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        assertEquals("locked",BridgeActions.execute(c,"d",command,Long.MAX_VALUE){true}.getString("code"))
        keyguard.setKeyguardLocked(false)
        assertEquals("cancelled",BridgeActions.execute(c,"d",command,Long.MAX_VALUE){false}.getString("code"))
        assertEquals(0,provider.reads);assertEquals(0,provider.writes);BridgeSettings.enable(c,false)
    }
    @Test fun singleNumberSelectionAndExportWorkWithOnlyReadConsentAndNeverEnableCalling() {
        BridgeSettings.enable(c,true);BridgeSettings.enableActions(c,false);PhoneContactExport.enable(c,true)
        org.robolectric.Shadows.shadowOf(c as android.app.Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val find=JSONObject().put("operation","find_contacts").put("params",JSONObject().put("name","Same name"))
        val found=BridgeActions.execute(c,"d",find,Long.MAX_VALUE){true}
        assertEquals("contacts_found",found.getString("code"))
        val row=found.getJSONArray("contacts").getJSONObject(0);assertFalse(row.has("number"))
        val start=JSONObject().put("operation","contacts_export_start").put("params",JSONObject().put("contact_ref",row.getString("contact_ref")))
        val ready=BridgeActions.execute(c,"d",start,Long.MAX_VALUE){true}
        assertEquals("contacts_export_ready",ready.getString("code"));assertEquals("number",ready.getString("mode"))
        assertEquals(1,ready.getInt("phones"));assertEquals(0,ready.getInt("emails"))
        val call=JSONObject().put("operation","call_contact").put("params",JSONObject().put("contact_ref",row.getString("contact_ref")))
        assertEquals("actions_disabled",BridgeActions.execute(c,"d",call,Long.MAX_VALUE){true}.getString("code"))
        PhoneContactExport.enable(c,false)
        assertEquals("actions_disabled",BridgeActions.execute(c,"d",find,Long.MAX_VALUE){true}.getString("code"))
        assertEquals(0,provider.writes);assertFalse(BridgeSettings.actionsEnabled(c));BridgeSettings.enable(c,false)
    }
    @Test fun newSnapshotInvalidatesOldAndOversizeSnapshotIsExplicitlyRejected() {
        val old=PhoneContactExport.freeze("d",body()){true}.getString("snapshot_id")
        PhoneContactExport.freeze("d",body()){true}
        failure("contacts_export_expired"){PhoneContactExport.page("d",old,0){true}}
        failure("contacts_export_too_large"){PhoneContactExport.freeze("d",JSONObject().put("padding","x".repeat(PhoneContactExport.MAX_BYTES))){true}}
    }
    @Test fun activePageReadsRenewTheInactivityLeaseBeyondTheOriginalSnapshotLifetime() {
        val id=PhoneContactExport.freeze("d",body()){true}.getString("snapshot_id")
        clock+=PhoneContactExport.TTL-1
        assertEquals("contacts_export_page",PhoneContactExport.page("d",id,0){true}.getString("code"))
        clock+=1000
        assertEquals("contacts_export_page",PhoneContactExport.page("d",id,0){true}.getString("code"))
        clock+=PhoneContactExport.TTL
        failure("contacts_export_expired"){PhoneContactExport.page("d",id,0){true}}
    }
}

