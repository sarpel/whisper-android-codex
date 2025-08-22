package com.app.whisper.core

object AudioConverter {
    /**
     * Convert PCM16 audio samples to FloatArray in range [-1.0, 1.0].
     */
    fun pcm16ToFloat(pcmData: ShortArray): FloatArray {
        return FloatArray(pcmData.size) { i ->
            pcmData[i] / 32768.0f
        }
    }
}