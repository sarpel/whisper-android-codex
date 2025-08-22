package com.app.whisper.data.model

import android.content.Context
import com.app.whisper.data.model.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Request
import okhttp3.OkHttpClient
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

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
        val tmpFile = File(modelsDir, model.fileName + ".part")
        val outFile = File(modelsDir, model.fileName)
        try {
            val request = Request.Builder().url(model.downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            val body = response.body ?: return@withContext Result.failure(Exception("Empty response"))
            val contentLength = body.contentLength().coerceAtLeast(1L)

            // Ensure tmp file clean
            if (tmpFile.exists()) tmpFile.delete()
            val sink = tmpFile.sink().buffer()

            var totalBytesRead = 0L
            val buffer = Buffer()
            val bufferSize = 128 * 1024L

            try {
                while (true) {
                    if (!coroutineContext.isActive) throw CancellationException("Cancelled")
                    val bytesRead = body.source().read(buffer, bufferSize)
                    if (bytesRead == -1L) break
                    sink.write(buffer, bytesRead)
                    totalBytesRead += bytesRead
                    onProgress(totalBytesRead.toFloat() / contentLength)
                }
            } finally {
                sink.close()
                body.close()
            }

            // Atomically move to final path
            if (outFile.exists()) outFile.delete()
            if (!tmpFile.renameTo(outFile)) return@withContext Result.failure(Exception("Failed to finalize download"))

            Result.success(Unit)
        } catch (e: CancellationException) {
            tmpFile.delete()
            Result.failure(e)
        } catch (e: Exception) {
            tmpFile.delete()
            Result.failure(e)
        }
    }

    fun deleteModel(model: WhisperModel): Boolean {
        val file = File(modelsDir, model.fileName)
        return file.delete()
    }
}
