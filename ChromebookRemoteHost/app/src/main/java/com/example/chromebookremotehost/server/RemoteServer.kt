package com.example.chromebookremotehost.server

import com.example.chromebookremotehost.capture.ScreenCaptureService
import com.example.chromebookremotehost.input.InputBackend
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RemoteServer(
    port: Int,
    private val password: String,
    private val inputBackend: InputBackend
) : NanoWSD(port) {
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val random = SecureRandom()

    override fun serve(session: NanoHTTPD.IHTTPSession): Response {
        return try {
            val path = session.uri
            if (path == "/login" && session.method == NanoHTTPD.Method.POST) return login(session)
            if (path == "/" || path == "/login") return html(loginPage())
            if (path == "/desktop") {
                return if (authenticated(session)) html(desktopPage()) else redirect("/")
            }
            if (path == "/screen.jpg") {
                return if (authenticated(session)) {
                    val data = ScreenCaptureService.latestJpeg.get()
                    if (data == null) NanoHTTPD.newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Screen capture is not active.")
                    else NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "image/jpeg", data.inputStream(), data.size.toLong())
                } else unauthorized()
            }
            if (path == "/capabilities") {
                val body = "{\"screenCapture\":${ScreenCaptureService.active},\"input\":${inputBackend.available},\"inputMessage\":${json(inputBackend.explanation)}}"
                return if (authenticated(session)) NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", body) else unauthorized()
            }
            NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        } catch (e: Exception) {
            NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server error: ${e.message ?: "unknown"}")
        }
    }

    override fun openWebSocket(handshake: NanoWSD.IHTTPSession): NanoWSD.WebSocket = ControlSocket(handshake)

    private fun login(session: NanoHTTPD.IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: ""
        val supplied = body.substringAfter("password=", "").substringBefore('&').urlDecode()
        if (MessageDigest.isEqual(supplied.toByteArray(), password.toByteArray())) {
            val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            sessions.add(token)
            val response = redirect("/desktop")
            response.addHeader("Set-Cookie", "session=$token; HttpOnly; SameSite=Strict; Path=/")
            return response
        }
        return html(loginPage("Invalid password."), Response.Status.UNAUTHORIZED)
    }

    private fun authenticated(session: NanoHTTPD.IHTTPSession): Boolean {
        val cookie = session.headers["cookie"] ?: return false
        val token = cookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("session=") }?.substringAfter('=') ?: return false
        return sessions.contains(token)
    }

    private fun html(body: String, status: Response.Status = Response.Status.OK) = NanoHTTPD.newFixedLengthResponse(status, "text/html; charset=utf-8", body)
    private fun redirect(location: String): Response { val r = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "Redirect"); r.addHeader("Location", location); return r }
    private fun unauthorized() = NanoHTTPD.newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Authentication required")

    private fun loginPage(error: String = "") = """
<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1"><title>Chromebook Remote Host</title>
<style>body{margin:0;background:#0b0d12;color:#fff;font:16px system-ui;display:grid;place-items:center;min-height:100vh}.card{width:min(92vw,420px);background:#151923;padding:24px;border-radius:18px}input,button{width:100%;box-sizing:border-box;padding:14px;border-radius:12px;border:1px solid #3a4150;margin-top:10px;font-size:16px}input{background:#0e1118;color:#fff}button{background:#7cc7ff;border:0;font-weight:700}small{color:#9da6b5}.err{color:#ff7777}</style></head>
<body><main class=card><h1>Chromebook Remote Host</h1><p><small>Sign in to the private remote session.</small></p>${if(error.isNotEmpty()) "<p class=err>$error</p>" else ""}<form method=post action=/login><input name=password type=password placeholder="Remote password" autofocus><button>CONNECT</button></form></main></body></html>
""".trimIndent()

    private fun desktopPage() = """
<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><title>Remote Desktop</title>
<style>html,body{margin:0;background:#05070a;color:#fff;font:14px system-ui;height:100%;overflow:hidden}.bar{height:52px;background:#11151d;display:flex;align-items:center;gap:10px;padding:0 12px;box-sizing:border-box}.dot{width:9px;height:9px;background:#6ee7a0;border-radius:50%}.screen{height:calc(100% - 52px);display:grid;place-items:center;background:#05070a;touch-action:none}.screen img{max-width:100%;max-height:100%;object-fit:contain;image-rendering:auto}.btn{background:#222938;color:#fff;border:0;border-radius:9px;padding:9px 12px}.msg{margin-left:auto;color:#aeb5c2;font-size:12px}</style></head>
<body><div class=bar><span class=dot></span><b>Remote Desktop</b><span class=msg id=msg>Screen capture</span><button class=btn onclick="document.documentElement.requestFullscreen?.()">FULL SCREEN</button><button class=btn onclick="location.href='/'">DISCONNECT</button></div><div class=screen id=screen><img id=shot alt="Remote screen"></div>
<script>
const img=document.getElementById('shot'),msg=document.getElementById('msg');let timer;
function refresh(){img.src='/screen.jpg?t='+Date.now();}
setInterval(refresh,250);refresh();
async function caps(){try{let r=await fetch('/capabilities');let c=await r.json();msg.textContent=c.input?'Screen + input':'Screen capture only — '+c.inputMessage}catch(e){msg.textContent='Connection lost'}} caps();
const ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws');
function send(o){if(ws.readyState===1)ws.send(JSON.stringify(o))}
document.getElementById('screen').addEventListener('pointerdown',e=>send({type:'pointer',action:'down',x:e.clientX,y:e.clientY}));
document.getElementById('screen').addEventListener('pointerup',e=>send({type:'pointer',action:'up',x:e.clientX,y:e.clientY}));
document.getElementById('screen').addEventListener('pointermove',e=>send({type:'pointer',action:'move',x:e.clientX,y:e.clientY}));
</script></body></html>
""".trimIndent()

    private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun String.urlDecode() = java.net.URLDecoder.decode(this, "UTF-8")

    inner class ControlSocket(handshake: NanoWSD.IHTTPSession) : NanoWSD.WebSocket(handshake) {
        override fun onOpen() = Unit
        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) = Unit
        override fun onException(exception: IOException?) = Unit
        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            val text = message.textPayload
            if (text.contains("\"type\":\"pointer\"")) send(inputBackend.handlePointer(text))
            else if (text.contains("\"type\":\"keyboard\"")) send(inputBackend.handleKeyboard(text))
        }
    }
}
