package com.example.btchat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.util.UUID

@SuppressLint("MissingPermission")
object BtConnection {
    private val APP_UUID: UUID = UUID.fromString("8f3c2a10-5b7e-4c1d-9a64-2f0e6d1b7a55")
    private val main = Handler(Looper.getMainLooper())
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile var onMessage: ((String) -> Unit)? = null
    @Volatile var onClosed: (() -> Unit)? = null
    var remoteName: String = ""

    fun listen(adapter: BluetoothAdapter, onConnected: () -> Unit, onError: (String) -> Unit) {
        stopListening()
        Thread {
            try {
                val ss = adapter.listenUsingRfcommWithServiceRecord("BtChat", APP_UUID)
                serverSocket = ss
                val s = ss.accept()
                try { ss.close() } catch (_: Exception) {}
                serverSocket = null
                attach(s)
                main.post { onConnected() }
            } catch (e: Exception) {
                main.post { onError(e.message ?: "Fehler beim Warten") }
            }
        }.start()
    }

    fun stopListening() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    fun connect(adapter: BluetoothAdapter, device: BluetoothDevice,
                onConnected: () -> Unit, onError: (String) -> Unit) {
        stopListening()
        Thread {
            try {
                adapter.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                attach(s)
                main.post { onConnected() }
            } catch (e: Exception) {
                main.post { onError(e.message ?: "Verbindung fehlgeschlagen") }
            }
        }.start()
    }

    private fun attach(s: BluetoothSocket) {
        close()
        socket = s
        remoteName = s.remoteDevice.name ?: s.remoteDevice.address
        Thread {
            try {
                val reader = s.inputStream.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val line = reader.readLine() ?: break
                    main.post { onMessage?.invoke(line) }
                }
            } catch (_: Exception) {}
            main.post { onClosed?.invoke() }
        }.start()
    }

    fun send(text: String): Boolean {
        return try {
            val o = socket!!.outputStream
            o.write((text.replace("\n", " ") + "\n").toByteArray(Charsets.UTF_8))
            o.flush()
            true
        } catch (e: Exception) { false }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
