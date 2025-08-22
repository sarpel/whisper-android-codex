package com.app.whisper.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.app.whisper.data.model.WhisperModel

class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val modelName = intent.getStringExtra(EXTRA_MODEL) ?: return
        val model = runCatching { WhisperModel.valueOf(modelName) }.getOrNull() ?: return
        val unique = "model_download_${model.name}"
        WorkManager.getInstance(context).cancelUniqueWork(unique)
    }

    companion object {
        const val ACTION = "com.app.whisper.CANCEL_DOWNLOAD"
        const val EXTRA_MODEL = "extra_model"
    }
}

