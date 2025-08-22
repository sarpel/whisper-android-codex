package com.app.whisper.data.model

data class TranscriptionResult(
    val text: String,
    val language: String,
    val timestamp: Long
)