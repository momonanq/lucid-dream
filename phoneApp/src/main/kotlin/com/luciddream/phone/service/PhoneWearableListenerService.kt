package com.luciddream.phone.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.luciddream.data.sync.CueTriggeredPayload
import com.luciddream.data.sync.QuickMorningFeedbackPayload
import com.luciddream.data.sync.WakeSpikePayload
import com.luciddream.data.sync.WearSyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Listens for asynchronous nocturnal events streamed from the watch over Wearable Data Layer.
 * Synchronizes live audio cues on the phone, updates Room session state, and processes morning reports.
 */
class PhoneWearableListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val coordinator = PhoneDependencies.getCoordinator(applicationContext)

        when (messageEvent.path) {
            WearSyncPaths.PATH_CUE_TRIGGERED -> {
                val payloadJson = String(messageEvent.data, Charsets.UTF_8)
                runCatching { json.decodeFromString<CueTriggeredPayload>(payloadJson) }
                    .onSuccess { payload ->
                        serviceScope.launch {
                            coordinator.handleLiveCueEvent(payload)
                        }
                    }
            }

            WearSyncPaths.PATH_WAKE_SPIKE -> {
                val payloadJson = String(messageEvent.data, Charsets.UTF_8)
                runCatching { json.decodeFromString<WakeSpikePayload>(payloadJson) }
                    .onSuccess { payload ->
                        serviceScope.launch {
                            coordinator.handleWakeSpikeEvent(payload)
                        }
                    }
            }

            WearSyncPaths.PATH_MORNING_FEEDBACK -> {
                val payloadJson = String(messageEvent.data, Charsets.UTF_8)
                runCatching { json.decodeFromString<QuickMorningFeedbackPayload>(payloadJson) }
                    .onSuccess { payload ->
                        serviceScope.launch {
                            coordinator.completeMorningSession(
                                sessionId = payload.sessionId,
                                morningFeedback = payload
                            )
                        }
                    }
            }
        }
    }
}
