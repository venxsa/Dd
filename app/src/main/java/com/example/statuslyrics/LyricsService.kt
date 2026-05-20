package com.example.statuslyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

class LyricsService : Service() {

    private var server: StatusServer? = null
    private val spotifyReceiver = SpotifyReceiver()

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        // Rejestrujemy nasłuchiwanie Spotify wewnątrz nieumieralnej usługi
        val filter = IntentFilter().apply {
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.queuechanged")
        }
        registerReceiver(spotifyReceiver, filter, Context.RECEIVER_EXPORTED)

        // Uruchamiamy serwer HTTP i pętlę LRCLIB
        server = StatusServer(2137)
        server?.start()
    }

    private fun startForegroundNotification() {
        val channelId = "statuslyrics_service_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "StatusLyrics Aktywność w Tle",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("StatusLyrics jest aktywny")
            .setContentText("Synchronizacja z LRCLIB działa w tle na porcie 2137")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Informuje system, że w razie braku pamięci ma od razu włączyć usługę ponownie
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        try {
            unregisterReceiver(spotifyReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
