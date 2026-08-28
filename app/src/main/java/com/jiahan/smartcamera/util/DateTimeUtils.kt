package com.jiahan.smartcamera.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formats an epoch-millisecond timestamp for display, in the reader's own locale and time zone.
 *
 * Uses the locale's own date/time style rather than a fixed `dd MMM yyyy, HH:mm` pattern, because a
 * fixed pattern localizes only the month *name* — it still forces day-before-month ordering and a
 * 24-hour clock on every locale. `MEDIUM` date + `SHORT` time carries the same information (month
 * abbreviated, full year, no weekday, no seconds) while letting each locale order and punctuate it
 * natively: `Nov 14, 2023, 10:13 PM` for en-US, `2023/11/14 22:13` for ja-JP.
 *
 * The locale and zone are resolved per call, not cached in a top-level formatter, so a locale or
 * time-zone change takes effect without a process restart.
 *
 * This is the app's only remaining `java.time` usage, and it stays deliberately. The localized
 * style comes from platform locale data that `kotlinx.datetime` intentionally doesn't carry: its
 * format DSL wants the pattern and month names spelled out, which is the hardcoded pattern this
 * function exists to avoid. `DateTimeFormatter` also formats a `java.time.TemporalAccessor`, so
 * swapping the local to `kotlin.time.Instant` would only add a conversion back. Under a Kotlin
 * Multiplatform move this file becomes an `expect`/`actual` rather than shared code, and the
 * Android `actual` keeps what is written here.
 */
fun Long.toFormattedDateTime(): String {
    val instant = Instant.ofEpochMilli(this)
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}