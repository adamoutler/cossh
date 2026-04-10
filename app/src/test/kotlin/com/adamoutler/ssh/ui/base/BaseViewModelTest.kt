package com.adamoutler.ssh.ui.base

import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BaseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class TestViewModel : BaseViewModel() {
        fun doSomethingThatThrows() {
            launchWithHandler {
                throw RuntimeException("Test Coroutine Exception")
            }
        }
    }

    @Test
    fun testCoroutineExceptionIsCaughtAndEmitsUiEvent() = runTest {
        val viewModel = TestViewModel()
        
        val events = mutableListOf<UiEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            UiEventBus.events.collect { events.add(it) }
        }

        viewModel.doSomethingThatThrows()

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Expected to receive at least one UiEvent", events.isNotEmpty())
        val event = events.first()
        assertTrue(event is UiEvent.ShowSnackbar)
        assertEquals("Test Coroutine Exception", (event as UiEvent.ShowSnackbar).message)
        
        job.cancel()
    }
}
