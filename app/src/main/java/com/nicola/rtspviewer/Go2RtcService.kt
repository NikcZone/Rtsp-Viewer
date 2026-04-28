package com.nicola.rtspviewer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*

class Go2RtcService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nativeProcess: java.lang.Process? = null

    companion object {
        const val CHANNEL_ID = "go2rtc"
        const val NOTIF_ID   = 1

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
            // go2rtc è impacchettato nell'APK come libreria nativa
            // nativeLibraryDir è sempre eseguibile su Android (nessun blocco SELinux)
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val binary = File(nativeLibDir, "libgo2rtc.so")

            if (!binary.exists()) {
                status("Errore: libgo2rtc.so non trovato in $nativeLibDir")
                Log.e("go2rtc", "Binary not found: ${binary.absolutePath}")
                return
            }

            binary.setExecutable(true)
            writeConfig()
            status("Server locale attivo ●")

            val configPath = File(filesDir, "go2rtc.yaml").absolutePath
            Log.d("go2rtc", "Avvio: ${binary.absolutePath} -config $configPath")

            val proc = ProcessBuilder(binary.absolutePath, "-config", configPath)
                .redirectErrorStream(true)
                .start() as java.lang.Process
            nativeProcess = proc

            scope.launch(Dispatchers.IO) {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    Log.d("go2rtc", line)
                }
            }
            val exitCode = proc.waitFor()
            Log.w("go2rtc", "Processo terminato con codice: $exitCode")
            status("Server arrestato (codice $exitCode) — riavvio…")

            // Riavvia automaticamente se crasha
            delay(3000)
            launch()

        } catch (e: Exception) {
            Log.e("Go2RtcService", "Errore: ${e.message}", e)
            status("Errore: ${e.message}")
        }
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
