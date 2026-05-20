package com.example.statuslyrics

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var server: StatusServer
    private val spotifyReceiver = SpotifyReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusTextView = findViewById<TextView>(R.id.statusText)
        statusTextView.text = "Serwer StatusLyrics aktywny port: 2137\nNasłuchiwanie piosenek ze Spotify działa!"

        // Filtry komunikatów bezpośrednio z transmisji Spotify
        val filter = IntentFilter().apply {
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.queuechanged")
        }

        // Rejestracja bezpieczna dla nowych wersji Androida
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(spotifyReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(spotifyReceiver, filter)
        }

        // Start serwera HTTP
        server = StatusServer(2137)
        server.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
        try {
            unregisterReceiver(spotifyReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
