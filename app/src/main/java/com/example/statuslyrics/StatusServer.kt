package com.example.statuslyrics

import java.io.OutputStream
import java.net.ServerSocket
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
                    thread {
                        try {
                            val output: OutputStream = socket.getOutputStream()
                            
                            val jsonResponse = """
                                {
                                    "playing": ${SpotifyReceiver.isPlaying},
                                    "track": "${SpotifyReceiver.trackName.replace("\"", "\\\"")}",
                                    "artist": "${SpotifyReceiver.artistName.replace("\"", "\\\"")}",
                                    "album": "${SpotifyReceiver.albumName.replace("\"", "\\\"")}",
                                    "spotify_id": "${SpotifyReceiver.trackId}"
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

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
