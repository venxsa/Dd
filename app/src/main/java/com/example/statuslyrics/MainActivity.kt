package com.example.statuslyrics

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var server: StatusServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusTextView = findViewById<TextView>(R.id.statusText)
        statusTextView.text = "Serwer StatusLyrics aktywny port: 2137"

        // Uruchomienie serwera HTTP
        server = StatusServer(2137)
        server.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}
