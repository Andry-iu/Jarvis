package it.jarviswake

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.View
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeCallPreparationTest {
    private val id="63f21d9a-acb8-4cdb-8f9f-7888d7c3bb1d"
    private val serviceField=VoiceAccessibilityService::class.java.getDeclaredField("instance").apply {isAccessible=true}
    private val previous=serviceField.get(null)
    @After fun reset() {BridgeCallPreparation.finish(id);serviceField.set(null,previous)}
    @Test fun gestureDismissesImmediatelyAndPermanentlyCancelsThisPendingCall() {
        val c=Robolectric.buildService(VoiceAccessibilityService::class.java).create();val s=c.get()
        serviceField.set(null,WeakReference(s))
        assertFalse(VoiceCycle.active)
        shadowOf(RuntimeEnvironment.getApplication()).clearNextStartedActivities()
        BridgeCallPreparation.begin(s,id);assertTrue(BridgeCallPreparation.allowed(id))
        val p=BridgeCallPreparation::class.java.getDeclaredField("pending").apply {isAccessible=true}.get(null)!!
        val view=p.javaClass.getDeclaredField("view").apply {isAccessible=true}.get(p) as HolographicOverlayView
        @Suppress("UNCHECKED_CAST")
        val close=HolographicOverlayView::class.java.getDeclaredField("onClose").apply {isAccessible=true}.get(view) as ()->Unit
        close();assertFalse(BridgeCallPreparation.allowed(id));assertEquals(View.GONE,view.visibility)
        close();assertFalse(BridgeCallPreparation.allowed(id))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        BridgeCallPreparation.finish(id);assertFalse(BridgeCallPreparation.allowed(id))
        c.destroy()
    }
    @Test fun foregroundIntentTargetsInstalledChatGptWithoutOpeningProjectOrClearingTask() {
        val app=RuntimeEnvironment.getApplication()
        val info=ResolveInfo().apply {activityInfo=ActivityInfo().apply {
            packageName=NativeChatSettings.PACKAGE;name="com.openai.chatgpt.TestLauncher";exported=true
        }}
        val query=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(NativeChatSettings.PACKAGE)
        shadowOf(app.packageManager).addResolveInfoForIntent(query,info)
        val intent=BridgeActions.chatGptResumeIntent(app)
        assertEquals(NativeChatSettings.PACKAGE,intent.component?.packageName)
        assertNotEquals(0,intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNotEquals(0,intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        assertEquals(0,intent.flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        assertNull(intent.data)
    }
    @Test fun mapsLaunchKeepsOrRemovesExistingCoverAccordingToTheSelectedPolicy() {
        val app=RuntimeEnvironment.getApplication()
        val c=Robolectric.buildService(VoiceAccessibilityService::class.java).create();val service=c.get()
        serviceField.set(null,WeakReference(service))
        val token="8cf00505-5a76-4206-9719-b107c71c1e65"
        service.getSharedPreferences("voice_accessibility",0).edit().putString("native_token",token).apply()
        val controller=NativeVoiceController(service,token)
        VoiceAccessibilityService::class.java.getDeclaredField("nativeController").apply {isAccessible=true}.set(service,controller)
        val hudField=NativeVoiceController::class.java.getDeclaredField("hud").apply {isAccessible=true}
        controller.launch(openShortcut=false,playWaitingAudio=false)
        assertNotNull(hudField.get(controller))
        val maps=Intent(Intent.ACTION_VIEW,android.net.Uri.parse("google.navigation:q=Test")).setPackage("com.google.android.apps.maps")
        shadowOf(app.packageManager).addResolveInfoForIntent(maps,ResolveInfo().apply {activityInfo=ActivityInfo().apply {
            packageName="com.google.android.apps.maps";name="com.google.android.apps.maps.TestLauncher";exported=true
        }})
        BridgeActions.launch(service,maps,keepCover=true)
        assertNotNull(hudField.get(controller))
        BridgeActions.launch(service,maps,keepCover=false)
        assertNull(hudField.get(controller))
        controller.destroy();c.destroy()
    }

}
