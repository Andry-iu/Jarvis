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
class BridgeContactCacheTest {
    private val favoriteRead=BridgeContactFavorites.read;private val favoriteWrite=BridgeContactFavorites.write
    private var favoritesStore=JSONObject()
    private val oldRead=BridgeContactCache.read;private val oldWrite=BridgeContactCache.write
    private lateinit var c:Context
    private var stored=JSONObject()
    @Before fun setup() {c=RuntimeEnvironment.getApplication();BridgeContactFavorites.read={JSONObject(favoritesStore.toString())};BridgeContactFavorites.write={_,j->favoritesStore=JSONObject(j.toString())};BridgeContactCache.read={JSONObject(stored.toString())};BridgeContactCache.write={_,j->stored=JSONObject(j.toString())}}
    @After fun reset() {BridgeContactFavorites.read=favoriteRead;BridgeContactFavorites.write=favoriteWrite;BridgeContactCache.read=oldRead;BridgeContactCache.write=oldWrite}
    private fun contact(row:Long,name:String="Test $row",query:String=name)=BridgeContact(name,"+3900000${row.toString().padStart(4,'0')}",null,true,row,query,"mobile")
    @Test fun frequentAndFavoriteRecordsAreBoundedAndNeverDuplicateTheSameRow() {
        BridgeContactFavorites.save(c,"Saved favorite","+39000000001",1,"mobile")
        for(i in 2L..25L) BridgeContactCache.remember(c,contact(i))
        assertEquals(20,BridgeContactCache.entries(c).size)
        assertEquals(1,BridgeContactFavorites.entries(c).size)
        BridgeContactCache.remember(c,contact(1))
        assertEquals(20,BridgeContactCache.entries(c).size)
        assertEquals(1,BridgeContactFavorites.entries(c).size)
    }
    @Test fun ambiguousAliasesAreNotAutoSelectedAndRemovalDoesNotTouchOtherContacts() {
        BridgeContactCache.remember(c,contact(1,"Test One","Test"))
        assertEquals(1L,BridgeContactCache.candidate(c," TEST ")?.getLong("row"))
        BridgeContactCache.remember(c,contact(2,"Test Two","Test"))
        assertNull(BridgeContactCache.candidate(c,"Test"))
        BridgeContactCache.remove(c,"1")
        assertEquals(2L,BridgeContactCache.candidate(c,"Test")?.getLong("row"))
        BridgeContactFavorites.save(c,"Saved favorite","+39000000002",2,"mobile");assertEquals(1,BridgeContactFavorites.entries(c).size)
        BridgeContactCache.clear(c);assertTrue(BridgeContactCache.entries(c).isEmpty());assertEquals(1,BridgeContactFavorites.entries(c).size)
    }
    @Test fun cacheScreenStartsWithNoDecorCrashAndShowsEveryStoredContact() {
        BridgeContactCache.remember(c,contact(1));BridgeContactCache.remember(c,contact(2))
        val controller=org.robolectric.Robolectric.buildActivity(ContactCacheActivity::class.java).setup().visible()
        fun buttons(v:android.view.View):List<android.widget.Button> = (if(v is android.widget.Button) listOf(v) else emptyList())+
            if(v is android.view.ViewGroup) (0 until v.childCount).flatMap {buttons(v.getChildAt(it))} else emptyList()
        buttons(controller.get().window.decorView).single {it.text.toString()=="Cache frequenti"}.performClick()
        val labels=buttons(controller.get().window.decorView).map {it.text.toString()}
        assertTrue(labels.any {it.contains("Test 1")});assertTrue(labels.any {it.contains("Test 2")})
        assertEquals("Indietro",labels.filter {it!="⋮"}.last());controller.pause().stop().destroy()
    }

}
