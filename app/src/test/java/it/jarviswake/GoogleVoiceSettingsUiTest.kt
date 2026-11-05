package it.jarviswake

import android.view.View
import android.view.WindowManager
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class GoogleVoiceSettingsUiTest {
    @Test fun pageStartsWithoutGoogleRequestsAndDoesNotSaveOrExposeTypedCredentials() {
        val controller=Robolectric.buildActivity(GoogleVoiceSettingsActivity::class.java).setup()
        val activity=controller.get()
        fun field(name:String)=GoogleVoiceSettingsActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity) as EditText
        val stt=field("stt");val tts=field("tts")
        assertFalse(stt.isSaveEnabled);assertFalse(tts.isSaveEnabled)
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,stt.importantForAutofill)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        val secret="UNSAVED_PRIVATE_CREDENTIAL"
        stt.setText(secret);tts.setText(secret)
        assertFalse(RuntimeEnvironment.getApplication().getSharedPreferences("google_voice_private",0).all.values.any{it.toString().contains(secret)})
        controller.pause();assertEquals("",stt.text.toString());assertEquals("",tts.text.toString())
        controller.stop().destroy()
    }
    @Test fun privilegedJsonIsRejectedAndConfigurationStringNeverIncludesTokens() {
        val c=GoogleVoiceConfig("{\"private_key\":\"PRIVATE_VALUE\"}","{\"private_key\":\"PRIVATE_VALUE\"}",
            System.currentTimeMillis()+60000,"test-project","https://chatgpt.com/g/g-p-test/c/12345678-1234-4234-8234-123456789abc")
        assertThrows(IllegalArgumentException::class.java){c.validate()}
        assertFalse(c.toString().contains("PRIVATE_VALUE"))
    }
    @Test fun configurationDoesNotRequireOrExposeManualConversationLink() {
        val config=GoogleVoiceConfig("","",0,"test-project","",authMode="backend")
        config.validate();assertEquals("",config.conversation)
        val controller=Robolectric.buildActivity(GoogleVoiceSettingsActivity::class.java).setup()
        val labels=mutableListOf<String>()
        fun visit(v:View) {if(v is android.widget.TextView)labels.add(v.text.toString());if(v is android.view.ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i))}
        visit(controller.get().window.decorView)
        assertFalse(labels.any{it=="Link della conversazione esistente nel progetto ChatGPT" || it=="Usa conversazione WebView o link già preparato"})
        assertTrue(labels.any{it.contains("Nuova chat automatica a ogni wake")})
        controller.pause().stop().destroy()
    }

    @Test fun economicalMaleIsDefaultAndMigrationPreservesLaterExplicitChoices() {
        assertEquals("it-IT-Wavenet-F",GoogleVoiceConfig("","",0,"test-project","",authMode="backend").voice)
        val old=org.json.JSONObject().put("voice","it-IT-Chirp3-HD-Enceladus")
        assertEquals("it-IT-Wavenet-F",GoogleVoiceSettings.selectedVoice(old))
        old.put("voiceChoiceVersion",1)
        assertEquals("it-IT-Chirp3-HD-Enceladus",GoogleVoiceSettings.selectedVoice(old))
        assertEquals("it-IT-Neural2-F",GoogleVoiceSettings.selectedVoice(org.json.JSONObject().put("voice","it-IT-Neural2-F")))
    }

}
