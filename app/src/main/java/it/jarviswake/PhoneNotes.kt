package it.jarviswake

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate key and archive: removing/replacing a pairing cannot remove personal notes. */
internal object PhoneNotes {
    private const val ALIAS="jarviswake.notes.v1"
    @Volatile private var repository:LocalNotesRepository?=null
    fun enabled(c:Context)=c.getSharedPreferences("local_notes_settings",Context.MODE_PRIVATE).getBoolean("remote",false)
    fun enable(c:Context,value:Boolean) { c.getSharedPreferences("local_notes_settings",Context.MODE_PRIVATE).edit().putBoolean("remote",value).apply() }
    @Synchronized fun book(c:Context):LocalNotesRepository {
        repository?.let { return it }
        val file=AtomicFile(File(c.applicationContext.noBackupFilesDir,"local-notes-v1.enc"))
        val storage=object:NotesStorage {
            override fun load():List<LocalNote> {
                val bytes=try { file.openRead().use { input ->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                    while(true) { val count=input.read(buffer);if(count<0) break
                        if(output.size()+count>2000000) throw NotesFailure("notes_unavailable")
                        output.write(buffer,0,count)
                    };output.toByteArray()
                } } catch(e:FileNotFoundException) { if(!file.baseFile.exists()) return emptyList() else throw e }
                val envelope=JSONObject(String(bytes,Charsets.UTF_8));require(envelope.getInt("v")==1)
                val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(envelope.getString("iv"),Base64.NO_WRAP)))
                }
                val plain=JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"),Base64.NO_WRAP)),Charsets.UTF_8))
                require(plain.getInt("v")==1)
                val items=plain.getJSONArray("notes")
                return (0 until items.length()).map { i -> val n=items.getJSONObject(i)
                    LocalNote(n.getString("id"),n.getString("title"),n.getString("text"),n.getInt("revision"),n.getLong("updated_at"))
                }
            }
            override fun store(notes:List<LocalNote>) {
                val plain=JSONObject().put("v",1).put("notes",JSONArray().apply { notes.forEach { put(full(it)) } })
                val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
                val encrypted=cipher.doFinal(plain.toString().toByteArray(Charsets.UTF_8))
                val envelope=JSONObject().put("v",1).put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
                    .put("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).toString().toByteArray(Charsets.UTF_8)
                val output=file.startWrite()
                try { output.write(envelope);file.finishWrite(output) }
                catch(e:Exception) { file.failWrite(output);throw e }
            }
        }
        return LocalNotesRepository(storage,System::currentTimeMillis) { UUID.randomUUID().toString() }.also { repository=it }
    }
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun metadata(n:LocalNote)=JSONObject().put("note_id",n.id).put("title",n.title).put("revision",n.revision).put("updated_at",n.updatedAt)
    private fun full(n:LocalNote)=JSONObject().put("id",n.id).put("title",n.title).put("text",n.text)
        .put("revision",n.revision).put("updated_at",n.updatedAt)
    fun execute(c:Context,op:String,p:JSONObject,allowed:()->Boolean):JSONObject = try {
        if(!enabled(c)) throw NotesFailure("notes_disabled")
        val repository=book(c)
        when(op) {
            "list_notes" -> {
                val (items,next)=repository.list(p.getInt("offset"))
                if(!allowed()) throw NotesFailure("expired_before_dispatch")
                result("notes_listed").put("notes",JSONArray().apply { items.forEach { put(metadata(it)) } })
                    .put("next_offset",next ?: JSONObject.NULL)
            }
            "read_note" -> {
                val n=repository.read(p.getString("note_id"))
                if(!allowed()) throw NotesFailure("expired_before_dispatch")
                result("note_read").put("note",metadata(n).put("text",n.text))
            }
            "save_note" -> {
                val n=repository.save(p.getString("note_id"),p.getString("title"),p.getString("text"),p.getInt("expected_revision"),allowed)
                result("note_saved").put("note",metadata(n))
            }
            else -> result("unsupported")
        }
    } catch(e:NotesFailure) { result(e.code) }
    catch(_:Exception) { result("notes_unavailable") }
}
