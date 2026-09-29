package com.hadii.tvcasing.streaming

import android.os.SystemClock
import android.util.Log
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Measures round-trip latency to connected TV sockets by sending periodic
 * ping frames and recording the time until the matching pong arrives.
 *
 * Suggested quality tiers (kbps) are derived from the smoothed RTT so the
 * phone can adapt the stream it serves.
 */
class LatencyMonitor(
    private val sockets: CopyOnWriteArrayList<NanoWSD.WebSocket>,
    private val scope: CoroutineScope,
) {
    enum class Quality(val label: String, val maxBitrateKbps: Int) {
        HIGH("1080p", 5000),
        MEDIUM("720p", 2500),
        LOW("480p", 1000),
        AUDIO_ONLY("audio", 128),
    }

    private val _quality = MutableStateFlow(Quality.HIGH)
    val quality: StateFlow<Quality> = _quality.asStateFlow()

    private val _rttMs = MutableStateFlow(0L)
    val rttMs: StateFlow<Long> = _rttMs.asStateFlow()

    private var job: Job? = null
    private val pending = HashMap<Long, Long>() // nonce -> sentAt

    fun start(intervalMs: Long = 2000) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                pingAll()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        pending.clear()
    }

    /** Call from the WebSocket onPong handler with the payload we sent. */
    fun onPong(payload: String) {
        val nonce = payload.toLongOrNull() ?: return
        val sentAt = pending.remove(nonce) ?: return
        val rtt = SystemClock.elapsedRealtime() - sentAt
        _rttMs.value = smooth(_rttMs.value, rtt)
        _quality.value = qualityFor(rtt)
    }

    private fun pingAll() {
        val nonce = SystemClock.elapsedRealtime()
        pending[nonce] = nonce
        val frame = JSONObject().apply {
            put("type", "ping")
            put("n", nonce)
        }.toString()
        val dead = mutableListOf<NanoWSD.WebSocket>()
        for (ws in sockets) {
            try {
                if (ws.isOpen) ws.send(frame) else dead.add(ws)
            } catch (_: Exception) {
                dead.add(ws)
            }
        }
        sockets.removeAll(dead.toSet())
        // Drop stale pending entries older than 10s to avoid unbounded growth.
        val cutoff = SystemClock.elapsedRealtime() - 10_000
        pending.entries.removeIf { it.value < cutoff }
    }

    private fun qualityFor(rtt: Long): Quality = when {
        rtt < 50 -> Quality.HIGH
        rtt < 150 -> Quality.MEDIUM
        rtt < 400 -> Quality.LOW
        else -> Quality.AUDIO_ONLY
    }

    private fun smooth(prev: Long, sample: Long): Long =
        if (prev == 0L) sample else (prev * 7 + sample) / 8

    companion object {
        private const val TAG = "LatencyMonitor"
    }
}
