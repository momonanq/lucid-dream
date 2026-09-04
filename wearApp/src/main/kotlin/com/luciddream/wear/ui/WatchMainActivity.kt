package com.luciddream.wear.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import com.luciddream.data.db.LucidDatabase
import com.luciddream.data.sync.AndroidWatchWearableTransportGateway
import com.luciddream.data.sync.QuickMorningFeedbackPayload
import com.luciddream.data.sync.RoomOfflineEventQueue
import com.luciddream.wear.haptic.AndroidWatchHapticEngine
import com.luciddream.wear.service.WatchTrackingForegroundService
import kotlinx.coroutines.launch

enum class WatchUiState {
    READY,
    TRACKING,
    FEEDBACK
}

class WatchMainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = mutableListOf(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }

        val hapticEngine = AndroidWatchHapticEngine(this)
        val db = LucidDatabase.getInstance(applicationContext)
        val transportGateway = AndroidWatchWearableTransportGateway(
            context = applicationContext,
            offlineQueue = RoomOfflineEventQueue(db.queuedSyncEventDao())
        )

        setContent {
            MaterialTheme {
                val isTracking by WatchTrackingForegroundService.isTracking.collectAsState()
                val activeSession by WatchTrackingForegroundService.activeSession.collectAsState()
                val sensorFidelity by WatchTrackingForegroundService.sensorFidelity.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf(WatchUiState.READY) }
                var lastSessionId by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(isTracking) {
                    if (isTracking) {
                        currentScreen = WatchUiState.TRACKING
                    } else if (currentScreen == WatchUiState.TRACKING) {
                        currentScreen = WatchUiState.FEEDBACK
                    }
                }

                when (currentScreen) {
                    WatchUiState.READY -> {
                        WatchReadyScreen(
                            sensorFidelity = sensorFidelity,
                            onStartTracking = {
                                val intent = Intent(this@WatchMainActivity, WatchTrackingForegroundService::class.java).apply {
                                    action = WatchTrackingForegroundService.ACTION_START
                                }
                                ContextCompat.startForegroundService(this@WatchMainActivity, intent)
                            },
                            onTestHaptic = {
                                coroutineScope.launch {
                                    hapticEngine.playTestTap()
                                }
                            }
                        )
                    }

                    WatchUiState.TRACKING -> {
                        WatchTrackingScreen(
                            session = activeSession,
                            onStopTracking = {
                                lastSessionId = activeSession?.id
                                val intent = Intent(this@WatchMainActivity, WatchTrackingForegroundService::class.java).apply {
                                    action = WatchTrackingForegroundService.ACTION_STOP
                                }
                                startService(intent)
                                currentScreen = WatchUiState.FEEDBACK
                            }
                        )
                    }

                    WatchUiState.FEEDBACK -> {
                        WatchMorningFeedbackScreen(
                            onSubmit = { hadDream, hadLucid, noticedSignal ->
                                coroutineScope.launch {
                                    val sid = lastSessionId ?: "session_unknown"
                                    val feedback = QuickMorningFeedbackPayload(
                                        sessionId = sid,
                                        timestampMs = System.currentTimeMillis(),
                                        hadDream = hadDream,
                                        hadLucidDream = hadLucid,
                                        noticedSignal = noticedSignal
                                    )
                                    transportGateway.sendQuickFeedback(feedback)
                                    currentScreen = WatchUiState.READY
                                }
                            },
                            onSkip = {
                                currentScreen = WatchUiState.READY
                            }
                        )
                    }
                }
            }
        }
    }
}
