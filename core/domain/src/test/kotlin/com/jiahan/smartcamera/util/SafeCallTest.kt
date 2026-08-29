package com.jiahan.smartcamera.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `safeCall catches IOException and returns Result failure`() = runTest {
        val exception = java.io.IOException("network error")
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
    fun `safeCall does not wrap CancellationException - it rethrows`() {
        // runBlocking is intentional here: runTest treats an uncaught CancellationException as
        // test cancellation and would not reach the assertion. runBlocking lets us catch it
        // explicitly and assert on the propagation behaviour.
        var resultLineReached = false
        try {
            runBlocking {
                safeCall<Unit> { throw CancellationException("cancelled") }
                resultLineReached = true // must NOT be reached
            }
        } catch (_: CancellationException) {
            // expected — safeCall rethrow it
        }
        assertFalse(
            "safeCall must rethrow CancellationException rather than wrapping it in Result",
            resultLineReached
        )
    }

    @Test
    fun `safeCall returns correct value from lambda`() = runTest {
        val list = listOf(1, 2, 3)
        val result = safeCall { list.map { it * 2 } }
        assertEquals(listOf(2, 4, 6), result.getOrThrow())
    }
}