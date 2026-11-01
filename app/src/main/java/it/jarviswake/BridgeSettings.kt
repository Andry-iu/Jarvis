package it.jarviswake

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class BridgeCredentials(val origin: String,val deviceId: String,val deviceToken: String,val siteAccess: String) {
    fun json()=JSONObject().put("v",1).put("origin",origin).put("deviceId",deviceId)
        .put("deviceToken",deviceToken).put("siteAccess",siteAccess)
}

/** Secrets are encrypted with Android Keystore, excluded from backups and never logged. */
internal object BridgeSettings {
    private const val ALIAS="jarviswake.bridge.v1"
    private fun prefs(c:Context)=c.getSharedPreferences("bridge_link",Context.MODE_PRIVATE)
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    fun enable(c:Context,value:Boolean) { prefs(c).edit().putBoolean("enabled",value).apply() }
    fun configured(c:Context)=prefs(c).contains("encrypted")
    fun actionsEnabled(c:Context)=prefs(c).getBoolean("actions",false)
    fun enableActions(c:Context,value:Boolean) { prefs(c).edit().putBoolean("actions",value).apply() }
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun parse(j:JSONObject):BridgeCredentials {
        require(j.getInt("v")==1)
        val c=BridgeCredentials(j.getString("origin"),j.getString("deviceId"),j.getString("deviceToken"),j.getString("siteAccess"))
        require(BridgeProtocol.validOrigin(c.origin) && BridgeProtocol.validId(c.deviceId))
        require(c.deviceToken.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(c.siteAccess.length in 16..4096 && c.siteAccess.all { it.code in 33..126 })
        return c
    }
    fun saveCode(context:Context,code:String) {
        val text=code.trim()
        require(text.startsWith("JW1.") && text.length<=12000)
        val c=parse(JSONObject(String(Base64.decode(text.substring(4),Base64.DEFAULT),Charsets.UTF_8)))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=cipher.doFinal(c.json().toString().toByteArray(Charsets.UTF_8))
        check(prefs(context).edit().putString("encrypted",Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putBoolean("enabled",false).commit())
    }
    fun read(context:Context):BridgeCredentials? {
        val p=prefs(context)
        val bytes=p.getString("encrypted",null) ?: return null
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(p.getString("iv",null),Base64.NO_WRAP)))
        }
        return parse(JSONObject(String(cipher.doFinal(Base64.decode(bytes,Base64.NO_WRAP)),Charsets.UTF_8)))
    }
    fun clear(context:Context) {
        prefs(context).edit().clear().apply()
        context.getSharedPreferences("bridge_private",Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Small private command/contact records; never included in exported diagnostics. */
    @Synchronized fun readPrivate(context:Context,name:String):JSONObject {
        val p=context.getSharedPreferences("bridge_private",Context.MODE_PRIVATE)
        val value=p.getString(name,null) ?: return JSONObject()
        val envelope=JSONObject(value)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(envelope.getString("iv"),Base64.NO_WRAP)))
        }
        return JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"),Base64.NO_WRAP)),Charsets.UTF_8))
    }
    @Synchronized fun writePrivate(context:Context,name:String,value:JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes=cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val envelope=JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .put("data",Base64.encodeToString(bytes,Base64.NO_WRAP))
        check(context.getSharedPreferences("bridge_private",Context.MODE_PRIVATE).edit().putString(name,envelope.toString()).commit())
    }
}
