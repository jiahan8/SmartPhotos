package com.jiahan.smartcamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces [Dispatchers.Main] with a [TestDispatcher] for the duration of a test.
 *
 * Use `UnconfinedTestDispatcher` (default) for most ViewModel tests — coroutines execute eagerly
 * without needing explicit time advancement. Pass `StandardTestDispatcher` when you need fine-grained
 * virtual-time control (e.g. debounce tests).
 *
 * **This is a copy of the same rule in `:app`, and the duplication is deliberate.** The plan's rule
 * for `:core:` modules is that each needs a forcing function — something that will not compile
 * without it — and `:core:testing` does not have one until a *second* feature module needs these
 * fixtures and their shape is known. One duplicated 18-line rule is what waiting costs; extracting
 * a module to avoid it would be paying earlier for less information. Delete this copy when
 * `:core:testing` lands and take the nine fakes with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}