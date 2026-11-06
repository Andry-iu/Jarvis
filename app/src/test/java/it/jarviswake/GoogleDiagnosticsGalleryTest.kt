package it.jarviswake

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class GoogleDiagnosticsGalleryTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private class Gallery(val root:File):ContentProvider() {
        var inserted:ContentValues?=null;var published=false;var removed=false;var fail=false
        val photo=File(root,"photo.png")
        override fun onCreate()=true
        override fun getType(uri:Uri)="image/png"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,order:String?):Cursor?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri {inserted=ContentValues(values);return Uri.parse("content://media/external/images/media/1")}
        override fun update(uri:Uri,values:ContentValues?,where:String?,args:Array<out String>?):Int {published=values?.getAsInteger(MediaStore.Images.Media.IS_PENDING)==0;return 1}
        override fun delete(uri:Uri,where:String?,args:Array<out String>?):Int {removed=true;photo.delete();return 1}
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(fail) throw java.io.FileNotFoundException()
            return ParcelFileDescriptor.open(photo,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
    }
    @Test fun galleryWritePublishesOnlyCompletePngAndDeletesFailedOrCancelledPendingImage() {
        val g=Gallery(app.cacheDir);g.attachInfo(app,android.content.pm.ProviderInfo().apply{authority="media";exported=true});ShadowContentResolver.registerProviderInternal("media",g)
        val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        assertEquals("saved",DiagnosticScreenshots.save(app.contentResolver,image){true})
        assertEquals("Pictures/JarvisWake",g.inserted!!.getAsString(MediaStore.Images.Media.RELATIVE_PATH));assertEquals("image/png",g.inserted!!.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals(1,g.inserted!!.getAsInteger(MediaStore.Images.Media.IS_PENDING));assertTrue(g.published);assertTrue(g.photo.length()>0)
        assertEquals("cancelled",DiagnosticScreenshots.save(app.contentResolver,image){false})
        g.fail=true;assertEquals("gallery_failed",DiagnosticScreenshots.save(app.contentResolver,image){true});assertTrue(g.removed)
        image.recycle()
    }
    @Test fun technicalDiagnosticsDropContentsCredentialsUrlsAndUnknownFields() {
        val private="private-secret-token-should-never-be-exported"
        val meta=JSONObject().put("draftChars",37).put("hasDraft",true).put("draftOwned",false).put("token",private).put("text",private)
        val raw=JSONObject.quote(JSONObject().put("phase","draft").put("conversation","https://chatgpt.com/c/$private").put("diagnostics",meta).toString())
        GoogleVoiceDiagnostics.dom(app,"unit-cycle","page",raw)
        GoogleVoiceDiagnostics.record(app,"unit-cycle","status",private,JSONObject().put("http",403).put("url",private))
        GoogleVoiceDiagnostics.snapshot(app,"unit-cycle",JSONObject().put("project_opened",true).put("transcript",private))
        val done=CountDownLatch(1);DiagnosticLog.afterPendingWrites{done.countDown()};assertTrue(done.await(3,TimeUnit.SECONDS))
        val files=File(app.filesDir,"diagnostics").listFiles()!!.filter{it.name.startsWith("google-")}
        val text=files.joinToString("\n"){it.readText()};assertFalse(text.contains(private));assertFalse(text.contains("https://"))
        assertTrue(text.contains("draftChars"));assertTrue(text.contains("draft"));assertTrue(text.contains("403"));assertTrue(text.contains("project_opened"))
    }
    @Test fun zipExportSelectsGoogleTechnicalFilesAndAlwaysExcludesImagesAudioAndOtherJson() {
        val root=File(app.filesDir,"diagnostics").apply{mkdirs()}
        for(name in listOf("google-state.json","google-voice-0.log","google-voice-1.log","screen.png","photo.jpg","audio.pcm","credentials.json"))File(root,name).writeText("private test fixture")
        val names=DiagnosticExportActivity.filesForExport(app).map{it.name}
        assertTrue(names.containsAll(listOf("google-state.json","google-voice-0.log","google-voice-1.log")))
        assertFalse(names.any{it in setOf("screen.png","photo.jpg","audio.pcm","credentials.json")})
    }

}
