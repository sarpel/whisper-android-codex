package com.app.whisper.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.app.whisper.data.model.TranscriptionResult
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.data.repository.TranscriptionRepository
import com.app.whisper.feature.settings.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    private val repository: TranscriptionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    private val _transcriptionResult = MutableStateFlow<TranscriptionResult?>(null)
    val transcriptionResult: StateFlow<TranscriptionResult?> = _transcriptionResult.asStateFlow()

    private var recordingJob: Job? = null
    private var modelDownloadJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    init {
        loadPreferencesAndModel()
    }

    private fun loadPreferencesAndModel() {
        viewModelScope.launch {
            // Load persisted preferences first
            val language = preferencesManager.getLanguage()
            val translate = preferencesManager.getTranslate()
            var threads = preferencesManager.getThreads()
            val avail = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            if (threads <= 0 || (threads == 4 && avail > 4)) {
                threads = avail
                preferencesManager.setThreads(threads)
            }
            _uiState.update { it.copy(selectedLanguage = language, translateEnabled = translate, nThreads = threads) }

            val selectedModel = preferencesManager.getSelectedModel()
            _uiState.update { it.copy(selectedModel = selectedModel, isModelLoaded = false, error = null) }

            val downloaded = repository.isModelDownloaded(selectedModel)
            _uiState.update { it.copy(isModelDownloaded = downloaded) }

            if (!downloaded) {
                startModelDownloadInternal(selectedModel)
                return@launch
            }

            repository.initializeModel(selectedModel, _uiState.value.nThreads)
                .onSuccess {
                    _uiState.update { it.copy(isModelLoaded = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isModelLoaded = false, error = error.message) }
                }
        }
    }

    fun startModelDownload() {
        val model = _uiState.value.selectedModel
        if (_uiState.value.modelDownloadProgress != null) return
        viewModelScope.launch { startModelDownloadInternal(model) }
    }

    private suspend fun startModelDownloadInternal(model: WhisperModel) {
        _uiState.update { it.copy(modelDownloadProgress = 0f, error = null) }
        modelDownloadJob?.cancel()
        modelDownloadJob = viewModelScope.launch {
            val result = repository.downloadModel(model) { progress ->
                _uiState.update { s -> s.copy(modelDownloadProgress = progress.coerceIn(0f, 1f)) }
            }
            result.onSuccess {
                _uiState.update { it.copy(isModelDownloaded = true, modelDownloadProgress = null) }
                repository.initializeModel(model, _uiState.value.nThreads)
                    .onSuccess { _uiState.update { it.copy(isModelLoaded = true) } }
                    .onFailure { e -> _uiState.update { it.copy(isModelLoaded = false, error = e.message) } }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(modelDownloadProgress = null) }
                } else {
                    _uiState.update { it.copy(modelDownloadProgress = null, error = e.message) }
                }
            }
        }
    }

    fun cancelModelDownload() {
        modelDownloadJob?.cancel()
        modelDownloadJob = null
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        audioBuffer.clear()
        _uiState.update { it.copy(isRecording = true, isProcessing = false) }

        recordingJob = viewModelScope.launch {
            try {
                repository.startRecording()
                    .collect { audioChunk ->
                        audioBuffer.add(audioChunk)

                        _uiState.update { currentState ->
                            val secondsDelta = (audioChunk.size / 16000f).toInt().coerceAtLeast(1)
                            currentState.copy(
                                recordingDuration = currentState.recordingDuration + secondsDelta,
                                waveformData = calculateWaveform(audioChunk)
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecording = false, error = e.message ?: "Recording failed") }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        repository.stopRecording()

        _uiState.update { it.copy(isRecording = false, isProcessing = true) }

        viewModelScope.launch {
            val fullAudio = combineAudioChunks(audioBuffer)

            repository.transcribeAudio(
                audioData = fullAudio,
                language = _uiState.value.selectedLanguage,
                translate = _uiState.value.translateEnabled
            )
                .onSuccess { result ->
                    _transcriptionResult.value = result
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            lastTranscription = result.text
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun transcribeFileUri(uri: Uri) {
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            repository.transcribeContentUri(uri)
                .collect { result ->
                    _transcriptionResult.value = result
                    _uiState.update { it.copy(isProcessing = false, lastTranscription = result.text) }
                }
        }
    }

    fun selectModel(model: WhisperModel) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedModel = model, isModelLoaded = false, isModelDownloaded = false, modelDownloadProgress = null, error = null) }
            preferencesManager.setSelectedModel(model)
            loadPreferencesAndModel()
        }
    }

    fun setTranslate(enabled: Boolean) {
        _uiState.update { it.copy(translateEnabled = enabled) }
        viewModelScope.launch { preferencesManager.setTranslate(enabled) }
    }

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
        viewModelScope.launch { preferencesManager.setLanguage(lang) }
    }

    fun setThreads(n: Int) {
        val threads = n.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        _uiState.update { it.copy(nThreads = threads) }
        viewModelScope.launch { preferencesManager.setThreads(threads) }
        // Optionally reinitialize model with new threads on next init
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val newTheme = !_uiState.value.isDarkTheme
            _uiState.update { it.copy(isDarkTheme = newTheme) }
            preferencesManager.setDarkTheme(newTheme)
        }
    }

    private fun combineAudioChunks(chunks: List<FloatArray>): FloatArray {
        val totalSize = chunks.sumOf { it.size }
        val combined = FloatArray(totalSize)
        var offset = 0

        chunks.forEach { chunk ->
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }

        return combined
    }

    private fun calculateWaveform(audioData: FloatArray): List<Float> {
        return audioData.toList().chunked(audioData.size / 50).map { chunk ->
            chunk.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        }
    }
}

data class TranscriptionUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isModelDownloaded: Boolean = false,
    val modelDownloadProgress: Float? = null,
    val selectedModel: WhisperModel = WhisperModel.TINY,
    val selectedLanguage: String = "auto",
    val translateEnabled: Boolean = false,
    val nThreads: Int = 4,
    val isDarkTheme: Boolean = false,
    val recordingDuration: Int = 0,
    val waveformData: List<Float> = emptyList(),
    val lastTranscription: String = "",
    val error: String? = null
)
