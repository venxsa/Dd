package com.example.statuslyrics

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class StatusServer(private val port: Int = 2137) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    private var cachedTrackKey = ""
    private var cachedLines = ArrayList<Pair<Long, String>>()
    private var currentLyricText = "Oczekiwanie..."
    
    private var localProgressMs: Long = 0
    private var lastTickTime: Long = 0

    fun start() {
        isRunning = true
        lastTickTime = System.currentTimeMillis()
        
        thread {
            while (isRunning) {
                try {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTickTime
                    lastTickTime = now

                    if (SpotifyReceiver.isPlaying) {
                        localProgressMs += elapsed
                    }

                    val spotifyEstimated = SpotifyReceiver.progressMs + (now - SpotifyReceiver.lastUpdateTime)
                    if (SpotifyReceiver.isPlaying && Math.abs(localProgressMs - spotifyEstimated) > 1000) {
                        localProgressMs = spotifyEstimated
                    }

                    updateLyricsPosition()
                    Thread.sleep(200)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread {
                        try {
                            val output: OutputStream = socket.getOutputStream()
                            
                            val jsonResponse = """
                                {
                                    "playing": ${SpotifyReceiver.isPlaying && SpotifyReceiver.trackName.isNotEmpty()},
                                    "track": "${SpotifyReceiver.trackName.replace("\"", "\\\"")}",
                                    "artist": "${SpotifyReceiver.artistName.replace("\"", "\\\"")}",
                                    "album": "${SpotifyReceiver.albumName.replace("\"", "\\\"")}",
                                    "current_lyric": "${currentLyricText.replace("\"", "\\\"")}"
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateLyricsPosition() {
        val track = SpotifyReceiver.trackName
        val artist = SpotifyReceiver.artistName
        val album = SpotifyReceiver.albumName
        val duration = SpotifyReceiver.durationSec

        if (track.isEmpty() || !SpotifyReceiver.isPlaying) {
            currentLyricText = if (track.isEmpty()) "Brak utworu" else "Muzyka zatrzymana"
            return
        }

        val currentTrackKey = "$track|$artist|$duration"
        if (currentTrackKey != cachedTrackKey) {
            cachedTrackKey = currentTrackKey
            cachedLines.clear()
            localProgressMs = SpotifyReceiver.progressMs
            fetchLyricsFromLrclib(track, artist, album, duration)
        }

        if (cachedLines.isEmpty()) {
            currentLyricText = "$track - $artist"
            return
        }

        var bestMatch = ""
        for (line in cachedLines) {
            if (localProgressMs >= line.first) {
                bestMatch = line.second
            } else {
                break
            }
        }
        
        currentLyricText = bestMatch.ifEmpty { "$track - $artist" }
    }

    private fun fetchLyricsFromLrclib(track: String, artist: String, album: String, duration: Int) {
        thread {
            try {
                val tEncoded = URLEncoder.encode(track, "UTF-8")
                val aEncoded = URLEncoder.encode(artist, "UTF-8")
                val alEncoded = URLEncoder.encode(album, "UTF-8")

                val url = URL("https://lrclib.net/api/get?artist_name=$aEncoded&track_name=$tEncoded&album_name=$alEncoded&duration=$duration")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "StatusLyricsRPC/1.0 (https://github.com)")
                connection.connectTimeout = 4000

                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    if (json.has("syncedLyrics")) {
                        parseLrc(json.getString("syncedLyrics"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseLrc(lrcText: String) {
        val lines = ArrayList<Pair<Long, String>>()
        for (rawLine in lrcText.split("\n")) {
            val line = rawLine.trim()
            if (!line.startsWith("[")) continue
            try {
                val timePart = line.substring(1, line.indexOf("]"))
                val words = line.substring(line.indexOf("]") + 1).trim()

                val minSec = timePart.split(":")
                val minutes = minSec[0].toLong()
                val secParts = minSec[1].split(".")
                val seconds = secParts[0].toLong()
                
                val msStr = secParts[1].trim()
                var millis = msStr.toLong()
                if (msStr.length == 2) {
                    millis *= 10
                }

                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                lines.add(Pair(totalMs, words))
            } catch (e: Exception) {
                continue
            }
        }
        lines.sortBy { it.first }
        cachedLines = lines
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}
