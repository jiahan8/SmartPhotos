package com.jiahan.smartcamera.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale
import kotlin.time.Instant

/**
 * Tests for [toFormattedDateTime].
 *
 * The formatter resolves [java.util.Locale.getDefault] and [java.time.ZoneId.systemDefault] per
 * call, and `app/build.gradle.kts` pins the unit-test JVM to en-US/UTC, so the rendering is
 * deterministic here and worth asserting literally rather than by shape.
 *
 * On JDK 20+ the time is separated from AM/PM by U+202F (narrow no-break space), which CLDR 42
 * substituted for the plain space. [normalizeSpaces] folds that and NBSP back to a plain space so
 * these assertions describe the visible text and survive that kind of CLDR churn.
 */
class DateTimeUtilsTest {

    /** U+202F narrow no-break space (CLDR 42+) and U+00A0 NBSP -> plain space. */
    private fun String.normalizeSpaces() = replace('\u202F', ' ').replace('\u00A0', ' ')

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `toFormattedDateTime renders a medium date and short time`() {
        val millis = Instant.parse("2024-06-15T10:30:00Z").toEpochMilliseconds()
        assertEquals("Jun 15, 2024, 10:30 AM", millis.toFormattedDateTime().normalizeSpaces())
    }

    @Test
    fun `toFormattedDateTime renders the unix epoch`() {
        assertEquals("Jan 1, 1970, 12:00 AM", 0L.toFormattedDateTime().normalizeSpaces())
    }

    @Test
    fun `toFormattedDateTime renders a far-future timestamp`() {
        val millis = Instant.parse("2100-01-01T00:00:00Z").toEpochMilliseconds()
        assertEquals("Jan 1, 2100, 12:00 AM", millis.toFormattedDateTime().normalizeSpaces())
    }

    @Test
    fun `toFormattedDateTime two different timestamps produce different strings`() {
        val t1 = Instant.parse("2023-03-15T08:00:00Z").toEpochMilliseconds()
        val t2 = Instant.parse("2024-11-20T18:45:00Z").toEpochMilliseconds()
        assertNotEquals(t1.toFormattedDateTime(), t2.toFormattedDateTime())
    }

    /**
     * The regression test for formatting with a localized style instead of a fixed
     * `dd MMM yyyy, HH:mm` pattern: field order, separators and the 12-vs-24-hour clock all have to
     * follow the locale. A fixed pattern renders the en-GB form for every locale, so it would fail
     * the US and Japanese cases here while still passing every other test in this class.
     */
    @Test
    fun `toFormattedDateTime field order and clock convention follow the locale`() {
        val millis = Instant.parse("2023-11-14T22:13:20Z").toEpochMilliseconds()

        val us = withLocale(Locale.US) { millis.toFormattedDateTime() }
        val uk = withLocale(Locale.UK) { millis.toFormattedDateTime() }
        val japan = withLocale(Locale.JAPAN) { millis.toFormattedDateTime() }

        assertEquals("Nov 14, 2023, 10:13 PM", us.normalizeSpaces())
        assertEquals("14 Nov 2023, 22:13", uk.normalizeSpaces())
        assertEquals("2023/11/14 22:13", japan.normalizeSpaces())
    }
}