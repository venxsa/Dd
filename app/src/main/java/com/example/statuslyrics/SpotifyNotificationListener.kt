package com.example.statuslyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService

class SpotifyNotificationListener : NotificationListenerService(), MediaSessionManager.OnActiveSessionsChangedListener {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeControllers = java.util.ArrayList<MediaController>()

    companion object {
        var trackName: String = ""
        var artistName: String = ""
        var albumName: String = ""
        var isPlaying: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            val componentName = ComponentName(this, SpotifyNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(this, componentName)
            // Wywołujemy ręcznie na start, żeby sprawdzić czy już coś gra
            onActiveSessionsChanged(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "statuslyrics_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "StatusLyrics Serwer w Tle", NotificationManager.IMPORTANCE_LOW)
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

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        if (controllers == null) return
        
        activeControllers.clear()
        for (controller in controllers) {
            if (controller.packageName == "com.spotify.music") {
                activeControllers.add(controller)
                updateMediaData(controller)
                
                // Rejestrujemy callback, żeby nasłuchiwać zmian w czasie rzeczywistym
                controller.registerCallback(object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        updateMediaData(controller)
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        updateMediaData(controller)
                    }
                })
            }
        }
    }

    private fun updateMediaData(controller: MediaController) {
        val metadata = controller.metadata
        val state = controller.playbackState

        if (metadata != null) {
            trackName = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            artistName = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            albumName = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        }

        if (state != null) {
            isPlaying = state.state == PlaybackState.STATE_PLAYING
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
