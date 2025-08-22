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

