package it.jarviswake

import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import it.jarviswake.watch.WatchProtocol
import org.json.JSONObject

/** Google Play services restricts Data Layer delivery to the same package and certificate. */
class WatchProbeService:WearableListenerService() {
    override fun onMessageReceived(e:MessageEvent) {
        if(e.path !in setOf(WatchProtocol.PREPARE,WatchProtocol.SNAPSHOT)) return
        val request=WatchProtocol.request(e.data) ?: return
        val id=request.getString("id")
        val response=runCatching {
            val nearby=Tasks.await(Wearable.getNodeClient(this).connectedNodes,2,TimeUnit.SECONDS).any {it.id==e.sourceNodeId && it.isNearby}
            when {
                !WatchTickets.enabled(this)->JSONObject().put("code","disabled")
                !nearby->JSONObject().put("code","nearby_required")
                e.path==WatchProtocol.PREPARE->JSONObject().put("code",WatchTickets.prepare(this,id))
                else->WatchAudioProbe.snapshot(this)
            }
        }.getOrElse {JSONObject().put("code","unavailable")}.put("id",id)
        Wearable.getMessageClient(this).sendMessage(e.sourceNodeId,WatchProtocol.REPLY,response.toString().toByteArray(Charsets.UTF_8))
    }
}
