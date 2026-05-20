package com.example.statuslyrics

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusTextView = findViewById<TextView>(R.id.statusText)
        statusTextView.text = "Serwer StatusLyrics działa w tle systemu!\nMożesz teraz bezpiecznie zamknąć to okno."

        // Uruchamiamy usługę w tle (metoda zależna od wersji Androida)
        val serviceIntent = Intent(this, LyricsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
