package it.jarviswake

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/** Android Auto projection state only; Bluetooth pairing is not evidence of projection. */
internal object BridgeCarConnection {
    private var connection:CarConnection?=null
    @Volatile private var type:Int?=null
    private val observer=Observer<Int> { type=it }
    fun start(c:Context) {
        if(connection!=null) return
        runCatching { CarConnection(c.applicationContext).also { connection=it;it.type.observeForever(observer) } }
            .onFailure { type=null;connection=null }
    }
    fun keepMapsCover()=type==CarConnection.CONNECTION_TYPE_PROJECTION
}
