package com.example.statuslyrics

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var server: StatusServer
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusText)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)

        btnPermissions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        server = StatusServer(2137)
        server.start()
    }

    override fun onResume() {
        super.onResume()
        // Sprawdzamy przy powrocie do aplikacji, czy użytkownik kliknął zezwolenie
        if (isNotificationServiceEnabled()) {
            statusTextView.text = "Serwer StatusLyrics działa na porcie 2137!\nPołączono z powiadomieniami."
        } else {
            statusTextView.text = "Serwer aktywny, ale BRAK dostępu do powiadomień!"
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && pkgName == cn.packageName) {
                    return true
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}
