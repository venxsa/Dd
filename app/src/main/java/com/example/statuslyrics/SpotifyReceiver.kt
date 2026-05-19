package com.example.statuslyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpotifyReceiver : BroadcastReceiver() {

    companion object {
        var trackName: String = ""
        var artistName: String = ""
        var albumName: String = ""
        var trackId: String = ""
        var isPlaying: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == "com.spotify.music.metadatachanged") {
            trackName = intent.getStringExtra("track") ?: ""
            artistName = intent.getStringExtra("artist") ?: ""
            albumName = intent.getStringExtra("album") ?: ""
            
            val rawId = intent.getStringExtra("id") ?: ""
            trackId = rawId.replace("spotify:track:", "") 
        } else if (action == "com.spotify.music.playbackstatechanged") {
            isPlaying = intent.getBooleanExtra("playing", false)
        }
    }
}
