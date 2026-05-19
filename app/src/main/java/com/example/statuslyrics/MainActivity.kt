package com.example.statuslyrics

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var server: StatusServer
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusText)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        
        btnPermissions.text = "Autoryzuj przez Shizuku"

        btnPermissions.setOnClickListener {
            if (Shizuku.pingBinder()) {
                // Jeśli Shizuku działa, prosimy o dostęp
                Shizuku.requestPermission(0)
            } else {
                statusTextView.text = "BŁĄD: Shizuku nie jest uruchomione w tle telefonu!"
            }
        }

        // Rejestrujemy nasłuchiwanie na wynik przyznania uprawnień
        Shizuku.addRequestPermissionResultListener(this)

        server = StatusServer(2137)
        server.start()
    }

    override fun onResume() {
        super.onResume()
        checkShizukuStatus()
    }

    private fun checkShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            statusTextView.text = "Uruchom najpierw aplikację Shizuku w telefonie!"
            return
        }

        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            statusTextView.text = "Serwer StatusLyrics działa na porcie 2137!\nPołączono poprawnie przez Shizuku."
        } else {
            statusTextView.text = "Wymagana autoryzacja Shizuku."
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        checkShizukuStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this)
        server.stop()
    }
}
