package com.app.whisper.core

import kotlin.math.ln
import kotlin.math.pow

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.size)
        val value = bytes / 1024.0.pow(exp.toDouble())
        val unit = if (exp == 0) "KB" else units[exp - 1]
        return String.format("%.2f %s", value, unit)
    }
}

