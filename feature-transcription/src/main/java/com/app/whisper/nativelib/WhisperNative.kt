package com.app.whisper.nativelib

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

    // Backed by native C++ (see native/jni/whisper_jni.cpp)
    private external fun initContext(modelPath: String, nThreads: Int): Long
    private external fun transcribeAudio(
        contextPtr: Long,
        audioData: FloatArray,
        sampleRate: Int,
        language: String,
        translate: Boolean
    ): String
    private external fun releaseContext(contextPtr: Long)

    suspend fun initialize(modelPath: String, threads: Int = 4): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            contextPtr = initContext(modelPath, threads)
            if (contextPtr != 0L) Result.success(Unit)
            else Result.failure(IllegalStateException("Failed to initialize Whisper context"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transcribe(
        audioData: FloatArray,
        language: String = "auto",
        translate: Boolean = false,
        sampleRate: Int = 16000
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (contextPtr == 0L) return@withContext Result.failure(IllegalStateException("Whisper context not initialized"))
            val result = transcribeAudio(contextPtr, audioData, sampleRate, language, translate)
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

