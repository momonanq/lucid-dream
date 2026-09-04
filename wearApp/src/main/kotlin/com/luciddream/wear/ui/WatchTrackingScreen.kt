package com.luciddream.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.luciddream.model.NightSession

@Composable
fun WatchTrackingScreen(
    session: NightSession?,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmStop by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tracking Sleep...",
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Cues: ${session?.cuesTriggered ?: 0} / ${session?.cuesPlanned ?: 5}",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (!confirmStop) {
            Button(
                onClick = { confirmStop = true },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(36.dp)
            ) {
                Text("End Sleep", fontSize = 12.sp)
            }
        } else {
            Button(
                onClick = onStopTracking,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(36.dp)
            ) {
                Text("Confirm Wake Up", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
