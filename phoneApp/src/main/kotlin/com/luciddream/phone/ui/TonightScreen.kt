package com.luciddream.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luciddream.data.sync.AndroidPhoneWearableTransportGateway
import com.luciddream.model.NightMode
import com.luciddream.phone.audio.AndroidTlrAudioEngine
import com.luciddream.phone.service.PhoneDependencies
import kotlinx.coroutines.launch

@Composable
fun TonightScreen(
    viewModel: TonightViewModel,
    audioEngine: AndroidTlrAudioEngine,
    transportGateway: AndroidPhoneWearableTransportGateway,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var testFeedbackText by remember { mutableStateOf<String?>(null) }
    var audioVolume by remember { mutableFloatStateOf(0.25f) }

    LaunchedEffect(Unit) {
        viewModel.loadState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Text(
            text = "Tonight's Protocol",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Active Session Banner (if running)
        if (uiState.activeSession != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tracking in Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Mode: ${uiState.activeSession?.mode?.name}")
                    Text("Cues Delivered: ${uiState.activeSession?.cuesTriggered} / ${uiState.activeSession?.cuesPlanned}")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val s = uiState.activeSession ?: return@launch
                                val stopPayload = com.luciddream.data.sync.StopSessionPayload(
                                    sessionId = s.id,
                                    endTimeMs = System.currentTimeMillis(),
                                    stoppedBy = "PHONE"
                                )
                                transportGateway.sendStopSession(stopPayload)
                                viewModel.loadState()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Sleep Tracking")
                    }
                }
            }
        }

        // Night Mode Selector
        Text(
            text = "Select Sleep Induction Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NightMode.values()) { mode ->
                val isSelected = uiState.selectedMode == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectMode(mode) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = mode.name,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Audio & Haptic Settings Card
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Targeted Lucidity Actuation (TLR)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Chimes (432 Hz)")
                    Switch(
                        checked = uiState.audioCueEnabled,
                        onCheckedChange = { viewModel.toggleAudioCues(it) }
                    )
                }

                Text(
                    text = "Chime Volume: ${(audioVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = audioVolume,
                    onValueChange = { audioVolume = it },
                    valueRange = 0.05f..0.60f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                testFeedbackText = "Playing 432 Hz Sine Chime..."
                                audioEngine.playLucidityChime(volume = audioVolume.toDouble())
                                testFeedbackText = "Chime complete."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Chime", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                testFeedbackText = "Playing 6 Hz Theta Beat..."
                                audioEngine.playBinauralThetaBeat(volume = audioVolume.toDouble())
                                testFeedbackText = "Theta beat complete."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Theta Beat", fontSize = 12.sp)
                    }
                }

                if (testFeedbackText != null) {
                    Text(
                        text = testFeedbackText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Stats Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(label = "Past 7 Nights", value = "${uiState.past7NightsCount}")
                StatItem(label = "Dream Recall", value = "${uiState.past7NightsRecallCount}")
                StatItem(label = "Lucid Dreams", value = "${uiState.past7NightsLucidCount}")
            }
        }

        // Start Sleep Button
        Button(
            onClick = {
                coroutineScope.launch {
                    val session = viewModel.startTonightSession()
                    val payload = com.luciddream.data.sync.StartSessionPayload(
                        sessionId = session.id,
                        mode = session.mode,
                        startTimeMs = session.startTimeMs,
                        earliestCueMinutes = session.earliestCueMinutes,
                        cooldownMinutes = session.cooldownMinutes,
                        maxCues = session.cuesPlanned,
                        hapticIntensity = session.hapticIntensity,
                        audioEnabled = session.audioEnabled
                    )
                    transportGateway.sendStartSession(payload)
                }
            },
            enabled = uiState.activeSession == null && !uiState.isStarting,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isStarting) "Starting..." else "Start Sleep Tracking",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
