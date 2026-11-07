package it.jarviswake

import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeNavigationTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val field=PhoneNotes::class.java.getDeclaredField("repository").apply {isAccessible=true}
    private var previous:Any?=null
    private var reads=0
    private var onRead:(Int)->Unit = {}
    private var rows=listOf(LocalNote("63f21d9a-acb8-4cdb-8f9f-7888d7c3bb1d","Indirizzo di casa","Casa è in via Esempio 4, Città.",1,1))
    @Before fun setup() {
        previous=field.get(null)
        field.set(null,LocalNotesRepository(object:NotesStorage {
            override fun load():List<LocalNote> {reads++;onRead(reads);return rows}
            override fun store(notes:List<LocalNote>) {error("navigation must never write notes")}
        },{1L},{error("must never create notes")}))
        BridgeSettings.enable(app,true);BridgeSettings.enableActions(app,true);PhoneNotes.enable(app,true)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(app).clearNextStartedActivities()
    }
    @After fun reset() {
        field.set(null,previous);PhoneNotes.enable(app,false)
        BridgeSettings.enableActions(app,false);BridgeSettings.enable(app,false)
    }
    private fun run(query:String):JSONObject {
        val command=JSONObject().put("requestId","63f21d9a-acb8-4cdb-8f9f-7888d7c3bb1d")
            .put("operation","navigate").put("params",JSONObject().put("destination",query))
        val value=AtomicReference<JSONObject>()
        val thread=Thread {value.set(BridgeActions.execute(app,"test-device",command,SystemClock.elapsedRealtime()+25000){true})}
        thread.start()
        val until=System.nanoTime()+4_000_000_000L
        while(thread.isAlive && System.nanoTime()<until) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(2)}
        thread.join(100);assertFalse(thread.isAlive);return value.get()
    }
    private fun register(query:String) {
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(query)+"&mode=d"))
            .setPackage("com.google.android.apps.maps")
        shadowOf(app.packageManager).addResolveInfoForIntent(intent,ResolveInfo().apply {activityInfo=ActivityInfo().apply {
            packageName="com.google.android.apps.maps";name="com.google.android.apps.maps.TestLauncher";exported=true
        }})
    }
    @Test fun homeIsResolvedAndLaunchedByOneCommandWithNoNoteContentsInResult() {
        register("via Esempio 4, Città")
        val result=run("casa")
        assertEquals("intent_dispatched",result.getString("code"));assertEquals(1,result.length())
        val intent=shadowOf(app).nextStartedActivity
        assertEquals("com.google.android.apps.maps",intent.`package`)
        assertEquals("google.navigation:q="+Uri.encode("via Esempio 4, Città")+"&mode=d",intent.data.toString())
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun literalPlaceSearchAndCompleteAddressNeedNoRemoteLookup() {
        PhoneNotes.enable(app,false)
        for(query in listOf("Farmacia Esempio Rimini","via Esempio 4, Città")) {
            register(query);assertEquals("intent_dispatched",run(query).getString("code"))
            assertNotNull(shadowOf(app).nextStartedActivity);assertNull(shadowOf(app).nextStartedActivity)
        }
        assertEquals(0,reads)
    }
    @Test fun ambiguityAndLockNeverStartMaps() {
        rows=rows+rows.single().copy(id="4ac0e8f0-19b7-413c-aed8-5e49089e5f18",title="Casa")
        assertEquals("destination_ambiguous",run("casa").getString("code"));assertNull(shadowOf(app).nextStartedActivity)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        assertEquals("locked",run("via Esempio 4, Città").getString("code"));assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun changedNoteOrRevokedConsentBeforeDispatchNeverLaunchesOldDestination() {
        register("via Esempio 4, Città")
        onRead={if(it==2) rows=rows.map {note->note.copy(revision=2,text="via Altra 9, Città")}}
        assertEquals("destination_changed",run("casa").getString("code"))
        assertNull(shadowOf(app).nextStartedActivity)
        reads=0
        onRead={PhoneNotes.enable(app,false)}
        assertEquals("notes_disabled",run("casa").getString("code"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
