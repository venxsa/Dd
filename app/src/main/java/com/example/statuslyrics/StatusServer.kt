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
            
            // Odpytujemy system za pomocą Shizuku
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

    data class MediaInfo(val track: String, val artist: String, val album: String, val playing: Boolean)

    private fun getMediaDataViaShizuku(): MediaInfo {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return MediaInfo("", "", "", false)
        }

        try {
            // Wywołujemy zrzut sesji multimedialnych przez Shizuku z prawami powłoki ADB
            val process = Shizuku.newProcess(arrayOf("cmd", "media_session", "dumpsys"), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            var track = ""
            var artist = ""
            var album = ""
            var isPlaying = false
            var inSpotifySection = false

            reader.forEachLine { line ->
                val trimmed = line.trim()
                
                // Wykrywanie bloku Spotify
                if (trimmed.startsWith("package=")) {
                    val pkg = trimmed.substringAfter("package=").substringBefore(" ")
                    inSpotifySection = (pkg == "com.spotify.music")
                }
                
                if (inSpotifySection) {
                    // Sposób 1: Czytanie z uniwersalnego pola description
                    if (trimmed.startsWith("description=")) {
                        val desc = trimmed.substringAfter("description=")
                        if (desc != "null" && desc.contains(",")) {
                            val parts = desc.split(", ")
                            if (parts.size >= 1) track = parts[0]
                            if (parts.size >= 2) artist = parts[1]
                            if (parts.size >= 3) album = parts[2]
                        }
                    }
                    
                    // Sposób 2: Agresywny fallback, gdy system rozbija metadane na klucze we flagach
                    if (track.isEmpty()) {
                        if (trimmed.contains("android.media.metadata.TITLE=")) {
                            track = trimmed.substringAfter("android.media.metadata.TITLE=").substringBefore(",")
                        }
                        if (trimmed.contains("android.media.metadata.ARTIST=")) {
                            artist = trimmed.substringAfter("android.media.metadata.ARTIST=").substringBefore(",")
                        }
                        if (trimmed.contains("android.media.metadata.ALBUM=")) {
                            album = trimmed.substringAfter("android.media.metadata.ALBUM=").substringBefore(",")
                        }
                    }

                    // Sprawdzanie stanu odtwarzania (state=3 oznacza odtwarzanie - STATE_PLAYING)
                    if (trimmed.startsWith("state=PlaybackState")) {
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
