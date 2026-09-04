package com.jiahan.smartcamera.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stands in for the `java.io.IOException` this file used to throw. The point of that case is that
 * `safeCall` catches an [Exception] that is not a [RuntimeException], not anything about IO, and
 * commonTest has no java.io to reach for.
 */
private class TestException(message: String) : Exception(message)

class SafeCallTest {

    @Test
    fun `safeCall wraps normal result in Result success`() = runTest {
        val result = safeCall { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `safeCall wraps string result in Result success`() = runTest {
        val result = safeCall { "hello" }
        assertEquals(Result.success("hello"), result)
    }

    @Test
    fun `safeCall wraps Unit operation in Result success`() = runTest {
        val result = safeCall { }
        assertEquals(Result.success(Unit), result)
    }

    @Test
    fun `safeCall catches RuntimeException and returns Result failure`() = runTest {
        val exception = RuntimeException("boom")
        val result = safeCall<Int> { throw exception }
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `safeCall catches a non-runtime Exception and returns Result failure`() = runTest {
        val exception = TestException("network error")
        val result = safeCall<String> { throw exception }
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `safeCall catches IllegalStateException and returns Result failure`() = runTest {
        val exception = IllegalStateException("bad state")
        val result = safeCall<Unit> { throw exception }
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `safeCall does not wrap CancellationException - it rethrows`() = runTest {
        // Caught in place rather than left to propagate: runTest treats an escaping
        // CancellationException as the test coroutine being cancelled and would not reach the
        // assertion. That is what the runBlocking block here used to buy, and runBlocking is
        // declared for JVM and Native but not in commonMain, so commonTest cannot call it.
        // Catching it inside the block never reaches a coroutine boundary, so nothing is cancelled.
        var resultLineReached = false
        try {
            safeCall<Unit> { throw CancellationException("cancelled") }
            resultLineReached = true // must NOT be reached
        } catch (_: CancellationException) {
            // expected — safeCall rethrow it
        }
        assertFalse(
            resultLineReached,
            "safeCall must rethrow CancellationException rather than wrapping it in Result"
        )
    }

    @Test
    fun `safeCall returns correct value from lambda`() = runTest {
        val list = listOf(1, 2, 3)
        val result = safeCall { list.map { it * 2 } }
        assertEquals(listOf(2, 4, 6), result.getOrThrow())
    }
}