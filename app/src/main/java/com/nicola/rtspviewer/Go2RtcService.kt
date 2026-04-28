package com.nicola.rtspviewer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class Go2RtcService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Esplicito java.lang.Process per evitare conflitto con android.os.Process
    private var nativeProcess: java.lang.Process? = null

    companion object {
        const val CHANNEL_ID  = "go2rtc"
        const val NOTIF_ID    = 1
        const val BINARY_URL  = "https://github.com/AlexxIT/go2rtc/releases/download/v1.9.4/go2rtc_linux_arm64"
        const val BINARY_SIZE = 15_000_000L

        var statusCallback: ((String) -> Unit)? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, Go2RtcService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, Go2RtcService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif("Avvio..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { launch() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        scope.cancel()
        nativeProcess?.destroy()
        super.onDestroy()
    }

    private suspend fun launch() {
        try {
            val binary = ensureBinary()
            writeConfig()
            status("Server locale attivo ●")

            val configPath = File(filesDir, "go2rtc.yaml").absolutePath
            val pb = ProcessBuilder(binary.absolutePath, "-config", configPath)
                .redirectErrorStream(true)

            // start() restituisce java.lang.Process, cast esplicito
            val proc = pb.start() as java.lang.Process
            nativeProcess = proc

            scope.launch(Dispatchers.IO) {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    Log.d("go2rtc", line)
                }
            }
            proc.waitFor()

        } catch (e: Exception) {
            Log.e("Go2RtcService", "Errore: ${e.message}", e)
            status("Errore: ${e.message}")
        }
    }

    private suspend fun ensureBinary(): File = withContext(Dispatchers.IO) {
        val bin = File(filesDir, "go2rtc")
        if (!bin.exists() || bin.length() < BINARY_SIZE) {
            status("Download go2rtc (~15 MB)...")
            download(BINARY_URL, bin)
            bin.setExecutable(true)
            status("Download completato ✓")
        }
        bin
    }

    private fun writeConfig() {
        val p    = getSharedPreferences("cam", Context.MODE_PRIVATE)
        val ip   = p.getString("ip",   "192.168.1.10") ?: "192.168.1.10"
        val user = p.getString("user", "admin")        ?: "admin"
        val pass = p.getString("pass", "admin")        ?: "admin"

        val yaml = """
streams:
  cortile:
    - dvrip://$user:$pass@$ip:34567

api:
  listen: :1984
  origin: '*'

log:
  level: warn
""".trimIndent()
        File(filesDir, "go2rtc.yaml").writeText(yaml)
    }

    private suspend fun download(url: String, dest: File) = withContext(Dispatchers.IO) {
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            conn = URL(conn.getHeaderField("Location")).openConnection() as HttpURLConnection
            conn.connect()
        }
        val total = conn.contentLengthLong.coerceAtLeast(1)
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    val pct = (downloaded * 100 / total).toInt()
                    if (pct % 10 == 0) status("Download go2rtc... $pct%")
                }
            }
        }
    }

    private fun status(msg: String) {
        updateNotif(msg)
        statusCallback?.invoke(msg)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Telecamera Cortile",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RTSP Viewer – Cortile")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotif(text))
    }
}
