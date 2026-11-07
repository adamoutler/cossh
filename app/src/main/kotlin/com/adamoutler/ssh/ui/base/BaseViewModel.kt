package com.adamoutler.ssh.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseViewModel : ViewModel() {

    protected val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        AppLog.e("BaseViewModel", "Unhandled Coroutine Exception", exception)
        val errorMessage = exception.message ?: "An unknown error occurred"
        UiEventBus.publish(UiEvent.ShowSnackbar(errorMessage))
    }

    protected fun launchWithHandler(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        viewModelScope.launch(context + exceptionHandler) {
            block()
        }
    }
}
