package com.hadii.tvcasing.streaming

sealed class StreamingState {
    data object Idle : StreamingState()
    data object Connecting : StreamingState()
    data object Connected : StreamingState()
    data object Playing : StreamingState()
    data class Error(val message: String) : StreamingState()
}
