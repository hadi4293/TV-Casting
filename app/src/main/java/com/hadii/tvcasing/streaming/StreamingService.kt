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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hadii.tvcasing.MainActivity
import com.hadii.tvcasing.R
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.security.auth.x500.X500Principal
import sun.security.x509.AlgorithmId
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.X500Name
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo

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
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    private var server: HttpsStreamServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentUrl: String? = null

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
        startForegroundWithNotification()
        acquireWakeLock()

        try {
            server?.stop()
            server = HttpsStreamServer(PORT, assetsReceiverHtml()).also {
                it.makeSecure(SSL_SERVER_FACTORY(), null)
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
            // Give the server a moment, then push the play command.
            Thread.sleep(400)
            broadcast(playCommand(url))
        } catch (e: Exception) {
            Log.e(TAG, "startStreaming failed", e)
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
    private fun stopCommand() = JSONObject().apply { put("type", "stop") }.toString()

    // ---- HTTPS + WebSocket server ----
    private inner class HttpsStreamServer(
        port: Int,
        private val receiverHtml: String,
    ) : NanoWSD(port) {

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

    private fun SSL_SERVER_FACTORY(): SSLServerSocketFactory {
        // Self-signed cert generated at runtime for local-only use.
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()
        val cert = generateSelfSignedCert(keyPair, "CN=TV Casting, O=Local")

        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setKeyEntry("key", keyPair.private, null, arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, null)

        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, null, SecureRandom())
        return ctx.serverSocketFactory
    }

    @Suppress("DEPRECATION")
    private fun generateSelfSignedCert(keyPair: KeyPair, dn: String): X509Certificate {
        val now = Date()
        val expiry = Date(now.time + 365L * 24 * 60 * 60 * 1000)
        val info = X509CertInfo().apply {
            set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
            set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(BigInteger(64, SecureRandom())))
            set(X509CertInfo.SUBJECT, X500Name(dn))
            set(X509CertInfo.ISSUER, X500Name(dn))
            set(X509CertInfo.VALIDITY, CertificateValidity(now, expiry))
            set(X509CertInfo.KEY, CertificateX509Key(keyPair.public))
            set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")))
        }
        val cert = X509CertImpl(info)
        cert.sign(keyPair.private, "SHA256withRSA")
        // Re-sign so the algorithm id matches the actual signature.
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, cert.get(X509CertImpl.SIG_ALG))
        val cert2 = X509CertImpl(info)
        cert2.sign(keyPair.private, "SHA256withRSA")
        return cert2
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        releaseWakeLock()
    }
}
