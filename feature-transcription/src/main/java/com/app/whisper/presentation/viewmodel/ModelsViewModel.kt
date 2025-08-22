package com.app.whisper.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.data.repository.TranscriptionRepository
import com.app.whisper.feature.settings.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.work.*
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.collectLatest
import com.app.whisper.work.ModelDownloadWorker

data class ModelItemState(
    val model: WhisperModel,
    val isDownloaded: Boolean = false,
    val isDefault: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
    val hasSpace: Boolean = true
)

data class ModelsUiState(
    val items: List<ModelItemState> = emptyList(),
    val freeSpaceBytes: Long = 0L,
    val wifiOnly: Boolean = false
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val repository: TranscriptionRepository,
    private val preferences: PreferencesManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    private val jobs = mutableMapOf<WhisperModel, Job>()
    private val observers = mutableMapOf<WhisperModel, Job>()

    init {
        viewModelScope.launch {
            val wifi = preferences.getWifiOnlyDownloads()
            _uiState.update { it.copy(wifiOnly = wifi) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val defaultModel = preferences.getSelectedModel()
            val free = appContext.filesDir.usableSpace
            val items = WhisperModel.values().map { m ->
                ModelItemState(
                    model = m,
                    isDownloaded = repository.isModelDownloaded(m),
                    isDefault = (m == defaultModel),
                    hasSpace = hasFreeSpaceFor(m, free)
                )
            }
            _uiState.value = ModelsUiState(items, freeSpaceBytes = free, wifiOnly = _uiState.value.wifiOnly)
            // Begin observing any running background work
            WhisperModel.values().forEach { observeBackgroundProgress(it) }
        }
    }

    fun setDefault(model: WhisperModel) {
        viewModelScope.launch {
            preferences.setSelectedModel(model)
            _uiState.update { state ->
                state.copy(items = state.items.map { it.copy(isDefault = it.model == model) })
            }
        }
    }

    fun startDownload(model: WhisperModel) {
        if (jobs[model]?.isActive == true) return
        if (!hasFreeSpaceFor(model)) {
            updateItem(model) { it.copy(error = "Insufficient storage space") }
            return
        }
        val job = viewModelScope.launch {
            updateItem(model) { it.copy(progress = 0f, error = null) }
            val result = repository.downloadModel(model) { p ->
                updateItem(model) { s -> s.copy(progress = p.coerceIn(0f, 1f)) }
            }
            result.onSuccess {
                updateItem(model) { it.copy(progress = null, isDownloaded = true) }
            }.onFailure { e ->
                updateItem(model) { it.copy(progress = null, error = e.message) }
            }
        }
        jobs[model] = job
    }

    fun cancelDownload(model: WhisperModel) {
        jobs[model]?.cancel()
        jobs.remove(model)
        updateItem(model) { it.copy(progress = null) }
    }

    fun deleteModel(model: WhisperModel) {
        if (repository.deleteModel(model)) {
            updateItem(model) { it.copy(isDownloaded = false) }
        }
    }

    // Background download via WorkManager
    fun enqueueBackgroundDownload(model: WhisperModel, wifiOnly: Boolean = _uiState.value.wifiOnly) {
        if (!hasFreeSpaceFor(model)) {
            updateItem(model) { it.copy(error = "Insufficient storage space") }
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(ModelDownloadWorker.inputData(model))
            .setConstraints(constraints)
            .build()
        val name = uniqueWorkName(model)
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        observeBackgroundProgress(model)
    }

    fun cancelBackgroundDownload(model: WhisperModel) {
        WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName(model))
    }

    private fun observeBackgroundProgress(model: WhisperModel) {
        if (observers[model]?.isActive == true) return
        val name = uniqueWorkName(model)
        val job = viewModelScope.launch {
            WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkLiveData(name)
                .asFlow()
                .collectLatest { infos ->
                    val info = infos.firstOrNull() ?: return@collectLatest
                    when (info.state) {
                        WorkInfo.State.RUNNING -> {
                            val p = info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                            updateItem(model) { it.copy(progress = p, error = null) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            updateItem(model) { it.copy(progress = null, isDownloaded = true, error = null) }
                        }
                        WorkInfo.State.FAILED -> {
                            updateItem(model) { it.copy(progress = null, error = "Background download failed") }
                        }
                        WorkInfo.State.CANCELLED -> {
                            updateItem(model) { it.copy(progress = null) }
                        }
                        else -> Unit
                    }
                }
        }
        observers[model] = job
    }

    private fun uniqueWorkName(model: WhisperModel) = "model_download_${'$'}{model.name}"

    private fun hasFreeSpaceFor(model: WhisperModel, free: Long = appContext.filesDir.usableSpace): Boolean {
        val need = (model.sizeInMB.toLong() + 10L) * 1024L * 1024L // +10MB buffer
        return free > need
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            preferences.setWifiOnlyDownloads(value)
            _uiState.update { it.copy(wifiOnly = value) }
        }
    }

    fun downloadAll(wifiOnly: Boolean = _uiState.value.wifiOnly) {
        val free = appContext.filesDir.usableSpace
        WhisperModel.values().forEach { model ->
            val item = _uiState.value.items.firstOrNull { it.model == model }
            val already = item?.isDownloaded == true
            val hasSpace = hasFreeSpaceFor(model, free)
            if (!already && hasSpace) {
                enqueueBackgroundDownload(model, wifiOnly)
            }
        }
    }

    fun cancelAllDownloads() {
        WhisperModel.values().forEach { model ->
            cancelBackgroundDownload(model)
        }
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _uiState.update { state ->
            state.copy(items = state.items.map { it.copy(progress = null) })
        }
    }

    private fun updateItem(model: WhisperModel, transform: (ModelItemState) -> ModelItemState) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.model == model) transform(it) else it })
        }
    }
}
