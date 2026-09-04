package com.luciddream.wear.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
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

    /**
     * Fires when the phone companion becomes reachable (or unreachable) again.
     *
     * This replaces onPeerConnected, which was only delivered through the deprecated
     * BIND_LISTENER binding. The phone advertises [WearSyncPaths.CAPABILITY_PHONE_COMPANION]
     * in its res/values/wear.xml; an empty node set means it is currently away, so there is
     * nothing to drain to.
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        if (capabilityInfo.name != WearSyncPaths.CAPABILITY_PHONE_COMPANION) return
        if (capabilityInfo.nodes.isEmpty()) return

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
