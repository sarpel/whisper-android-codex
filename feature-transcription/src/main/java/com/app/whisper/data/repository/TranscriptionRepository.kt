package com.app.whisper.data.repository

import android.content.Context
import android.net.Uri
import com.app.whisper.audio.AudioRecorder
import com.app.whisper.data.model.TranscriptionResult
import com.app.whisper.data.model.WhisperModel
import com.app.whisper.data.model.ModelManager
import com.app.whisper.nativelib.WhisperNative
import dagger.hilt.android.qualifiers.ApplicationContext
import com.app.whisper.core.AudioDecodeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val whisperNative: WhisperNative,
    private val audioRecorder: AudioRecorder,
    private val modelManager: ModelManager,
    @ApplicationContext private val context: Context
) {
    fun isModelDownloaded(model: WhisperModel): Boolean = modelManager.isModelDownloaded(model)

    suspend fun downloadModel(
        model: WhisperModel,
        onProgress: (Float) -> Unit
    ): Result<Unit> = modelManager.downloadModel(model, onProgress)

    suspend fun initializeModel(model: WhisperModel, threads: Int): Result<Unit> {
        val modelPath = modelManager.getModelPath(model)
        return whisperNative.initialize(modelPath, threads)
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

    suspend fun startRecording(): Flow<FloatArray> = audioRecorder.startRecording()

    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    fun transcribeFile(filePath: String): Flow<TranscriptionResult> = flow {
        val audioData = loadAudioFile(filePath)
        val result = transcribeAudio(audioData)
        result.onSuccess { emit(it) }
    }

    fun transcribeContentUri(uri: Uri): Flow<TranscriptionResult> = flow {
        val audioData = loadAudioFromUri(uri)
        val result = transcribeAudio(audioData)
        result.onSuccess { emit(it) }
    }

    fun deleteModel(model: WhisperModel): Boolean = modelManager.deleteModel(model)

    private suspend fun loadAudioFile(filePath: String): FloatArray {
        // Minimal WAV PCM16 mono/16kHz loader. For other formats, a proper decoder is needed.
        return try {
            val file = java.io.File(filePath)
            val bytes = file.readBytes()
            if (bytes.size < 44) return floatArrayOf()

            // Parse WAV header
            fun leInt(offset: Int) =
                (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            fun leShort(offset: Int) =
                (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8)

            val channels = leShort(22)
            val sampleRate = leInt(24)
            val bitsPerSample = leShort(34)
            if (channels != 1 || sampleRate != 16000 || bitsPerSample != 16) {
                // Not supported in this minimal loader
                return floatArrayOf()
            }

            // Find 'data' chunk
            var pos = 12
            var dataOffset = -1
            var dataSize = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4)
                val size = leInt(pos + 4)
                if (id == "data") {
                    dataOffset = pos + 8
                    dataSize = size
                    break
                }
                pos += 8 + size
            }
            if (dataOffset == -1 || dataOffset + dataSize > bytes.size) return floatArrayOf()

            val samples = dataSize / 2
            val out = FloatArray(samples)
            var i = 0
            var j = dataOffset
            while (i < samples && j + 1 < bytes.size) {
                val lo = bytes[j].toInt() and 0xFF
                val hi = bytes[j + 1].toInt()
                val s = (hi shl 8) or lo
                val value = (s.toShort().toInt()) / 32768.0f
                out[i] = value
                i++
                j += 2
            }
            out
        } catch (_: Exception) {
            floatArrayOf()
        }
    }

    private fun loadAudioFromUri(uri: Uri): FloatArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                // Reuse WAV loader by writing to temp file, or parse header directly
                if (bytes.size < 44) return floatArrayOf()
                // Parse minimal WAV PCM16 mono/16kHz
                fun leInt(offset: Int) =
                    (bytes[offset].toInt() and 0xFF) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                fun leShort(offset: Int) =
                    (bytes[offset].toInt() and 0xFF) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                val channels = leShort(22)
                val sampleRate = leInt(24)
                val bitsPerSample = leShort(34)
                if (channels != 1 || sampleRate != 16000 || bitsPerSample != 16) return floatArrayOf()
                var pos = 12
                var dataOffset = -1
                var dataSize = 0
                while (pos + 8 <= bytes.size) {
                    val id = String(bytes, pos, 4)
                    val size = leInt(pos + 4)
                    if (id == "data") { dataOffset = pos + 8; dataSize = size; break }
                    pos += 8 + size
                }
                if (dataOffset == -1 || dataOffset + dataSize > bytes.size) return floatArrayOf()
                val samples = dataSize / 2
                val out = FloatArray(samples)
                var i = 0
                var j = dataOffset
                while (i < samples && j + 1 < bytes.size) {
                    val lo = bytes[j].toInt() and 0xFF
                    val hi = bytes[j + 1].toInt()
                    val s = (hi shl 8) or lo
                    out[i] = (s.toShort().toInt()) / 32768.0f
                    i++; j += 2
                }
                out
            } ?: AudioDecodeUtils.decodeToMono16k(context, uri)
        } catch (_: Exception) {
            // Fallback to framework decoder
            AudioDecodeUtils.decodeToMono16k(context, uri)
        }
    }
}
