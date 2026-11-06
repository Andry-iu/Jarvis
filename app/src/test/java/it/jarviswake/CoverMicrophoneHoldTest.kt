package it.jarviswake

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Actual HUD touch events and Handler deadlines, including removal during a held gesture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34,36])
@LooperMode(LooperMode.Mode.PAUSED)
class CoverMicrophoneHoldTest {
    private lateinit var activity:org.robolectric.android.controller.ActivityController<Activity>
    private lateinit var view:HolographicOverlayView
    private val actions=mutableListOf<String>()
    @Before fun setup() {
        activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        view=HolographicOverlayView(activity.get())
        view.setCoverActions(singleTap={actions.add("tap")},belowTap={actions.add("opacity")},
            close={actions.add("close")},export={actions.add("export")},hide={actions.add("home")},
            microphoneHoldStart={actions.add("unmute");view.setMicrophoneMuted(false)},
            microphoneHoldEnd={actions.add("mute");view.setMicrophoneMuted(true)})
        activity.get().setContentView(view,android.view.ViewGroup.LayoutParams(1000,2000))
        view.layout(0,0,1000,2000)
        view.setMicrophoneMuted(true)
        assertTrue(view.isShown)
    }
    @After fun cleanup() {activity.pause().stop().destroy()}
    private fun advance(ms:Long) {shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
    private fun touch(action:Int,x:Float=500f,y:Float=860f) {
        val now=SystemClock.uptimeMillis()
        val event=MotionEvent.obtain(now,now,action,x,y,0)
        try {view.onTouchEvent(event)} finally {event.recycle()}
    }
    private fun hold() {touch(MotionEvent.ACTION_DOWN);advance(ViewConfiguration.getLongPressTimeout().toLong()+1)}

    @Test fun mutedHoldActivatesOnlyUntilReleaseAndDoesNotAlsoTap() {
        hold();assertEquals(listOf("unmute"),actions)
        touch(MotionEvent.ACTION_UP);advance(400)
        assertEquals(listOf("unmute","mute"),actions)
    }
    @Test fun shortTapRetainsToggleAndDoubleTapRetainsClose() {
        touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_UP);advance(400)
        assertEquals(listOf("tap"),actions);actions.clear()
        touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_UP);advance(50)
        touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_UP);advance(600)
        assertEquals(listOf("close"),actions)
    }
    @Test fun holdingAnAlreadyActiveMicrophoneDoesNotToggleOnRelease() {
        view.setMicrophoneMuted(false);hold();touch(MotionEvent.ACTION_UP);advance(400)
        assertTrue(actions.isEmpty())
    }
    @Test fun cancelAfterActivationRestoresOnceAndSuppressesTap() {
        hold();touch(MotionEvent.ACTION_CANCEL);touch(MotionEvent.ACTION_UP);advance(400)
        assertEquals(listOf("unmute","mute"),actions)
    }
    @Test fun dragOutRestoresMuteAndDoesNotTurnIntoATap() {
        hold();touch(MotionEvent.ACTION_MOVE,700f,860f);touch(MotionEvent.ACTION_UP,500f,860f);advance(400)
        assertEquals(listOf("unmute","mute"),actions)
    }
    @Test fun additionalPointerBeforeThresholdCancelsActivation() {
        touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_POINTER_DOWN);advance(1000)
        touch(MotionEvent.ACTION_UP);advance(400);assertTrue(actions.isEmpty())
    }
    @Test fun hiddenOrDetachedCoverRestoresOnceWithoutReopening() {
        hold();view.visibility=View.GONE
        activity.get().setContentView(View(activity.get()));advance(500)
        assertEquals(listOf("unmute","mute"),actions)
        assertFalse(view.isAttachedToWindow)
    }
}
