package com.app.whisper.presentation.ui

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.presentation.viewmodel.TranscriptionViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.OpenableColumns
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TranscriptionScreen(
    onOpenModels: () -> Unit = {},
    viewModel: TranscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val transcriptionResult by viewModel.transcriptionResult.collectAsState()

    val audioPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )

    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(audioPermissionState.status) {
        if (!audioPermissionState.status.isGranted) {
            snackbarHostState.showSnackbar("Microphone permission denied")
        }
    }
    var showCancelConfirm by remember { mutableStateOf(false) }
    val prevRecording = remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRecording) {
        if (uiState.isRecording && !prevRecording.value) {
            snackbarHostState.showSnackbar("Recording started")
        } else if (!uiState.isRecording && prevRecording.value) {
            snackbarHostState.showSnackbar(if (uiState.isProcessing) "Processing…" else "Recording stopped")
        }
        prevRecording.value = uiState.isRecording
    }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Whisper Transcription") },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Model Settings")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage Models") },
                                onClick = { expanded = false; onOpenModels() }
                            )
                            Divider()
                            WhisperModel.values().forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(model.displayName)
                                            if (model == uiState.selectedModel) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectModel(model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model: ${uiState.selectedModel.displayName}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        when {
                            uiState.isModelLoaded -> {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("Ready")
                                }
                            }
                            uiState.modelDownloadProgress != null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        progress = uiState.modelDownloadProgress!!,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Downloading ${(uiState.modelDownloadProgress!! * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { showCancelConfirm = true }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                            uiState.isModelDownloaded -> {
                                Text(text = "Initializing…", style = MaterialTheme.typography.labelMedium)
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Model not downloaded", color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = { viewModel.startModelDownload() }) { Text("Download") }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!uiState.isModelDownloaded && uiState.modelDownloadProgress == null && uiState.error != null) {
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = { viewModel.startModelDownload() }) { Text("Retry") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val languageLabels = mapOf(
                            "tr" to "Türkçe",
                            "auto" to "Otomatik Algılama",
                            "en" to "English",
                            "de" to "Deutsch",
                            "fr" to "Français",
                            "es" to "Español",
                            "it" to "Italiano",
                            "ru" to "Русский",
                            "ar" to "العربية",
                            "zh" to "中文",
                            "ja" to "日本語"
                        )
                        val languageOrder = listOf("tr", "auto", "en", "de", "fr", "es", "it", "ru", "ar", "zh", "ja")

                        Text(
                            text = "Language: ${languageLabels[uiState.selectedLanguage] ?: uiState.selectedLanguage.uppercase()}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var langMenu by remember { mutableStateOf(false) }
                            TextButton(onClick = { langMenu = true }) {
                                Text(languageLabels[uiState.selectedLanguage] ?: uiState.selectedLanguage.uppercase())
                            }
                            DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                                languageOrder.forEach { code ->
                                    DropdownMenuItem(
                                        text = { Text(languageLabels[code] ?: code.uppercase()) },
                                        onClick = {
                                            viewModel.setLanguage(code)
                                            langMenu = false
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(text = "Translate", style = MaterialTheme.typography.labelMedium)
                            Switch(
                                checked = uiState.translateEnabled,
                                onCheckedChange = { viewModel.setTranslate(it) },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Threads", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.setThreads(uiState.nThreads - 1) }, enabled = uiState.nThreads > 1) {
                                Icon(Icons.Default.Remove, contentDescription = "Dec threads")
                            }
                            Text("${uiState.nThreads}")
                            IconButton(onClick = { viewModel.setThreads(uiState.nThreads + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Inc threads")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (uiState.isRecording || uiState.waveformData.isNotEmpty()) {
                WaveformVisualizer(
                    waveformData = uiState.waveformData,
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
            if (uiState.isRecording) {
                Text(
                    text = formatDuration(uiState.recordingDuration),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Transcription", style = MaterialTheme.typography.titleMedium)
                        Row {
                            val clipboard = LocalClipboardManager.current
                            val context = LocalContext.current
                            IconButton(onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(uiState.lastTranscription))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied to clipboard")
                                }
                            }, enabled = uiState.lastTranscription.isNotEmpty()) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, uiState.lastTranscription)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share transcription"))
                                scope.launch { snackbarHostState.showSnackbar("Share sheet opened") }
                            }, enabled = uiState.lastTranscription.isNotEmpty()) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (uiState.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (uiState.lastTranscription.isEmpty()) {
                            Text(
                                text = "No transcription yet. Press the record button to start.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            SelectionContainer {
                                Text(text = uiState.lastTranscription, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val context = LocalContext.current
                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        if (uri != null) {
                            // Persist permission for the session
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            viewModel.transcribeFileUri(uri)
                        }
                    }
                )
                OutlinedButton(
                    onClick = {
                        try {
                            filePicker.launch(arrayOf("audio/wav", "audio/x-wav"))
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRecording && !uiState.isProcessing
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose File")
                }
                Spacer(modifier = Modifier.width(16.dp))
                FilledTonalButton(
                    onClick = {
                        if (uiState.isRecording) viewModel.stopRecording() else viewModel.startRecording()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isModelLoaded && !uiState.isProcessing,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isRecording) "Stop" else "Record")
                }
            }
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                    }
                ) { Text(error) }
            }

            if (showCancelConfirm) {
                AlertDialog(
                    onDismissRequest = { showCancelConfirm = false },
                    title = { Text("Cancel download?") },
                    text = { Text("The model file download will be canceled and the partial file removed.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showCancelConfirm = false
                            viewModel.cancelModelDownload()
                        }) { Text("Cancel download") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelConfirm = false }) { Text("Keep downloading") }
                    }
                )
            }
        }
    }
}

@Composable
fun WaveformVisualizer(
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (waveformData.isEmpty()) {
            drawLine(color = Color.Gray, start = Offset(0f, centerY), end = Offset(width, centerY), strokeWidth = 2f)
        } else {
            val barWidth = width / waveformData.size
            waveformData.forEachIndexed { index, amplitude ->
                val x = index * barWidth + barWidth / 2
                val barHeight = amplitude * height * 0.8f
                drawLine(
                    color = Color.Blue.copy(alpha = if (index.toFloat() / waveformData.size < animationProgress) 1f else 0.3f),
                    start = Offset(x, centerY - barHeight / 2),
                    end = Offset(x, centerY + barHeight / 2),
                    strokeWidth = barWidth * 0.8f
                )
            }
        }
    }

}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)


}
