package com.luciddream.phone.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luciddream.algorithm.PilotValidationEngine
import com.luciddream.data.repository.NightSessionRepository
import com.luciddream.model.NightSession
import com.luciddream.model.SleepImport
import com.luciddream.model.SleepStage
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun SleepReviewScreen(
    viewModel: SleepReviewViewModel,
    sessionRepository: NightSessionRepository,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

            // 1. Session Overview Card
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

            // 2. Interactive Hypnogram & Stage Timeline
            HypnogramCard(
                session = session,
                sleepImport = uiState.sleepImport
            )

            // 3. Pilot Validation & Hit-rate Card
            uiState.validationMetrics?.let { metrics ->
                PilotValidationCard(
                    metrics = metrics,
                    csvData = uiState.pilotCsvData,
                    sessionId = session.id,
                    onExportCsv = { csv ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "lucid_pilot_${session.id}.csv")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export Pilot CSV"))
                    }
                )
            }

            // 4. Morning Feedback Card
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

            // 5. Calibration & Threshold Adaptation Card
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

@Composable
fun HypnogramCard(
    session: NightSession,
    sleepImport: SleepImport?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Hypnogram & REM Prediction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val stages = sleepImport?.stages ?: emptyList()
            val sessionDurationMs = if (session.endTimeMs != null && session.endTimeMs!! > session.startTimeMs) {
                session.endTimeMs!! - session.startTimeMs
            } else 8 * 3600 * 1000L

            // Canvas Timeline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // 1. Draw ground truth stages as stacked horizontal blocks
                    if (stages.isNotEmpty()) {
                        for (stage in stages) {
                            val stageStartRel = ((stage.startTimestampMs - session.startTimeMs).coerceAtLeast(0L)).toFloat() / sessionDurationMs
                            val stageEndRel = ((stage.endTimestampMs - session.startTimeMs).coerceAtLeast(0L)).toFloat() / sessionDurationMs

                            val blockX = stageStartRel * width
                            val blockWidth = ((stageEndRel - stageStartRel) * width).coerceAtLeast(2f)

                            val (blockY, blockHeight, color) = when (stage.stage) {
                                SleepStage.AWAKE -> Triple(0f, height * 0.20f, Color(0xFFF59E0B))
                                SleepStage.REM -> Triple(height * 0.25f, height * 0.25f, Color(0xFFA855F7))
                                SleepStage.LIGHT -> Triple(height * 0.55f, height * 0.22f, Color(0xFF60A5FA))
                                SleepStage.DEEP -> Triple(height * 0.80f, height * 0.20f, Color(0xFF1E3A8A))
                            }

                            drawRoundRect(
                                color = color,
                                topLeft = Offset(blockX, blockY),
                                size = Size(blockWidth, blockHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }

                    // 2. Draw algorithm REM confidence line
                    val windows = session.sensorWindows
                    if (windows.size > 1) {
                        val path = Path()
                        for ((i, w) in windows.withIndex()) {
                            val midMs = (w.startTimestampMs + w.endTimestampMs) / 2
                            val relX = ((midMs - session.startTimeMs).coerceAtLeast(0L)).toFloat() / sessionDurationMs * width
                            // Invert: confidence 1.0 is near top of REM band (height * 0.25f), 0.0 is near bottom
                            val relY = height * 0.85f - (w.confidence.toFloat() * height * 0.65f)

                            if (i == 0) path.moveTo(relX, relY) else path.lineTo(relX, relY)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF10B981),
                            style = Stroke(width = 3f)
                        )
                    }

                    // 3. Draw cue delivery marker circles
                    for (cue in session.cueEvents) {
                        val cueRelX = ((cue.timestampMs - session.startTimeMs).coerceAtLeast(0L)).toFloat() / sessionDurationMs * width
                        drawCircle(
                            color = Color.Yellow,
                            radius = 6f,
                            center = Offset(cueRelX, height * 0.25f)
                        )
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(color = Color(0xFFA855F7), label = "REM")
                LegendItem(color = Color(0xFF1E3A8A), label = "Deep")
                LegendItem(color = Color(0xFF60A5FA), label = "Light")
                LegendItem(color = Color(0xFFF59E0B), label = "Awake")
                LegendItem(color = Color(0xFF10B981), label = "Algo Score")
            }
        }
    }
}

@Composable
fun PilotValidationCard(
    metrics: PilotValidationEngine.ValidationMetrics,
    csvData: String?,
    sessionId: String,
    onExportCsv: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null)
                    Text(
                        text = "Pilot Validation Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (csvData != null) {
                    IconButton(onClick = { onExportCsv(csvData) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Pilot CSV")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(title = "Hit Rate", value = "${(metrics.hitRate * 100).toInt()}%")
                MetricColumn(title = "Precision", value = "${(metrics.precision * 100).toInt()}%")
                MetricColumn(title = "Specificity", value = "${(metrics.specificity * 100).toInt()}%")
                MetricColumn(title = "F1 Score", value = "%.2f".format(metrics.f1Score))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Ground Truth REM Windows: ${metrics.remWindows} | True Hits: ${metrics.truePositives} | False Alarms: ${metrics.falsePositives} | Misses: ${metrics.falseNegatives}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (csvData != null) {
                Button(
                    onClick = { onExportCsv(csvData) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Pilot Dataset (.csv)")
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = title, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(text = label, fontSize = 10.sp)
    }
}
