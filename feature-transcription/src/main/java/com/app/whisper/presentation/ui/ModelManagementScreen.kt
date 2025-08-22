package com.app.whisper.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.presentation.viewmodel.ModelsUiState
import com.app.whisper.presentation.viewmodel.ModelsViewModel
import com.app.whisper.core.FormatUtils
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onBack: () -> Unit,
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: free space and Wi‑Fi only toggle
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Free space: ${FormatUtils.formatBytes(uiState.freeSpaceBytes)}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { viewModel.downloadAll(uiState.wifiOnly) }) { Text("Download All") }
                            OutlinedButton(onClick = { viewModel.cancelAllDownloads() }) { Text("Cancel All") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wi‑Fi only", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = uiState.wifiOnly, onCheckedChange = { viewModel.setWifiOnly(it) })
                    }
                }
            }
            uiState.items.forEach { item ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (item.isDefault) {
                                    Spacer(Modifier.width(8.dp))
                                    AssistChip(onClick = {}, label = { Text("Default") }, leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) })
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            when {
                                item.progress != null -> {
                                    LinearProgressIndicator(progress = item.progress, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(4.dp))
                                    Text("Downloading ${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                }
                                item.isDownloaded -> Text("Downloaded", color = MaterialTheme.colorScheme.primary)
                                else -> Text("Not downloaded", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Size: ${FormatUtils.formatBytes(item.model.sizeInMB.toLong() * 1024L * 1024L)}", style = MaterialTheme.typography.bodySmall)
                            if (!item.hasSpace) {
                                Spacer(Modifier.height(2.dp))
                                Text("Insufficient free space for this model", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            item.error?.let { err ->
                                Spacer(Modifier.height(4.dp))
                                Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.progress != null) {
                                TextButton(onClick = { viewModel.cancelDownload(item.model) }) { Text("Cancel") }
                            } else if (!item.isDownloaded) {
                                FilledTonalButton(onClick = { viewModel.startDownload(item.model) }, enabled = item.hasSpace) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download (FG)")
                                }
                                TextButton(onClick = { viewModel.enqueueBackgroundDownload(item.model, wifiOnly = uiState.wifiOnly) }, enabled = item.hasSpace) { Text("Background") }
                            } else {
                                OutlinedButton(onClick = { viewModel.deleteModel(item.model) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete")
                                }
                            }
                            TextButton(onClick = { viewModel.setDefault(item.model) }) { Text("Set default") }
                        }
                    }
                }
            }
        }
    }
}
