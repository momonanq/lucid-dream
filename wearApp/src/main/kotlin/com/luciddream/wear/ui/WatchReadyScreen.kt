package com.luciddream.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun WatchReadyScreen(
    sensorFidelity: com.luciddream.wear.sensor.SourceFidelity = com.luciddream.wear.sensor.SourceFidelity.SIMULATED,
    onStartTracking: () -> Unit,
    onTestHaptic: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Lucid Watch",
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = when (sensorFidelity) {
                com.luciddream.wear.sensor.SourceFidelity.SAMSUNG_CONTINUOUS_IBI -> "● Samsung IBI Active"
                com.luciddream.wear.sensor.SourceFidelity.ANDROID_STANDARD_HR -> "● Standard Wear OS HR"
                com.luciddream.wear.sensor.SourceFidelity.SIMULATED -> "● Simulated Sensors"
            },
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onStartTracking,
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier.size(56.dp)
        ) {
            Text("🌙", fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompactChip(
            onClick = onTestHaptic,
            label = { Text("Test Haptic Tap", fontSize = 10.sp) }
        )
    }
}
