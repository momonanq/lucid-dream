package com.luciddream.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status as OngoingActivityStatus
import com.luciddream.data.db.LucidDatabase
import com.luciddream.data.sync.AndroidWatchWearableTransportGateway
import com.luciddream.data.sync.RoomOfflineEventQueue
import com.luciddream.data.sync.StartSessionPayload
import com.luciddream.data.sync.WatchWearableTransportGateway
import com.luciddream.model.NightSession
import com.luciddream.model.UserProfile
import com.luciddream.wear.haptic.AndroidWatchHapticEngine
import com.luciddream.wear.sensor.SamsungSensorManager
import com.luciddream.wear.ui.WatchMainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Foreground Service running on Wear OS for the duration of sleep tracking.
 * Maintains PARTIAL_WAKE_LOCK, posts Ongoing Activity notification, collects sensor windows,
 * actuates haptic cues, and synchronizes events via Wearable Data Layer.
 */
class WatchTrackingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "lucid_sleep_tracking"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.luciddream.wear.action.START_TRACKING"
        const val ACTION_STOP = "com.luciddream.wear.action.STOP_TRACKING"
        const val EXTRA_PAYLOAD = "extra_start_payload"

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _activeSession = MutableStateFlow<NightSession?>(null)
        val activeSession: StateFlow<NightSession?> = _activeSession.asStateFlow()

        private val _sensorFidelity = MutableStateFlow(com.luciddream.wear.sensor.SourceFidelity.SIMULATED)
        val sensorFidelity: StateFlow<com.luciddream.wear.sensor.SourceFidelity> = _sensorFidelity.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var trackingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val dutyCycleManager = com.luciddream.wear.sensor.BatteryDutyCycleManager()

    private lateinit var sensorManager: SamsungSensorManager
    private lateinit var hapticEngine: AndroidWatchHapticEngine
    private lateinit var trackingService: WatchNightTrackingService
    private lateinit var transportGateway: WatchWearableTransportGateway

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val dataSource = com.luciddream.wear.sensor.SensorDataSourceFactory.create(applicationContext)
        _sensorFidelity.value = dataSource.fidelity

        sensorManager = SamsungSensorManager(dataSource)
        hapticEngine = AndroidWatchHapticEngine(applicationContext)
        trackingService = WatchNightTrackingService(
            sensorManager = sensorManager,
            hapticEngine = hapticEngine
        )

        val db = LucidDatabase.getInstance(applicationContext)
        val offlineQueue = RoomOfflineEventQueue(db.queuedSyncEventDao())
        transportGateway = AndroidWatchWearableTransportGateway(applicationContext, offlineQueue)

        trackingService.onCueTriggeredCallbacks.add { payload ->
            transportGateway.sendCueTriggered(payload)
        }
        trackingService.onWakeSpikeCallbacks.add { payload ->
            transportGateway.sendWakeSpike(payload)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val payloadJson = intent?.getStringExtra(EXTRA_PAYLOAD)
                val payload = if (payloadJson != null) {
                    runCatching { Json.decodeFromString<StartSessionPayload>(payloadJson) }.getOrNull()
                } else null

                startTracking(payload)
            }
        }
        return START_STICKY
    }

    private fun startTracking(payload: StartSessionPayload?) {
        acquireWakeLock()
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        val activePayload = payload ?: StartSessionPayload(
            sessionId = "session_local_${System.currentTimeMillis()}",
            mode = com.luciddream.model.NightMode.WATCH_ASSIST,
            startTimeMs = System.currentTimeMillis(),
            earliestCueMinutes = 90,
            cooldownMinutes = 15,
            maxCues = 5,
            hapticIntensity = 0.5,
            audioEnabled = true
        )

        val session = trackingService.startSession(activePayload)
        _activeSession.value = session
        _isTracking.value = true

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            var lastWindowEnd = System.currentTimeMillis()

            // The SDK answers a policy refusal within a second of the tracker being attached.
            // Surfacing it here rather than a full window later means the user learns the source
            // degraded while still looking at the watch, not an hour into the night.
            delay(5_000L)
            _sensorFidelity.value = sensorManager.currentFidelity

            while (isActive) {
                delay(60_000L) // 60s window aggregation cycle
                val now = System.currentTimeMillis()
                val elapsedMinutes = (now - session.startTimeMs) / 60000L
                val battery = com.luciddream.wear.sensor.BatteryDutyCycleManager.getCurrentBatteryPercentage(applicationContext)
                val decision = dutyCycleManager.evaluate(elapsedMinutes, battery)
                sensorManager.setSamplingPolicy(decision.policy)

                val window = trackingService.processSensorWindow(lastWindowEnd, now)
                lastWindowEnd = now
                _activeSession.value = trackingService.currentSession.value

                // Fidelity is not fixed at creation: the Samsung source downgrades itself when the
                // SDK binds but refuses data (no partner approval or developer mode) and promotes
                // back when real intervals arrive. Re-reading it here keeps the watch indicator
                // showing what is actually being measured.
                _sensorFidelity.value = sensorManager.currentFidelity
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        val finished = trackingService.stopSession()
        _activeSession.value = null
        _isTracking.value = false

        serviceScope.launch {
            transportGateway.drainOfflineQueue()
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LucidDream:TrackingWakeLock").apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 3600 * 1000L) // Safety clamp 10 hours
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun buildForegroundNotification(): Notification {
        val tapIntent = Intent(this, WatchMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Lucid Dream Tracking")
            .setContentText("Monitoring sleep stages and REM cues")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            NOTIFICATION_ID,
            builder
        )
            .setAnimatedIcon(android.R.drawable.ic_popup_sync)
            .setStaticIcon(android.R.drawable.ic_dialog_info)
            .setTouchIntent(pendingIntent)
            .setStatus(OngoingActivityStatus.Builder().addPart("status", OngoingActivityStatus.TextPart("Tracking Sleep")).build())
            .build()
        ongoingActivity.apply(applicationContext)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lucid dream nocturnal sleep tracking and REM cueing"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        _isTracking.value = false
        _activeSession.value = null
    }
}
