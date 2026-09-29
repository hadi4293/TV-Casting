package com.hadii.tvcasing.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StreamingViewModel : ViewModel() {
    private val _state = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val state: StateFlow<StreamingState> = _state.asStateFlow()

    fun setState(s: StreamingState) {
        viewModelScope.launch { _state.value = s }
    }

    fun togglePause() {
        // Wired to StreamingService via binder in a full build.
    }
}
