package com.luciddream.wear.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.luciddream.data.db.LucidDatabase
import com.luciddream.data.sync.AndroidWatchWearableTransportGateway
import com.luciddream.data.sync.RoomOfflineEventQueue
import com.luciddream.data.sync.WearSyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for commands from the companion Phone app over Wearable Data Layer.
 */
class WatchWearableListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncPaths.PATH_START_SESSION -> {
                val payloadJson = String(messageEvent.data, Charsets.UTF_8)
                val intent = Intent(this, WatchTrackingForegroundService::class.java).apply {
                    action = WatchTrackingForegroundService.ACTION_START
                    putExtra(WatchTrackingForegroundService.EXTRA_PAYLOAD, payloadJson)
                }
                ContextCompat.startForegroundService(this, intent)
            }
            WearSyncPaths.PATH_STOP_SESSION -> {
                val intent = Intent(this, WatchTrackingForegroundService::class.java).apply {
                    action = WatchTrackingForegroundService.ACTION_STOP
                }
                startService(intent)
            }
        }
    }

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        serviceScope.launch {
            val db = LucidDatabase.getInstance(applicationContext)
            val gateway = AndroidWatchWearableTransportGateway(
                context = applicationContext,
                offlineQueue = RoomOfflineEventQueue(db.queuedSyncEventDao())
            )
            gateway.drainOfflineQueue()
        }
    }
}
