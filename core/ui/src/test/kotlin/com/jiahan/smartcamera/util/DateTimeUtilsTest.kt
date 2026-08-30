package com.jiahan.smartcamera.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant

/**
 * Tests for [toFormattedDateTime].
 *
 * Every case here passes [ZoneId] and [Locale] explicitly rather than leaning on the unit-test
 * JVM's UTC/en-US pin. That pin is a containment measure for the Roborazzi goldens, which render
 * this through composables that call it with the defaults; it is not a testing strategy for the
 * formatter itself, because it can only ever hold one zone and one locale at a time. Two cases
 * below could not be written against it at all.
 *
 * On JDK 20+ the time is separated from AM/PM by U+202F (narrow no-break space), which CLDR 42
 * substituted for the plain space. [normalizeSpaces] folds that and NBSP back to a plain space so
 * these assertions describe the visible text and survive that kind of CLDR churn.
 */
class DateTimeUtilsTest {

    /** U+202F narrow no-break space (CLDR 42+) and U+00A0 NBSP -> plain space. */
    private fun String.normalizeSpaces() = replace('\u202F', ' ').replace('\u00A0', ' ')

    private val utc = ZoneId.of("UTC")

    @Test
    fun `toFormattedDateTime renders a medium date and short time`() {
        val millis = Instant.parse("2024-06-15T10:30:00Z").toEpochMilliseconds()
        assertEquals(
            "Jun 15, 2024, 10:30 AM",
            millis.toFormattedDateTime(utc, Locale.US).normalizeSpaces()
        )
    }

    @Test
    fun `toFormattedDateTime renders the unix epoch`() {
        assertEquals(
            "Jan 1, 1970, 12:00 AM",
            0L.toFormattedDateTime(utc, Locale.US).normalizeSpaces()
        )
    }

    @Test
    fun `toFormattedDateTime renders a far-future timestamp`() {
        val millis = Instant.parse("2100-01-01T00:00:00Z").toEpochMilliseconds()
        assertEquals(
            "Jan 1, 2100, 12:00 AM",
            millis.toFormattedDateTime(utc, Locale.US).normalizeSpaces()
        )
    }

    @Test
    fun `toFormattedDateTime two different timestamps produce different strings`() {
        val t1 = Instant.parse("2023-03-15T08:00:00Z").toEpochMilliseconds()
        val t2 = Instant.parse("2024-11-20T18:45:00Z").toEpochMilliseconds()
        assertNotEquals(
            t1.toFormattedDateTime(utc, Locale.US),
            t2.toFormattedDateTime(utc, Locale.US)
        )
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

        val us = millis.toFormattedDateTime(utc, Locale.US)
        val uk = millis.toFormattedDateTime(utc, Locale.UK)
        val japan = millis.toFormattedDateTime(utc, Locale.JAPAN)

        assertEquals("Nov 14, 2023, 10:13 PM", us.normalizeSpaces())
        assertEquals("14 Nov 2023, 22:13", uk.normalizeSpaces())
        assertEquals("2023/11/14 22:13", japan.normalizeSpaces())
    }

    /**
     * Untestable before the [ZoneId] parameter existed: the zone came from
     * [ZoneId.systemDefault], which is JVM-wide, and the build pins it to UTC for every test in
     * the module. The same instant crossing a date boundary is exactly the case the goldens'
     * eight-hour CI failure was, so it is worth holding directly.
     */
    @Test
    fun `toFormattedDateTime renders the instant in the zone it is given`() {
        val millis = Instant.parse("2024-06-15T22:30:00Z").toEpochMilliseconds()

        val utcText = millis.toFormattedDateTime(utc, Locale.US)
        val tokyo = millis.toFormattedDateTime(ZoneId.of("Asia/Tokyo"), Locale.US)
        val newYork = millis.toFormattedDateTime(ZoneId.of("America/New_York"), Locale.US)

        assertEquals("Jun 15, 2024, 10:30 PM", utcText.normalizeSpaces())
        assertEquals("Jun 16, 2024, 7:30 AM", tokyo.normalizeSpaces())
        assertEquals("Jun 15, 2024, 6:30 PM", newYork.normalizeSpaces())
    }

    /**
     * The defaults have to stay live reads rather than values captured once, or a locale or
     * time-zone change would not take effect until the process restarted.
     */
    @Test
    fun `toFormattedDateTime defaults to the platform zone and locale`() {
        val millis = Instant.parse("2024-06-15T10:30:00Z").toEpochMilliseconds()

        assertEquals(
            millis.toFormattedDateTime(ZoneId.systemDefault(), Locale.getDefault()),
            millis.toFormattedDateTime()
        )
    }
}