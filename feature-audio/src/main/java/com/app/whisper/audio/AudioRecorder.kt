package com.app.whisper.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import javax.inject.Inject

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
                    for (i in 0 until readSize) {
                        audioData.add(buffer[i] / 32768.0f)
                    }
                    if (audioData.size >= SAMPLE_RATE) {
                        emit(audioData.toFloatArray())
                        audioData.clear()
                    }
                }
            }

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