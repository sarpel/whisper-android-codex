# Whisper Android Application Implementation Plan

## Project Overview
**Objective**: Develop a native Android application utilizing whisper.cpp for on-device speech-to-text transcription on ARM v8 processors. The application will feature model selection, theme support, multiple audio input methods, and modular architecture for future extensibility.

**Target Platform**: Android 7.0+ (API 24+) optimized for ARM v8 (aarch64) architecture
**Development Environment**: Windows 10/11 with Android Studio
**Core Technology**: whisper.cpp with JNI bindings, Kotlin, Jetpack Compose

## Architecture Overview

### Layer Structure
```
┌─────────────────────────────────────┐
│     Presentation Layer (UI)         │
│    Jetpack Compose + Material 3     │
├─────────────────────────────────────┤
│      Domain Layer (Business)        │
│    Use Cases + Domain Models        │
├─────────────────────────────────────┤
│        Data Layer (Repository)      │
│    Repository Pattern + DataSources │
├─────────────────────────────────────┤
│       Native Layer (C++)            │
│    whisper.cpp + JNI Bindings       │
└─────────────────────────────────────┘
```

## Module Structure

### Project Modules
```
whisper-android/
├── app/                          # Main application module
├── core/                         # Shared utilities and base classes
├── feature-audio/                # Audio recording and processing
├── feature-transcription/        # Whisper integration and transcription
├── feature-settings/             # Settings and preferences
├── ui-components/                # Reusable UI components
└── native/                       # C++ native code and JNI
```

## Development Environment Setup

### Required Tools and Versions
```yaml
android_studio: "Hedgehog 2023.1.1+"
android_sdk:
  compile_sdk: 34
  min_sdk: 24
  target_sdk: 34
  build_tools: "34.0.0"
ndk_version: "26.1.10909125"
cmake_version: "3.22.1"
jdk_version: 17
kotlin_version: "1.9.22"
agp_version: "8.2.0"
compose_bom: "2024.02.00"
```

### Environment Variables Configuration
```batch
# Add to Windows environment variables
ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\26.1.10909125
PATH=%PATH%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools
```

## Native Layer Implementation

### 1. whisper.cpp Integration

#### CMakeLists.txt Configuration
```cmake
cmake_minimum_required(VERSION 3.22.1)
project("whisper-android")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ARM v8 specific optimizations
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+fp16")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a+fp16")
    add_definitions(-DGGML_USE_FP16_VA=1)
    add_definitions(-DARM_NEON=1)
endif()

# Add whisper.cpp source files
add_subdirectory(${CMAKE_SOURCE_DIR}/whisper.cpp)

# Create shared library
add_library(whisper-jni SHARED
    jni/whisper_jni.cpp
    jni/audio_processor.cpp
)

# Link libraries
target_link_libraries(whisper-jni
    whisper
    log
    android
)
```

### 2. JNI Bindings Implementation

#### WhisperJNI.cpp Structure
```cpp
#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <string>
#include <vector>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_app_whisper_native_WhisperJNI_initContext(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint n_threads) {
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // CPU only for compatibility
    
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    
    env->ReleaseStringUTFChars(model_path, path);
    
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_app_whisper_native_WhisperJNI_transcribeAudio(
    JNIEnv* env,
    jobject /* this */,
    jlong context_ptr,
    jfloatArray audio_data,
    jint sample_rate,
    jstring language,
    jboolean translate) {
    
    struct whisper_context* ctx = reinterpret_cast<whisper_context*>(context_ptr);
    
    jsize audio_length = env->GetArrayLength(audio_data);
    jfloat* audio = env->GetFloatArrayElements(audio_data, nullptr);
    
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    
    // Configure parameters for ARM optimization
    wparams.n_threads = 4;  // Optimal for most ARM v8 devices
    wparams.translate = translate;
    
    const char* lang = env->GetStringUTFChars(language, nullptr);
    wparams.language = lang;
    
    // Process audio
    int result = whisper_full(ctx, wparams, audio, audio_length);
    
    std::string transcription;
    if (result == 0) {
        int n_segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < n_segments; ++i) {
            transcription += whisper_full_get_segment_text(ctx, i);
            transcription += " ";
        }
    }
    
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    
    return env->NewStringUTF(transcription.c_str());
}

JNIEXPORT void JNICALL
Java_com_app_whisper_native_WhisperJNI_releaseContext(
    JNIEnv* env,
    jobject /* this */,
    jlong context_ptr) {
    
    struct whisper_context* ctx = reinterpret_cast<whisper_context*>(context_ptr);
    whisper_free(ctx);
}

} // extern "C"
```

## Kotlin Implementation

### 1. Project-level build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

### 2. App-level build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.app.whisper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.app.whisper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters.add("arm64-v8a")  // ARM v8 only
        }
        
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-O3", "-ffast-math")
                arguments(
                    "-DANDROID_ARM_NEON=ON",
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

### 3. Native Interface (Kotlin)
```kotlin
package com.app.whisper.native

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperNative @Inject constructor() {
    
    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }
    }
    
    private var contextPtr: Long = 0L
    
    external fun initContext(modelPath: String, nThreads: Int): Long
    external fun transcribeAudio(
        contextPtr: Long,
        audioData: FloatArray,
        sampleRate: Int,
        language: String,
        translate: Boolean
    ): String
    external fun releaseContext(contextPtr: Long)
    
    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            contextPtr = initContext(modelPath, 4)
            if (contextPtr != 0L) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to initialize Whisper context"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun transcribe(
        audioData: FloatArray,
        language: String = "auto",
        translate: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (contextPtr == 0L) {
                return@withContext Result.failure(Exception("Whisper context not initialized"))
            }
            
            val result = transcribeAudio(
                contextPtr,
                audioData,
                16000,
                language,
                translate
            )
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun release() {
        if (contextPtr != 0L) {
            releaseContext(contextPtr)
            contextPtr = 0L
        }
    }
}
```

### 4. Audio Recording Implementation
```kotlin
package com.app.whisper.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

class AudioRecorder @Inject constructor() {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    suspend fun startRecording(): Flow<FloatArray> = flow {
        withContext(Dispatchers.IO) {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            audioRecord?.startRecording()
            isRecording = true
            
            val buffer = ShortArray(bufferSize)
            val audioData = mutableListOf<Float>()
            
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (readSize > 0) {
                    // Convert PCM16 to Float
                    for (i in 0 until readSize) {
                        audioData.add(buffer[i] / 32768.0f)
                    }
                    
                    // Emit chunks for real-time processing
                    if (audioData.size >= SAMPLE_RATE) {
                        emit(audioData.toFloatArray())
                        audioData.clear()
                    }
                }
            }
            
            // Emit remaining data
            if (audioData.isNotEmpty()) {
                emit(audioData.toFloatArray())
            }
        }
    }
    
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    fun getWaveformData(audioData: FloatArray, points: Int = 100): List<Float> {
        val waveform = mutableListOf<Float>()
        val chunkSize = audioData.size / points
        
        for (i in 0 until points) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, audioData.size)
            
            var maxAmplitude = 0f
            for (j in start until end) {
                maxAmplitude = maxOf(maxAmplitude, abs(audioData[j]))
            }
            
            waveform.add(maxAmplitude)
        }
        
        return waveform
    }
}
```

### 5. Repository Pattern Implementation
```kotlin
package com.app.whisper.data.repository

import com.app.whisper.audio.AudioRecorder
import com.app.whisper.data.model.TranscriptionResult
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.native.WhisperNative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val whisperNative: WhisperNative,
    private val audioRecorder: AudioRecorder,
    private val modelManager: ModelManager
) {
    
    suspend fun initializeModel(model: WhisperModel): Result<Unit> {
        val modelPath = modelManager.getModelPath(model)
        return whisperNative.initialize(modelPath)
    }
    
    suspend fun transcribeAudio(
        audioData: FloatArray,
        language: String = "auto",
        translate: Boolean = false
    ): Result<TranscriptionResult> {
        return whisperNative.transcribe(audioData, language, translate).map { text ->
            TranscriptionResult(
                text = text,
                language = language,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    fun startRecording(): Flow<FloatArray> {
        return audioRecorder.startRecording()
    }
    
    fun stopRecording() {
        audioRecorder.stopRecording()
    }
    
    fun transcribeFile(filePath: String): Flow<TranscriptionResult> = flow {
        // Load audio file and process
        val audioData = loadAudioFile(filePath)
        val result = transcribeAudio(audioData)
        result.onSuccess { emit(it) }
    }
    
    private suspend fun loadAudioFile(filePath: String): FloatArray {
        // Implementation for loading and converting audio file to FloatArray
        // Support various formats using MediaExtractor/MediaCodec
        return floatArrayOf()
    }
}
```

### 6. ViewModel Implementation
```kotlin
package com.app.whisper.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.whisper.data.model.TranscriptionResult
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.data.repository.TranscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    
    private var recordingJob: kotlinx.coroutines.Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()
    
    init {
        loadSelectedModel()
    }
    
    private fun loadSelectedModel() {
        viewModelScope.launch {
            val selectedModel = preferencesManager.getSelectedModel()
            _uiState.update { it.copy(selectedModel = selectedModel) }
            
            repository.initializeModel(selectedModel)
                .onSuccess {
                    _uiState.update { it.copy(isModelLoaded = true) }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isModelLoaded = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    fun startRecording() {
        if (_uiState.value.isRecording) return
        
        audioBuffer.clear()
        _uiState.update { it.copy(isRecording = true, isProcessing = false) }
        
        recordingJob = viewModelScope.launch {
            repository.startRecording()
                .collect { audioChunk ->
                    audioBuffer.add(audioChunk)
                    
                    // Update UI with waveform data
                    _uiState.update { currentState ->
                        currentState.copy(
                            recordingDuration = currentState.recordingDuration + 1,
                            waveformData = calculateWaveform(audioChunk)
                        )
                    }
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
    
    fun selectModel(model: WhisperModel) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedModel = model, isModelLoaded = false) }
            preferencesManager.setSelectedModel(model)
            loadSelectedModel()
        }
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
        // Simplified waveform calculation
        return audioData.toList().chunked(audioData.size / 50).map { chunk ->
            chunk.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        }
    }
}

data class TranscriptionUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isModelLoaded: Boolean = false,
    val selectedModel: WhisperModel = WhisperModel.TINY,
    val selectedLanguage: String = "auto",
    val translateEnabled: Boolean = false,
    val isDarkTheme: Boolean = false,
    val recordingDuration: Int = 0,
    val waveformData: List<Float> = emptyList(),
    val lastTranscription: String = "",
    val error: String? = null
)
```

### 7. UI Implementation (Jetpack Compose)
```kotlin
package com.app.whisper.presentation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.presentation.viewmodel.TranscriptionViewModel
import com.google.accompanist.permissions.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TranscriptionScreen(
    viewModel: TranscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val transcriptionResult by viewModel.transcriptionResult.collectAsState()
    
    val audioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )
    
    LaunchedEffect(Unit) {
        if (!audioPermissionState.status.isGranted) {
            audioPermissionState.launchPermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whisper Transcription") },
                actions = {
                    // Model selector
                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Model Settings")
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
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
                    
                    // Theme toggle
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            if (uiState.isDarkTheme) Icons.Default.LightMode 
                            else Icons.Default.DarkMode,
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
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Model: ${uiState.selectedModel.displayName}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        
                        if (uiState.isModelLoaded) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text("Ready")
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Language selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Language: ${uiState.selectedLanguage}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Translate",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Switch(
                                checked = uiState.translateEnabled,
                                onCheckedChange = { /* viewModel.toggleTranslate() */ },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Waveform visualization
            if (uiState.isRecording || uiState.waveformData.isNotEmpty()) {
                WaveformVisualizer(
                    waveformData = uiState.waveformData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }
            
            // Recording duration
            if (uiState.isRecording) {
                Text(
                    text = formatDuration(uiState.recordingDuration),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Transcription result
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transcription",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Row {
                            IconButton(
                                onClick = { /* Copy to clipboard */ },
                                enabled = uiState.lastTranscription.isNotEmpty()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            
                            IconButton(
                                onClick = { /* Share */ },
                                enabled = uiState.lastTranscription.isNotEmpty()
                            ) {
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
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
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
                                Text(
                                    text = uiState.lastTranscription,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // File picker button
                OutlinedButton(
                    onClick = { /* Open file picker */ },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRecording && !uiState.isProcessing
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose File")
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Record button
                FilledTonalButton(
                    onClick = {
                        if (uiState.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isModelLoaded && !uiState.isProcessing,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (uiState.isRecording) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isRecording) "Stop" else "Record")
                }
            }
            
            // Error handling
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { /* Dismiss */ }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
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
            // Draw placeholder line
            drawLine(
                color = Color.Gray,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2f
            )
        } else {
            val barWidth = width / waveformData.size
            
            waveformData.forEachIndexed { index, amplitude ->
                val x = index * barWidth + barWidth / 2
                val barHeight = amplitude * height * 0.8f
                
                drawLine(
                    color = Color.Blue.copy(
                        alpha = if (index.toFloat() / waveformData.size < animationProgress) 
                            1f else 0.3f
                    ),
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
```

### 8. Model Management
```kotlin
package com.app.whisper.data.model

enum class WhisperModel(
    val fileName: String,
    val displayName: String,
    val sizeInMB: Int,
    val downloadUrl: String
) {
    TINY(
        fileName = "ggml-tiny.bin",
        displayName = "Tiny (39 MB)",
        sizeInMB = 39,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    ),
    BASE(
        fileName = "ggml-base.bin",
        displayName = "Base (74 MB)",
        sizeInMB = 74,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    ),
    SMALL(
        fileName = "ggml-small.bin",
        displayName = "Small (244 MB)",
        sizeInMB = 244,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
    ),
    MEDIUM(
        fileName = "ggml-medium.bin",
        displayName = "Medium (769 MB)",
        sizeInMB = 769,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin"
    ),
    LARGE(
        fileName = "ggml-large-v3.bin",
        displayName = "Large v3 (1.5 GB)",
        sizeInMB = 1550,
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3.bin"
    )
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    private val modelsDir = File(context.filesDir, "models")
    
    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }
    
    fun getModelPath(model: WhisperModel): String {
        return File(modelsDir, model.fileName).absolutePath
    }
    
    fun isModelDownloaded(model: WhisperModel): Boolean {
        return File(modelsDir, model.fileName).exists()
    }
    
    suspend fun downloadModel(
        model: WhisperModel,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            }
            
            val body = response.body ?: return@withContext Result.failure(Exception("Empty response"))
            val contentLength = body.contentLength()
            
            val file = File(modelsDir, model.fileName)
            val sink = file.sink().buffer()
            
            var totalBytesRead = 0L
            val buffer = Buffer()
            val bufferSize = 8 * 1024L
            
            while (true) {
                val bytesRead = body.source().read(buffer, bufferSize)
                if (bytesRead == -1L) break
                
                sink.write(buffer, bytesRead)
                totalBytesRead += bytesRead
                
                val progress = totalBytesRead.toFloat() / contentLength
                onProgress(progress)
            }
            
            sink.close()
            body.close()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun deleteModel(model: WhisperModel): Boolean {
        val file = File(modelsDir, model.fileName)
        return file.delete()
    }
}
```

## Testing Strategy

### 1. Unit Tests
```kotlin
@Test
fun `test audio conversion from PCM16 to Float`() {
    val pcm16Data = shortArrayOf(32767, -32768, 0, 16384)
    val expectedFloat = floatArrayOf(1.0f, -1.0f, 0.0f, 0.5f)
    
    val result = AudioConverter.pcm16ToFloat(pcm16Data)
    
    assertArrayEquals(expectedFloat, result, 0.001f)
}

@Test
fun `test model download progress reporting`() = runTest {
    val progressValues = mutableListOf<Float>()
    
    modelManager.downloadModel(WhisperModel.TINY) { progress ->
        progressValues.add(progress)
    }
    
    assertTrue(progressValues.isNotEmpty())
    assertTrue(progressValues.last() >= 0.99f)
}
```

### 2. Instrumentation Tests
```kotlin
@Test
fun `test recording permission request flow`() {
    val scenario = launchActivity<MainActivity>()
    
    onView(withId(R.id.record_button)).perform(click())
    
    // Verify permission dialog appears
    onView(withText("Allow recording")).check(matches(isDisplayed()))
}

@Test
fun `test transcription UI flow`() {
    // Mock successful transcription
    every { repository.transcribeAudio(any()) } returns 
        Result.success(TranscriptionResult("Test transcription"))
    
    composeTestRule.setContent {
        TranscriptionScreen()
    }
    
    composeTestRule.onNodeWithText("Record").performClick()
    composeTestRule.onNodeWithText("Stop").performClick()
    
    composeTestRule.waitUntil {
        composeTestRule.onNodeWithText("Test transcription").isDisplayed()
    }
}
```

## Performance Optimization

### 1. ProGuard Rules
```proguard
# Whisper native library
-keep class com.app.whisper.native.** { *; }
-keepclassmembers class com.app.whisper.native.WhisperNative {
    native <methods>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }
```

### 2. Baseline Profiles
```kotlin
@ExperimentalBaselineProfilesApi
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collectBaselineProfile(
        packageName = "com.app.whisper"
    ) {
        startActivityAndWait()
        
        // Critical user journey
        device.findObject(By.res("record_button")).click()
        device.wait(Until.hasObject(By.res("stop_button")), 5000)
        device.findObject(By.res("stop_button")).click()
    }
}
```

## Deployment Configuration

### 1. GitHub Actions CI/CD
```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Setup Android SDK
      uses: android-actions/setup-android@v2
    
    - name: Download whisper.cpp
      run: |
        git submodule update --init --recursive
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Run tests
      run: ./gradlew test
    
    - name: Build Release APK
      run: ./gradlew assembleRelease
    
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-release
        path: app/build/outputs/apk/release/app-release.apk
```

### 2. Local Build Instructions (Windows)
```batch
# Clone repository
git clone https://github.com/yourusername/whisper-android.git
cd whisper-android

# Initialize submodules
git submodule update --init --recursive

# Build debug APK
gradlew.bat assembleDebug

# Build release APK (requires signing config)
gradlew.bat assembleRelease

# Install on connected device
gradlew.bat installDebug

# Run tests
gradlew.bat test

# Run instrumentation tests
gradlew.bat connectedAndroidTest
```

## Future Enhancements

### Phase 1 (v1.1)
- Real-time streaming transcription
- Speaker diarization support
- Export formats (SRT, VTT, TXT, PDF)
- Cloud backup integration

### Phase 2 (v1.2)
- Multi-language UI support
- Voice commands for hands-free operation
- Integration with note-taking apps
- Offline language pack downloads

### Phase 3 (v2.0)
- Custom model training support
- API for third-party integrations
- Widget support for quick access
- Wear OS companion app

## Troubleshooting Guide

### Common Issues and Solutions

1. **Model loading fails**
   - Verify model file integrity
   - Check available storage space
   - Ensure ARM v8 compatibility

2. **Audio recording issues**
   - Check microphone permissions
   - Verify audio format compatibility
   - Test with different sample rates

3. **Performance problems**
   - Reduce model size
   - Enable hardware acceleration
   - Optimize thread count for device

4. **Build errors**
   - Update NDK version
   - Clean and rebuild project
   - Check CMake configuration

## Documentation References

- whisper.cpp: https://github.com/ggml-org/whisper.cpp
- Android NDK: https://developer.android.com/ndk
- Jetpack Compose: https://developer.android.com/jetpack/compose
- Material Design 3: https://m3.material.io
- Kotlin Coroutines: https://kotlinlang.org/docs/coroutines-overview.html

## License and Credits

This implementation plan is designed for educational and development purposes. Ensure compliance with:
- whisper.cpp MIT License
- OpenAI Whisper Model License
- Android SDK License Agreement
- Third-party library licenses

---

**Implementation Notes for AI Agent:**

1. Start with the native layer setup and ensure whisper.cpp compiles correctly for ARM v8
2. Implement the JNI bindings and test with a simple model
3. Build the Kotlin infrastructure layer by layer
4. Implement UI components with proper state management
5. Add features incrementally with thorough testing
6. Optimize performance based on profiling results
7. Prepare for production deployment with proper signing and optimization

This plan provides a complete roadmap for implementing a production-ready Whisper Android application optimized for ARM v8 processors.