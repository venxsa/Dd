package com.example.statuslyrics

import dev.rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlin.concurrent.thread

class StatusServer(private val port: Int = 2137) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread { handleClient(socket) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val output: OutputStream = socket.getOutputStream()
            
            // Pobieramy dane bezpośrednio z systemu za pomocą Shizuku
            val mediaData = getMediaDataViaShizuku()
            
            var spotifyId = ""
            if (mediaData.playing && mediaData.track.isNotEmpty()) {
                spotifyId = fetchSpotifyId(mediaData.track, mediaData.artist)
            }

            val jsonResponse = """
                {
                    "playing": ${mediaData.playing},
                    "track": "${mediaData.track.replace("\"", "\\\"")}",
                    "artist": "${mediaData.artist.replace("\"", "\\\"")}",
                    "album": "${mediaData.album.replace("\"", "\\\"")}",
                    "spotify_id": "$spotifyId"
                }
            """.trimIndent()

            val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" + 
                    "Content-Length: ${jsonResponse.toByteArray().size}\r\n\r\n" +
                    jsonResponse

            output.write(response.toByteArray())
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Klasa pomocnicza na dane
    data class MediaInfo(val track: String, val artist: String, val album: String, val playing: Boolean)

    private fun getMediaDataViaShizuku(): MediaInfo {
        // Jeśli Shizuku nie ma uprawnień, zwracamy pustki
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return MediaInfo("", "", "", false)
        }

        try {
            // Wykonujemy polecenie systemowe dumpsys przez Shizuku
            val process = Shizuku.newProcess(arrayOf("cmd", "media_session", "dumpsys"), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            var currentPackage = ""
            var track = ""
            var artist = ""
            var album = ""
            var isPlaying = false
            var inSpotifySection = false

            reader.forEachLine { line ->
                val trimmed = line.trim()
                
                // Szukamy sekcji aktywnej sesji dla Spotify
                if (trimmed.startsWith("package=")) {
                    currentPackage = trimmed.substringAfter("package=").substringBefore(" ")
                    inSpotifySection = (currentPackage == "com.spotify.music")
                }
                
                if (inSpotifySection) {
                    if (trimmed.startsWith("description=")) {
                        // Przykładowy format: description=Tytuł, Wykonawca, Album
                        val desc = trimmed.substringAfter("description=")
                        val parts = desc.split(", ")
                        if (parts.size >= 1) track = parts[0]
                        if (parts.size >= 2) artist = parts[1]
                        if (parts.size >= 3) album = parts[2]
                    }
                    if (trimmed.startsWith("state=PlaybackState")) {
                        // Sprawdzamy czy stan to STATE_PLAYING (zazwyczaj kod 3)
                        if (trimmed.contains("state=3")) {
                            isPlaying = true
                        }
                    }
                }
            }
            process.waitFor()
            
            if (track.isNotEmpty()) {
                return MediaInfo(track, artist, album, isPlaying)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return MediaInfo("", "", "", false)
    }

    private fun fetchSpotifyId(track: String, artist: String): String {
        return try {
            val query = URLEncoder.encode("$track $artist", "UTF-8")
            val url = URL("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val pattern = Pattern.compile("track/([^&\"?\\s]+)")
                val matcher = pattern.matcher(response)
                if (matcher.find()) return matcher.group(1) ?: ""
            }
            ""
        } catch (e: Exception) { "" }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}
