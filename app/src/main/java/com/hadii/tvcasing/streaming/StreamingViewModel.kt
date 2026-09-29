package com.hadii.tvcasing.streaming

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StreamingViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val state: StateFlow<StreamingState> = _state.asStateFlow()

    private var service: StreamingService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? StreamingService.LocalBinder
            service = local?.getService()
            bound = true
            viewModelScope.launch { _state.value = StreamingState.Connected }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            viewModelScope.launch { _state.value = StreamingState.Idle }
        }
    }

    fun bind() {
        if (bound) return
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, StreamingService::class.java)
        ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        val ctx = getApplication<Application>()
        ctx.unbindService(connection)
        bound = false
        service = null
    }

    fun start(url: String) {
        viewModelScope.launch {
            _state.value = StreamingState.Connecting
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, StreamingService::class.java).apply {
                action = StreamingService.ACTION_START
                putExtra(StreamingService.EXTRA_URL, url.trim())
            }
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
            if (!bound) bind()
        }
    }

    fun stop() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            ctx.startService(Intent(ctx, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP
            })
            _state.value = StreamingState.Idle
        }
    }

    fun togglePause() {
        service?.togglePause()
        val current = _state.value
        _state.value = when (current) {
            is StreamingState.Playing -> StreamingState.Connected
            is StreamingState.Connected -> StreamingState.Playing
            else -> current
        }
    }

    fun seek(seconds: Double) {
        service?.seek(seconds)
    }

    fun setVolume(volume: Double) {
        service?.setVolume(volume)
    }

    override fun onCleared() {
        super.onCleared()
        unbind()
    }
}
