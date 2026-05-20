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

    // Cache na napisy, żeby nie odpytywać LRCLIB przy każdym zapytaniu serwera
    private var cachedTrackId = ""
    private var cachedLines = ArrayList<Pair<Long, String>>()
    private var currentLyricText = "Oczekiwanie..."

    fun start() {
        isRunning = true
        
        // Pętla odświeżania tekstu co 200ms bezpośrednio w telefonie
        thread {
            while (isRunning) {
                try {
                    updateLyricsPosition()
                    Thread.sleep(200)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Serwer HTTP dla bota
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
                                    "spotify_id": "${SpotifyReceiver.trackId}",
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

    // Funkcja wylicza aktualną milisekundę i dopasowuje tekst z LRCLIB
    private fun updateLyricsPosition() {
        val trackId = SpotifyReceiver.trackId
        if (trackId.isEmpty() || !SpotifyReceiver.isPlaying) {
            currentLyricText = if (trackId.isEmpty()) "Brak utworu" else "Muzyka zatrzymana"
            return
        }

        // Jeśli zmieniła się piosenka, pobieramy świeże LRC z LRCLIB
        if (trackId != cachedTrackId) {
            cachedTrackId = trackId
            cachedLines.clear()
            fetchLyricsFromLrclib(trackId)
        }

        if (cachedLines.isEmpty()) {
            currentLyricText = "${SpotifyReceiver.trackName} - ${SpotifyReceiver.artistName}"
            return
        }

        // Liczymy dokładny postęp odtwarzania w danej chwili
        val timePassed = System.currentTimeMillis() - SpotifyReceiver.lastUpdateTime
        val estimatedProgress = SpotifyReceiver.progressMs + timePassed

        var bestMatch = ""
        for (line in cachedLines) {
            if (estimatedProgress >= line.first) {
                bestMatch = line.second
            } else {
                break
            }
        }
        currentLyricText = bestMatch.ifEmpty { "${SpotifyReceiver.trackName} - ${SpotifyReceiver.artistName}" }
    }

    private fun fetchLyricsFromLrclib(trackId: String) {
        thread {
            try {
                val url = URL("https://lrclib.net/api/get?spotifyTrackId=$trackId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000

                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    if (json.has("syncedLyrics")) {
                        val syncedLyrics = json.getString("syncedLyrics")
                        parseLrc(syncedLyrics)
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
                val millis = secParts[1].toLong() * (if (secParts[1].length == 2) 10 else 1)

                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                lines.add(Pair(totalMs, words))
            } catch (e: Exception) {
                continue
            }
        }
        cachedLines = lines
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}
