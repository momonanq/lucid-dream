package com.luciddream.data.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.luciddream.data.db.QueuedSyncEventDao
import com.luciddream.data.db.QueuedSyncEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface TransportResult {
    data class Success(val nodeId: String) : TransportResult
    data class Failure(val error: Throwable) : TransportResult
    data object QueuedOffline : TransportResult
}

interface OfflineEventQueue {
    suspend fun enqueue(path: String, payloadJson: String)
    suspend fun drainQueue(sendAction: suspend (path: String, payloadJson: String) -> Boolean): Int
    suspend fun pendingCount(): Int
}

class RoomOfflineEventQueue(
    private val dao: QueuedSyncEventDao
) : OfflineEventQueue {
    override suspend fun enqueue(path: String, payloadJson: String) = withContext(Dispatchers.IO) {
        dao.enqueue(
            QueuedSyncEventEntity(
                path = path,
                payloadJson = payloadJson,
                timestampMs = System.currentTimeMillis()
            )
        )
        Unit
    }

    override suspend fun drainQueue(
        sendAction: suspend (path: String, payloadJson: String) -> Boolean
    ): Int = withContext(Dispatchers.IO) {
        val pending = dao.getPendingEvents()
        var drained = 0
        for (event in pending) {
            val sent = runCatching { sendAction(event.path, event.payloadJson) }.getOrDefault(false)
            if (sent) {
                dao.deleteEvent(event.id)
                drained++
            } else {
                // If transmission fails, stop draining to preserve sequence
                break
            }
        }
        drained
    }

    override suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        dao.getPendingEvents().size
    }
}

interface PhoneWearableTransportGateway {
    suspend fun sendStartSession(payload: StartSessionPayload): TransportResult
    suspend fun sendStopSession(payload: StopSessionPayload): TransportResult
}

class AndroidPhoneWearableTransportGateway(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PhoneWearableTransportGateway {

    override suspend fun sendStartSession(payload: StartSessionPayload): TransportResult = withContext(Dispatchers.IO) {
        sendPayload(WearSyncPaths.PATH_START_SESSION, json.encodeToString(payload))
    }

    override suspend fun sendStopSession(payload: StopSessionPayload): TransportResult = withContext(Dispatchers.IO) {
        sendPayload(WearSyncPaths.PATH_STOP_SESSION, json.encodeToString(payload))
    }

    private suspend fun sendPayload(path: String, jsonString: String): TransportResult {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                return TransportResult.Failure(IllegalStateException("No connected Wear OS nodes found"))
            }
            val messageClient = Wearable.getMessageClient(context)
            val bytes = jsonString.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, bytes).await()
            }
            TransportResult.Success(nodes.first().id)
        } catch (e: Exception) {
            TransportResult.Failure(e)
        }
    }
}

interface WatchWearableTransportGateway {
    suspend fun sendCueTriggered(payload: CueTriggeredPayload): TransportResult
    suspend fun sendWakeSpike(payload: WakeSpikePayload): TransportResult
    suspend fun sendQuickFeedback(payload: QuickMorningFeedbackPayload): TransportResult
    suspend fun drainOfflineQueue(): Int
}

class AndroidWatchWearableTransportGateway(
    private val context: Context,
    private val offlineQueue: OfflineEventQueue,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : WatchWearableTransportGateway {

    override suspend fun sendCueTriggered(payload: CueTriggeredPayload): TransportResult =
        sendOrEnqueue(WearSyncPaths.PATH_CUE_TRIGGERED, json.encodeToString(payload))

    override suspend fun sendWakeSpike(payload: WakeSpikePayload): TransportResult =
        sendOrEnqueue(WearSyncPaths.PATH_WAKE_SPIKE, json.encodeToString(payload))

    override suspend fun sendQuickFeedback(payload: QuickMorningFeedbackPayload): TransportResult =
        sendOrEnqueue(WearSyncPaths.PATH_MORNING_FEEDBACK, json.encodeToString(payload))

    override suspend fun drainOfflineQueue(): Int = withContext(Dispatchers.IO) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrNull() ?: emptyList()
        if (nodes.isEmpty()) return@withContext 0

        val primaryNodeId = nodes.first().id
        offlineQueue.drainQueue { path, jsonString ->
            try {
                messageClient.sendMessage(primaryNodeId, path, jsonString.toByteArray(Charsets.UTF_8)).await()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun sendOrEnqueue(path: String, jsonString: String): TransportResult = withContext(Dispatchers.IO) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                offlineQueue.enqueue(path, jsonString)
                return@withContext TransportResult.QueuedOffline
            }

            val messageClient = Wearable.getMessageClient(context)
            val primaryNodeId = nodes.first().id
            messageClient.sendMessage(primaryNodeId, path, jsonString.toByteArray(Charsets.UTF_8)).await()
            TransportResult.Success(primaryNodeId)
        } catch (e: Exception) {
            offlineQueue.enqueue(path, jsonString)
            TransportResult.QueuedOffline
        }
    }
}
