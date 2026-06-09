package com.zyz4.gamepademu.service

import com.google.protobuf.AbstractMessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class TcpServerService {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val writeMutex = Mutex()
    var isRunning = false
        private set
    var isClientConnected = false
        private set

    suspend fun start(
        port: Int,
        onClientConnected: () -> Unit,
        onClientDisconnected: () -> Unit,
        onMessage: (ByteArray) -> Unit,
    ) = withContext(Dispatchers.IO) {
        stop()
        serverSocket = ServerSocket(port)
        isRunning = true
        while (isRunning && !isClientConnected) {
            try {
                serverSocket?.soTimeout = 1000
                val socket = serverSocket!!.accept()
                socket.keepAlive = true
                socket.tcpNoDelay = true
                clientSocket = socket
                outputStream = socket.getOutputStream()
                inputStream = socket.getInputStream()
                isClientConnected = true
                onClientConnected()
                readLoop(onMessage)
                isClientConnected = false
                onClientDisconnected()
            } catch (_: java.net.SocketTimeoutException) {
            }
        }
    }

    private fun readLoop(onMessage: (ByteArray) -> Unit) {
        val buf = ByteArray(4096)
        while (isRunning && isClientConnected) {
            try {
                val len = inputStream?.read(buf) ?: -1
                if (len <= 0) break
                onMessage(buf.copyOf(len))
            } catch (_: Exception) { break }
        }
    }

    suspend fun send(message: AbstractMessageLite<*, *>) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val data = message.toByteArray()
                val len = data.size
                val header = byteArrayOf(
                    (len shr 24).toByte(), (len shr 16).toByte(),
                    (len shr 8).toByte(), len.toByte()
                )
                outputStream?.write(header)
                outputStream?.write(data)
                outputStream?.flush()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        isClientConnected = false
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
        outputStream = null
        inputStream = null
    }
}
