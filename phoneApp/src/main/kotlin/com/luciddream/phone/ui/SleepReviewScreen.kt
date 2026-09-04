package com.luciddream.phone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luciddream.data.repository.NightSessionRepository
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun SleepReviewScreen(
    viewModel: SleepReviewViewModel,
    sessionRepository: NightSessionRepository,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        val sessions = sessionRepository.getAllSessions().firstOrNull() ?: emptyList()
        val latestCompleted = sessions.firstOrNull { it.status == com.luciddream.model.SessionStatus.COMPLETED }
        if (latestCompleted != null) {
            viewModel.loadSessionReview(latestCompleted.id)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sleep & Calibration Review",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (uiState.session == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No completed sleep sessions found.\nComplete your first night to view analytics and personalization metrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val session = uiState.session!!

            // Session Overview Card
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NightsStay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Last Night (${session.mode.name})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val durationMinutes = if (session.endTimeMs != null) (session.endTimeMs!! - session.startTimeMs) / 60000 else 0
                    Text("Duration: ${durationMinutes / 60}h ${durationMinutes % 60}m")
                    Text("Cues Delivered: ${session.cuesTriggered} / ${session.cuesPlanned}")
                    Text("Wake Spikes Recorded: ${session.cueEvents.count { it.wakeSpikeAfter }}")
                }
            }

            // Morning Feedback Card
            uiState.morningReport?.let { report ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Text("Morning Subjective Feedback", fontWeight = FontWeight.Bold)
                        }
                        Text("Dreams recalled: ${if (report.hadDreams) "Yes" else "No"}")
                        Text("Lucid Dream achieved: ${if (report.lucidSuccess) "Yes ⭐" else "No"}")
                        Text("Cue incorporated in dream: ${if (report.cueDetectedInDream) "Yes ⚡" else "No"}")
                    }
                }
            }

            // Calibration & Threshold Adaptation Card
            uiState.calibrationResult?.let { cal ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("Algorithmic Adaptation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Text("Adapted REM Confidence Threshold: ${"%.2f".format(cal.adaptedProfile.confidenceThreshold)}")
                        Text("Calibrated Baseline Heart Rate: ${"%.1f".format(cal.adaptedProfile.baselineHeartRate)} BPM")
                        Text("Preferred Haptic Intensity: ${"%.2f".format(cal.adaptedProfile.preferredHapticIntensity)}")
                        Text("Calibration Nights Completed: ${cal.adaptedProfile.calibrationNightsCompleted}")

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recommendations: ${cal.recommendations.joinToString("; ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
