package com.app.whisper.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecodeUtils {
    data class DecodedAudio(val data: FloatArray, val sampleRate: Int)

    fun decodeToMono16k(context: Context, uri: Uri): FloatArray {
        val decoded = decodePcm(context, uri) ?: return floatArrayOf()
        val mono = if (decoded.channels > 1) downmixToMono(decoded.samples, decoded.channels) else decoded.samples
        return if (decoded.sampleRate != 16000) resampleLinear(mono, decoded.sampleRate, 16000) else mono
    }

    private data class RawPcm(val samples: FloatArray, val sampleRate: Int, val channels: Int)

    private fun decodePcm(context: Context, uri: Uri): RawPcm? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                mime.startsWith("audio/")
            } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val outSamples = ArrayList<Short>()
            var outputFormat = codec.outputFormat
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer: ByteBuffer? = codec.getInputBuffer(inIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val info = MediaCodec.BufferInfo()
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    outIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outIndex) ?: return null
                        if (info.size > 0) {
                            val chunk = ByteArray(info.size)
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            outputBuffer.get(chunk)
                            // Assuming 16-bit PCM little endian
                            val shorts = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val arr = ShortArray(shorts.remaining())
                            shorts.get(arr)
                            outSamples.ensureCapacity(outSamples.size + arr.size)
                            for (s in arr) outSamples.add(s)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                }
            }

            val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm = ShortArray(outSamples.size)
            for (i in outSamples.indices) pcm[i] = outSamples[i]
            val floats = FloatArray(pcm.size) { i -> pcm[i] / 32768.0f }
            codec.stop(); codec.release(); extractor.release()
            return RawPcm(floats, sampleRate, channels)
        } catch (_: Exception) {
            try { extractor.release() } catch (_: Exception) {}
            return null
        }
    }

    private fun downmixToMono(samples: FloatArray, channels: Int): FloatArray {
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        var idx = 0
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += samples[idx + c]
            mono[i] = sum / channels
            idx += channels
        }
        return mono
    }

    private fun resampleLinear(src: FloatArray, inRate: Int, outRate: Int): FloatArray {
        if (inRate == outRate) return src
        val ratio = outRate.toDouble() / inRate
        val outLen = (src.size * ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val x = i / ratio
            val x0 = x.toInt().coerceIn(0, src.size - 1)
            val x1 = (x0 + 1).coerceAtMost(src.size - 1)
            val t = (x - x0)
            out[i] = (1 - t).toFloat() * src[x0] + t.toFloat() * src[x1]
        }
        return out
    }
}

