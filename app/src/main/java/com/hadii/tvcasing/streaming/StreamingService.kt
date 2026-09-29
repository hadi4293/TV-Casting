package com.hadii.tvcasing.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hadii.tvcasing.MainActivity
import com.hadii.tvcasing.R
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Foreground service that:
 *  - hosts a self-signed HTTPS + WebSocket server on the local Wi-Fi IP
 *  - serves the receiver.html page to the TV browser
 *  - pushes play/pause/seek/volume commands over a real WebSocket (NanoWSD)
 *  - keeps running while the phone is locked (PARTIAL_WAKE_LOCK)
 */
class StreamingService : Service() {

    companion object {
        const val ACTION_START = "com.hadii.tvcasing.START"
        const val ACTION_STOP = "com.hadii.tvcasing.STOP"
        const val EXTRA_URL = "url"
        const val CHANNEL_ID = "tv_casting_channel"
        const val NOTIF_ID = 42
        private const val PORT = 8443
        private const val TAG = "StreamingService"
        private const val KEYSTORE_PASSWORD = "changeit"
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    private var server: HttpsStreamServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentUrl: String? = null
    @Volatile private var paused = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Connected TV browser sockets; CopyOnWriteArrayList so broadcast is thread-safe.
    private val sockets = CopyOnWriteArrayList<NanoWSD.WebSocket>()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (url.isNotBlank()) startStreaming(url)
            }
            ACTION_STOP -> stopStreaming()
        }
        return START_STICKY
    }

    private fun startStreaming(url: String) {
        currentUrl = url
        paused = false
        startForegroundWithNotification()
        acquireWakeLock()

        try {
            server?.stop()
            val ip = localIp()
            server = HttpsStreamServer(PORT, assetsReceiverHtml(), ip).also { it.start() }
            // Give the server a moment, then push the play command (off the main thread).
            mainHandler.postDelayed({ broadcast(playCommand(url)) }, 400)
        } catch (e: Exception) {
            Log.e(TAG, "startStreaming failed", e)
        }
    }

    private fun stopStreaming() {
        mainHandler.removeCallbacksAndMessages(null)
        try { broadcast(stopCommand()) } catch (_: Exception) {}
        server?.stop()
        server = null
        currentUrl = null
        paused = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun togglePause() {
        paused = !paused
        broadcast(if (paused) pauseCommand() else resumeCommand())
    }

    fun seek(seconds: Double) {
        broadcast(seekCommand(seconds))
    }

    fun setVolume(volume: Double) {
        broadcast(volumeCommand(volume))
    }

    private fun startForegroundWithNotification() {
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Casting")
            .setContentText("Yerel HTTPS yayını aktif")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "TV Casting", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvcasing:stream").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (e: Exception) {
                Log.e(TAG, "acquireWakeLock failed", e)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun assetsReceiverHtml(): String =
        try { assets.open("receiver.html").bufferedReader().use { it.readText() } }
        catch (e: Exception) { "<html><body>receiver missing</body></html>" }

    private fun localIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) { "127.0.0.1" }
    }

    fun receiverUrl(): String = "https://${localIp()}:$PORT/"

    // ---- WebSocket command channel ----
    private fun broadcast(json: String) {
        val dead = mutableListOf<NanoWSD.WebSocket>()
        for (ws in sockets) {
            try {
                if (ws.isOpen) ws.send(json) else dead.add(ws)
            } catch (_: Exception) {
                dead.add(ws)
            }
        }
        sockets.removeAll(dead.toSet())
    }

    private fun playCommand(url: String) = JSONObject().apply {
        put("type", "play"); put("url", url)
    }.toString()
    private fun pauseCommand() = JSONObject().apply { put("type", "pause") }.toString()
    private fun resumeCommand() = JSONObject().apply { put("type", "resume") }.toString()
    private fun seekCommand(t: Double) = JSONObject().apply { put("type", "seek"); put("t", t) }.toString()
    private fun volumeCommand(vol: Double) = JSONObject().apply { put("type", "volume"); put("vol", vol) }.toString()
    private fun stopCommand() = JSONObject().apply { put("type", "stop") }.toString()

    // ---- HTTPS + WebSocket server ----
    private inner class HttpsStreamServer(
        port: Int,
        private val receiverHtml: String,
        private val ip: String,
    ) : NanoWSD(port) {

        init {
            makeSecure(sslFactory(), null)
        }

        private fun sslFactory(): javax.net.ssl.SSLServerSocketFactory {
            val (keyPair, cert) = CertUtils.generate(ip)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)
            ks.setKeyEntry("key", keyPair.private, KEYSTORE_PASSWORD.toCharArray(), arrayOf(cert))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, KEYSTORE_PASSWORD.toCharArray())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, SecureRandom())
            return ctx.serverSocketFactory
        }

        override fun openWebSocket(handshake: IHTTPSession): WebSocket = TvSocket(handshake)

        override fun serveHttp(session: IHTTPSession): Response {
            return when (session.uri) {
                "/", "/index.html" -> newFixedLengthResponse(Response.Status.OK, "text/html", receiverHtml)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }
        }

        private inner class TvSocket(handshake: IHTTPSession) : WebSocket(handshake) {
            override fun onOpen() { sockets.add(this) }
            override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
                sockets.remove(this)
            }
            override fun onMessage(message: WebSocketFrame) { /* receiver is read-only for now */ }
            override fun onPong(pong: WebSocketFrame) {}
            override fun onException(exception: java.io.IOException) { sockets.remove(this) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        server?.stop()
        releaseWakeLock()
    }
}
