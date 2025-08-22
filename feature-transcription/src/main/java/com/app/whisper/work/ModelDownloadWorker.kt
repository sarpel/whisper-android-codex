package com.app.whisper.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.app.whisper.data.model.ModelManager
import com.app.whisper.data.model.WhisperModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.core.app.NotificationCompat
import com.app.whisper.core.CoreConfig
import android.app.PendingIntent
import android.content.Intent

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val modelManager: ModelManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: return Result.failure()
        val model = runCatching { WhisperModel.valueOf(modelName) }.getOrNull() ?: return Result.failure()

        setForeground(createForegroundInfo(model))

        val result = modelManager.downloadModel(model) { progress ->
            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            updateNotification(model, progress)
        }

        return result.fold(
            onSuccess = {
                // Show completion notification and stop foreground
                showCompletion(model)
                Result.success(workDataOf(KEY_PROGRESS to 1.0f))
            },
            onFailure = {
                showFailed(model)
                Result.retry()
            }
        )
    }

    private fun createForegroundInfo(model: WhisperModel, progress: Float = 0f): ForegroundInfo {
        val cancelIntent = Intent(applicationContext, CancelDownloadReceiver::class.java).apply {
            action = CancelDownloadReceiver.ACTION
            putExtra(CancelDownloadReceiver.EXTRA_MODEL, model.name)
        }
        val cancelPending = PendingIntent.getBroadcast(
            applicationContext,
            model.ordinal,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CoreConfig.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading ${model.displayName}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
            .build()
        return ForegroundInfo(notificationId(model), notification)
    }

    private fun updateNotification(model: WhisperModel, progress: Float) {
        val nm = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        val cancelIntent = Intent(applicationContext, CancelDownloadReceiver::class.java).apply {
            action = CancelDownloadReceiver.ACTION
            putExtra(CancelDownloadReceiver.EXTRA_MODEL, model.name)
        }
        val cancelPending = PendingIntent.getBroadcast(
            applicationContext,
            model.ordinal,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CoreConfig.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading ${model.displayName}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
            .build()
        nm.notify(notificationId(model), notification)
    }

    private fun showCompletion(model: WhisperModel) {
        val nm = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CoreConfig.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloaded ${model.displayName}")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId(model), notification)
    }

    private fun showFailed(model: WhisperModel) {
        val nm = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CoreConfig.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Failed: ${model.displayName}")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId(model), notification)
    }

    companion object {
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_PROGRESS = "progress"

        fun inputData(model: WhisperModel): Data = workDataOf(KEY_MODEL_NAME to model.name)
    }

    private fun notificationId(model: WhisperModel): Int = 1000 + model.ordinal
}
