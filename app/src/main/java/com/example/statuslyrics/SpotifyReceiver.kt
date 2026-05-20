package com.example.statuslyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpotifyReceiver : BroadcastReceiver() {

    companion object {
        var trackName: String = ""
        var artistName: String = ""
        var albumName: String = ""
        var durationSec: Int = 0
        var isPlaying: Boolean = false
        
        var progressMs: Long = 0
        var lastUpdateTime: Long = 0
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == "com.spotify.music.metadatachanged") {
            trackName = intent.getStringExtra("track") ?: ""
            artistName = intent.getStringExtra("artist") ?: ""
            albumName = intent.getStringExtra("album") ?: ""
            
            // Pobieramy długość utworu w milisekundach i zmieniamy na sekundy dla LRCLIB
            val durationMs = intent.getIntExtra("length", 0)
            durationSec = durationMs / 1000
            
        } else if (action == "com.spotify.music.playbackstatechanged") {
            isPlaying = intent.getBooleanExtra("playing", false)
            progressMs = intent.getIntExtra("playbackPosition", 0).toLong()
            lastUpdateTime = System.currentTimeMillis()
        }
    }
}
