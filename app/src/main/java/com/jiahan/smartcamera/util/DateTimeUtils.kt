package com.jiahan.smartcamera.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Modern approach using java.time (thread-safe, API 26+)
fun Long.toFormattedDateTime(): String {
    val instant = Instant.ofEpochMilli(this)
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}