package com.luciddream.phone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.luciddream.model.DreamEntry
import com.luciddream.model.DreamTag
import kotlinx.coroutines.launch

@Composable
fun DreamJournalScreen(
    viewModel: DreamJournalViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadEntries()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Dream Entry")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Dream Journal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Top Dream Signs Cloud
            if (uiState.topDreamSigns.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Frequent Dream Signs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.topDreamSigns) { sign ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("${sign.keyword} (${sign.occurrenceCount})") }
                                )
                            }
                        }
                    }
                }
            }

            // Entries List
            if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No dream records yet.\nTap + to record your first dream!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.entries) { entry ->
                        DreamEntryCard(entry = entry)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDreamDialog(
            uiState = uiState,
            onTitleChange = { viewModel.updateDraftTitle(it) },
            onTranscriptChange = { viewModel.updateDraftTranscript(it) },
            onToggleTag = { viewModel.toggleTag(it) },
            onLucidityChange = { viewModel.setLucidityRating(it) },
            onSave = {
                coroutineScope.launch {
                    viewModel.saveCurrentEntry()
                    showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun DreamEntryCard(entry: DreamEntry) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val isLucid = entry.lucidityLevel > 0
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (isLucid) "Lucid (Lvl ${entry.lucidityLevel})" else "Non-Lucid",
                            fontSize = 11.sp
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isLucid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Text(
                text = entry.dateIso,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = entry.transcript,
                style = MaterialTheme.typography.bodyMedium
            )

            if (entry.dreamSigns.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(entry.dreamSigns) { sign ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text("⚡ ${sign.keyword}", fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDreamDialog(
    uiState: DreamJournalUiState,
    onTitleChange: (String) -> Unit,
    onTranscriptChange: (String) -> Unit,
    onToggleTag: (DreamTag) -> Unit,
    onLucidityChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Dream Record",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = uiState.draftTitle,
                    onValueChange = onTitleChange,
                    label = { Text("Dream Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.draftTranscript,
                    onValueChange = onTranscriptChange,
                    label = { Text("Dream Narrative / Memory") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Lucidity Level: ${uiState.draftLucidityRating} / 5",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = uiState.draftLucidityRating.toFloat(),
                    onValueChange = { onLucidityChange(it.toInt()) },
                    valueRange = 0f..5f,
                    steps = 4
                )

                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DreamTag.values()) { tag ->
                        val isSelected = uiState.draftTags.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleTag(tag) },
                            label = { Text(tag.name, fontSize = 11.sp) }
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = uiState.draftTranscript.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Extract Dream Signs")
                }
            }
        }
    }
}
