package com.example.chromebookremotehost.ngrok

import com.ngrok.Forwarder
import com.ngrok.Session
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class NgrokManager {
    private var session: Session? = null
    private var forwarder: Forwarder.Endpoint? = null
    private val publicUrl = AtomicReference<String?>(null)

    fun start(token: String, localPort: Int, callback: (Result<String>) -> Unit) {
        Thread {
            try {
                val s = Session.withAuthtoken(token).metadata("Chromebook Remote Host").connect()
                val f = s.httpEndpoint().forward(URL("http://127.0.0.1:$localPort"))
                session = s
                forwarder = f
                val url = f.url.toString()
                publicUrl.set(url)
                callback(Result.success(url))
            } catch (e: Throwable) {
                try { session?.close() } catch (_: Exception) {}
                session = null; forwarder = null
                callback(Result.failure(e))
            }
        }.start()
    }

    fun stop() {
        try { forwarder?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        forwarder = null; session = null; publicUrl.set(null)
    }

    fun url(): String? = publicUrl.get()
}
