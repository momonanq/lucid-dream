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

@Composable
fun WatchMorningFeedbackScreen(
    onSubmit: (hadDream: Boolean, hadLucid: Boolean, noticedSignal: Boolean) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(1) }
    var hadDream by remember { mutableStateOf(false) }
    var hadLucid by remember { mutableStateOf(false) }
    var noticedSignal by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val question = when (step) {
            1 -> "Did you dream?"
            2 -> "Was your dream lucid?"
            else -> "Did you notice the cue?"
        }

        Text(
            text = "Morning Check ($step/3)",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = question,
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = {
                    when (step) {
                        1 -> { hadDream = true; step = 2 }
                        2 -> { hadLucid = true; step = 3 }
                        3 -> { noticedSignal = true; onSubmit(hadDream, hadLucid, noticedSignal) }
                    }
                },
                colors = ButtonDefaults.primaryButtonColors(),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Text("Yes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    when (step) {
                        1 -> { hadDream = false; onSubmit(false, false, false) }
                        2 -> { hadLucid = false; step = 3 }
                        3 -> { noticedSignal = false; onSubmit(hadDream, hadLucid, false) }
                    }
                },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Text("No", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier.fillMaxWidth(0.6f).height(24.dp)
        ) {
            Text("Skip", fontSize = 9.sp, color = MaterialTheme.colors.onSurfaceVariant)
        }
    }
}
