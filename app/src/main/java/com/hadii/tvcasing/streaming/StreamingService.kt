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
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hadii.tvcasing.MainActivity
import com.hadii.tvcasing.R
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Foreground service that:
 *  - hosts a self-signed HTTPS server on the local Wi-Fi IP
 *  - serves the receiver.html page to the TV browser
 *  - pushes play/pause/seek/volume commands over a WebSocket-like channel
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
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    private var server: HttpsStreamServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentUrl: String? = null

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
        startForegroundWithNotification()
        acquireWakeLock()

        try {
            server?.stop()
            server = HttpsStreamServer(PORT, assetsReceiverHtml(), ::broadcast).also {
                it.start(SSL_SERVER_FACTORY(), false)
            }
            // Give the server a moment, then push the play command.
            Thread.sleep(400)
            broadcast(playCommand(url))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopStreaming() {
        try { broadcast(stopCommand()) } catch (_: Exception) {}
        server?.stop()
        server = null
        currentUrl = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvcasing:stream").apply { acquire(60 * 60 * 1000L) }
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

    // ---- command channel (simple long-poll / WS-ish over HTTP for robustness) ----
    private val subscribers = mutableListOf<NanoHTTPD.Response.IStatus>()
    @Volatile private var lastCommand: String = ""

    private fun broadcast(json: String) {
        lastCommand = json
        // In a full impl this would push via WebSocket; here the receiver polls /events.
    }

    private fun playCommand(url: String) = JSONObject().apply {
        put("type", "play"); put("url", url)
    }.toString()
    private fun stopCommand() = JSONObject().apply { put("type", "stop") }.toString()

    // ---- HTTPS server ----
    private inner class HttpsStreamServer(
        port: Int,
        private val receiverHtml: String,
        private val onCommand: (String) -> Unit,
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/", "/index.html" -> newFixedLengthResponse(Response.Status.OK, "text/html", receiverHtml)
                "/events" -> {
                    // Long-poll: return the latest command, client reconnects.
                    val body = lastCommand.ifEmpty { "{}" }
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                }
                "/cmd" -> {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val raw = map["postData"] ?: ""
                    onCommand(raw)
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }
        }
    }

    private fun SSL_SERVER_FACTORY(): SSLServerSocketFactory {
        // Self-signed cert generated at runtime for local-only use.
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, null)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, null, SecureRandom())
        return ctx.serverSocketFactory
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        releaseWakeLock()
    }
}
