package vn.navara.launcher

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

// Máy chủ HTTP nhỏ nhận dữ liệu từ tính năng "Upload to webserver" của Torque Pro
object TorqueServer {
    const val PORT = 8765
    @Volatile var lastHit = 0L
    @Volatile var lastKeys = ""
    private var server: ServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope, onParams: (Map<String, String>) -> Unit) {
        if (job?.isActive == true) return
        lastHit = 0L
        lastKeys = ""
        job = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
                server = ss
                while (isActive) {
                    val s = ss.accept()
                    launch { handle(s, onParams) }
                }
            } catch (e: Exception) {
                Log.d("Obd", "torque server: " + e.message)
            }
        }
    }

    private fun handle(s: Socket, onParams: (Map<String, String>) -> Unit) {
        try {
            s.use {
                s.soTimeout = 3000
                val line = s.getInputStream().bufferedReader().readLine() ?: return
                val q = line.substringAfter("?", "").substringBefore(" ")
                val m = HashMap<String, String>()
                for (part in q.split("&")) {
                    val i = part.indexOf('=')
                    if (i > 0) m[part.substring(0, i)] = try { URLDecoder.decode(part.substring(i + 1), "UTF-8") } catch (e: Exception) { part.substring(i + 1) }
                }
                s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 3\r\nConnection: close\r\n\r\nOK!".toByteArray())
                lastHit = System.currentTimeMillis()
                lastKeys = m.keys.filter { it.startsWith("k") }.joinToString(",")
                onParams(m)
            }
        } catch (e: Exception) {
            Log.d("Obd", "torque handle: " + e.message)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { server?.close() }
        server = null
    }
}
