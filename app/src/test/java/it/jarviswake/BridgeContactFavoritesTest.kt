package it.jarviswake

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
class BridgeContactFavoritesTest {
    private val oldRead=BridgeContactFavorites.read;private val oldWrite=BridgeContactFavorites.write
    private val cacheRead=BridgeContactCache.read;private val cacheWrite=BridgeContactCache.write
    private var saved=JSONObject();private var cached=JSONObject()
    private lateinit var c:Context
    @Before fun setup() {
        c=RuntimeEnvironment.getApplication()
        BridgeContactFavorites.read={JSONObject(saved.toString())};BridgeContactFavorites.write={_,j->saved=JSONObject(j.toString())}
        BridgeContactCache.read={JSONObject(cached.toString())};BridgeContactCache.write={_,j->cached=JSONObject(j.toString())}
    }
    @After fun reset() {BridgeContactFavorites.read=oldRead;BridgeContactFavorites.write=oldWrite;BridgeContactCache.read=cacheRead;BridgeContactCache.write=cacheWrite}
    @Test fun legacyFavoritesAreCopiedBeforeCacheIsClearedAndImportedOnlyEntriesLeaveTheCache() {
        cached.put("1",JSONObject().put("row",1).put("name","Old Favorite").put("number","+39000000001").put("favorite",true).put("count",0))
        cached.put("2",JSONObject().put("row",2).put("name","Frequent").put("number","+39000000002").put("count",3))
        val favorites=BridgeContactFavorites.entries(c)
        assertEquals(1,favorites.size);assertEquals("Old Favorite",favorites.single().getString("name"))
        assertEquals(1,BridgeContactCache.entries(c).size)
        BridgeContactCache.clear(c);assertEquals(1,BridgeContactFavorites.entries(c).size)
        BridgeContactFavorites.migrate(c);assertEquals(1,BridgeContactFavorites.entries(c).size)
    }
    @Test fun failedMigrationDoesNotDeleteLegacyFavoriteAndRetryDoesNotDuplicate() {
        cached.put("1",JSONObject().put("row",1).put("name","Old Favorite").put("number","+39000000001").put("favorite",true).put("count",0))
        BridgeContactFavorites.write={_,_->throw IllegalStateException("test storage error")}
        assertThrows(IllegalStateException::class.java) {BridgeContactCache.clear(c)}
        assertTrue(cached.has("1"))
        BridgeContactFavorites.write={_,j->saved=JSONObject(j.toString())}
        BridgeContactFavorites.migrate(c);assertEquals(1,BridgeContactFavorites.entries(c).size)
    }
    @Test fun manualEntriesArePersistentIndependentAndIdenticalRetriesDoNotDuplicate() {
        val first=BridgeContactFavorites.save(c,"Home","+39 000 000 001")
        BridgeContactFavorites.save(c,"Home","+39000000001")
        assertEquals(1,BridgeContactFavorites.entries(c).size)
        val latest=BridgeContactFavorites.entries(c).single()
        assertEquals(first.getString("id"),latest.getString("id"));assertEquals("manual",latest.getString("kind"))
        for(i in 1L..25L) BridgeContactCache.remember(c,BridgeContact("Test $i","+3900000${i.toString().padStart(4,'0')}",null,true,i))
        assertEquals(20,BridgeContactCache.entries(c).size);assertEquals(1,BridgeContactFavorites.entries(c).size)
        BridgeContactCache.clear(c);assertEquals(1,BridgeContactFavorites.entries(c).size)
        assertThrows(IllegalStateException::class.java) {BridgeContactFavorites.remove(c,first.getString("id"),first.getInt("revision"))}
        BridgeContactFavorites.remove(c,latest.getString("id"),latest.getInt("revision"));assertTrue(BridgeContactFavorites.entries(c).isEmpty())
    }
    @Test fun invalidDialStringsAreRejectedAndPairingClearLeavesTheFavoriteNamespace() {
        for(n in listOf("", "112", "*123#", "12345;123", "12345,123", "12+3456", "123456n789", "abc123456")) {
            assertThrows(IllegalArgumentException::class.java) {BridgeContactFavorites.save(c,"Test",n)}
        }
        assertTrue(BridgeContactFavorites.entries(c).isEmpty())
        c.getSharedPreferences("contact_favorites",0).edit().putString("sentinel","test").commit()
        BridgeSettings.clear(c)
        assertEquals("test",c.getSharedPreferences("contact_favorites",0).getString("sentinel",null))
    }
    @Test fun cacheUpdatesOfFavoritesDoNotDuplicateBookRowsAndRemovingFavoriteKeepsHistory() {
        val f=BridgeContactFavorites.save(c,"Book Test","+39000000001",1)
        val first=BridgeContact("Book Test","+39000000001",null,true,1)
        BridgeContactCache.remember(c,first)
        BridgeContactCache.remember(c,first.copy(favoriteId=f.getString("id"),favoriteRevision=f.getInt("revision")))
        assertEquals(1,BridgeContactCache.entries(c).size);assertEquals(2L,BridgeContactCache.entries(c).single().getLong("count"))
        BridgeContactFavorites.remove(c,f.getString("id"),f.getInt("revision"))
        assertEquals(1,BridgeContactCache.entries(c).size)
    }

}
