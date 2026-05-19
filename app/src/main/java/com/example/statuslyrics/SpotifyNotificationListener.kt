package com.example.statuslyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SpotifyNotificationListener : NotificationListenerService() {

    companion object {
        var trackName: String = ""
        var artistName: String = ""
        var albumName: String = ""
        var isPlaying: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "statuslyrics_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "StatusLyrics Serwer w Tle",
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
            .setContentTitle("StatusLyrics działa")
            .setContentText("Serwer HTTP nasłuchuje na porcie 2137...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.spotify.music") {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val artist = extras.getCharSequence("android.text")?.toString() ?: ""
            val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

            if (title.isNotEmpty() && artist.isNotEmpty() && !title.contains("Spotify")) {
                trackName = title
                artistName = artist
                albumName = subText
                isPlaying = true
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.spotify.music") {
            isPlaying = false
        }
    }
}
