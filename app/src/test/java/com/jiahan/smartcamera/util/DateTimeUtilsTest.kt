package com.jiahan.smartcamera.util

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests for [toFormattedDateTime].
 *
 * The formatter uses [java.time.ZoneId.systemDefault] and [java.util.Locale.getDefault], so we
 * validate the structural pattern rather than a literal string to keep the tests locale/timezone
 * agnostic.
 *
 * Expected pattern: "dd MMM yyyy, HH:mm"  → e.g. "01 Jan 1970, 08:00"
 */
class DateTimeUtilsTest {

    // Matches "dd MMM yyyy, HH:mm" – two digits, space, 3-letter month, space, 4-digit year, comma+space, HH:mm
    private val datePattern = Regex("""^\d{2} \w{3} \d{4}, \d{2}:\d{2}$""")

    @Test
    fun `toFormattedDateTime returns string matching dd MMM yyyy HH mm pattern`() {
        val millis = Instant.parse("2024-06-15T10:30:00Z").toEpochMilli()
        val result = millis.toFormattedDateTime()
        assertTrue(
            "Result '$result' did not match expected pattern",
            result.matches(datePattern)
        )
    }

    @Test
    fun `toFormattedDateTime unix epoch produces correctly formatted string`() {
        val result = 0L.toFormattedDateTime()
        assertTrue(
            "Result '$result' did not match expected pattern",
            result.matches(datePattern)
        )
    }

    @Test
    fun `toFormattedDateTime large future timestamp produces correctly formatted string`() {
        // Year 2100 – 1 Jan 00:00 UTC
        val millis = Instant.parse("2100-01-01T00:00:00Z").toEpochMilli()
        val result = millis.toFormattedDateTime()
        assertTrue(
            "Result '$result' did not match expected pattern",
            result.matches(datePattern)
        )
    }

    @Test
    fun `toFormattedDateTime two different timestamps produce different strings`() {
        val t1 = Instant.parse("2023-03-15T08:00:00Z").toEpochMilli()
        val t2 = Instant.parse("2024-11-20T18:45:00Z").toEpochMilli()
        assertNotEquals(t1.toFormattedDateTime(), t2.toFormattedDateTime())
    }

    @Test
    fun `toFormattedDateTime output has expected minimum length`() {
        // "01 Jan 1970, 00:00" = 18 chars minimum
        val result = 0L.toFormattedDateTime()
        assertTrue("Formatted date '$result' is too short", result.length >= 15)
    }
}