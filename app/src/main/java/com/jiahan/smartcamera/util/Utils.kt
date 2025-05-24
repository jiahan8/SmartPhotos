package com.jiahan.smartcamera.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Utils {
    // Modern approach using java.time (thread-safe, API 26+)
    fun formatDateTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}