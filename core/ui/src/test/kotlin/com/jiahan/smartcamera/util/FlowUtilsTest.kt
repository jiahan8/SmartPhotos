package com.jiahan.smartcamera.util

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowUtilsTest {

    @Test
    fun `pairwise emits consecutive pairs for multi-element flow`() = runTest {
        val result = flowOf(1, 2, 3, 4).pairwise().toList()
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4), result)
    }

    @Test
    fun `pairwise emits single pair for two-element flow`() = runTest {
        val result = flowOf("a", "b").pairwise().toList()
        assertEquals(listOf("a" to "b"), result)
    }

    @Test
    fun `pairwise emits nothing for single-element flow`() = runTest {
        val result = flowOf("only").pairwise().toList()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pairwise emits nothing for empty flow`() = runTest {
        val result = emptyFlow<Int>().pairwise().toList()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pairwise preserves order of all consecutive pairs`() = runTest {
        val items = listOf(10, 20, 30, 40, 50)
        val result = flow { items.forEach { emit(it) } }.pairwise().toList()
        assertEquals(
            listOf(10 to 20, 20 to 30, 30 to 40, 40 to 50),
            result
        )
    }

    @Test
    fun `pairwise output size is upstream size minus one`() = runTest {
        val n = 6
        val source = flow { repeat(n) { emit(it) } }
        val result = source.pairwise().toList()
        assertEquals(n - 1, result.size)
    }
}