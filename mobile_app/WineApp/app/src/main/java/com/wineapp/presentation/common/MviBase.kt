package com.wineapp.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface BaseState {
    data class Error(val message: String) : BaseState
    data class Loading(val message: String = "") : BaseState
    object Idle : BaseState
}

interface BaseIntent

abstract class BaseViewModel<State : BaseState, Intent : BaseIntent> : ViewModel() {

    protected val _state = MutableStateFlow<State>(getInitialState())
    val state: StateFlow<State> = _state

    protected val intentChannel = Channel<Intent>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (intent in intentChannel) {
                reduce(intent)
            }
        }
    }

    protected abstract fun getInitialState(): State
    protected abstract fun reduce(intent: Intent)

    fun sendIntent(intent: Intent) {
        viewModelScope.launch { intentChannel.send(intent) }
    }

    protected fun updateState(newState: State) {
        _state.value = newState
    }
}

interface Reducer<State, Intent> {
    fun reduce(currentState: State, intent: Intent): State
}