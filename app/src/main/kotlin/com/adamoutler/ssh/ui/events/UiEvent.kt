package com.adamoutler.ssh.ui.events

sealed interface UiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEvent
    data class ShowToast(val message: String) : UiEvent
    data class Navigate(val route: String) : UiEvent
    object NavigateUp : UiEvent
}
