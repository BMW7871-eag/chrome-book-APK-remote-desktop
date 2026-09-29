package com.example.chromebookremotehost

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chromebookremotehost.capture.ScreenCaptureService
import com.example.chromebookremotehost.input.ChromeOsInputBackend
import com.example.chromebookremotehost.ngrok.NgrokManager
import com.example.chromebookremotehost.security.SecureStore
import com.example.chromebookremotehost.server.RemoteServer
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : ComponentActivity() {
    private lateinit var token: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var port: TextInputEditText
    private lateinit var status: TextView
    private lateinit var url: TextView
    private lateinit var log: TextView
    private lateinit var capabilities: TextView
    private lateinit var start: MaterialButton
    private lateinit var stop: MaterialButton
    private lateinit var copy: MaterialButton
    private lateinit var secure: SecureStore
    private var server: RemoteServer? = null
    private val ngrok = NgrokManager()
    private var pendingPort = 8080

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            setError("Screen capture permission was denied. Remote access was not started.")
            return@registerForActivityResult
        }
        startRemote(result.resultCode, result.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        token = findViewById(R.id.ngrokToken); password = findViewById(R.id.remotePassword); port = findViewById(R.id.port)
        status = findViewById(R.id.status); url = findViewById(R.id.url); log = findViewById(R.id.log); capabilities = findViewById(R.id.capabilities)
        start = findViewById(R.id.startButton); stop = findViewById(R.id.stopButton); copy = findViewById(R.id.copyButton)
        secure = SecureStore(this)
        token.setText(secure.get("ngrok_token") ?: "")
        password.setText(secure.get("remote_password") ?: "")
        capabilities.text = capabilityText()
        start.setOnClickListener { requestStart() }
        stop.setOnClickListener { stopRemote() }
        copy.setOnClickListener { copyUrl() }
    }

    private fun capabilityText(): String {
        val chrome = isLikelyChromeOs()
        return "Capabilities\n" +
            "• ChromeOS detected: ${if (chrome) "yes (heuristic)" else "no / unknown"}\n" +
            "• Screen capture: Android MediaProjection (user approval required)\n" +
            "• Global ChromeOS mouse/keyboard injection: unavailable to a normal Android app\n" +
            "• Web server: available\n" +
            "• ngrok: embedded official Java SDK"
    }

    private fun isLikelyChromeOs(): Boolean {
        val model = (Build.MODEL + " " + Build.DEVICE + " " + Build.BRAND).lowercase()
        return model.contains("chromebook") || model.contains("chrome") || model.contains("eve") || model.contains("volteer") || model.contains("octopus")
    }

    private fun requestStart() {
        val t = token.text?.toString()?.trim().orEmpty()
        val p = password.text?.toString().orEmpty()
        val portValue = port.text?.toString()?.toIntOrNull()
        when {
            t.isBlank() -> setError("Enter your ngrok Auth Token.")
            p.length < 6 -> setError("Use a remote password with at least 6 characters.")
            portValue == null || portValue !in 1024..65535 -> setError("Choose a local port from 1024 to 65535.")
            else -> {
                pendingPort = portValue
                secure.put("ngrok_token", t); secure.put("remote_password", p)
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                status.text = "● Waiting for screen permission"; status.setTextColor(0xFFFFC857.toInt())
                projectionLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }
    }

    private fun startRemote(resultCode: Int, data: Intent) {
        try {
            server?.stop()
            server = RemoteServer(pendingPort, secure.get("remote_password")!!, ChromeOsInputBackend())
            server!!.start(10_000, false)
        } catch (e: Exception) {
            setError("Web server failed to start. Port $pendingPort may already be in use. ${e.message ?: ""}")
            return
        }

        val capture = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(capture) else startService(capture)
        } catch (e: Exception) {
            server?.stop(); server = null
            setError("Could not start screen capture: ${e.message ?: "permission or ChromeOS limitation"}")
            return
        }

        status.text = "● Connecting to ngrok"; status.setTextColor(0xFFFFC857.toInt())
        start.isEnabled = false; stop.isEnabled = true
        val t = secure.get("ngrok_token")!!
        ngrok.start(t, pendingPort) { result ->
            runOnUiThread {
                result.onSuccess { public ->
                    status.text = "● Online"; status.setTextColor(0xFF6EE7A0.toInt()); url.text = public; copy.isEnabled = true
                    log.text = "Remote page: $public\nAuthentication is required. Screen capture is user-approved MediaProjection.\nInput: unavailable on standard ChromeOS Android app permissions."
                }.onFailure { e ->
                    stopRemote()
                    setError("ngrok failed: ${e.message ?: e.javaClass.simpleName}. Check the token, internet connection, and Android native ngrok runtime packaging.")
                }
            }
        }
    }

    private fun stopRemote() {
        try { ngrok.stop() } catch (_: Exception) {}
        try { server?.stop() } catch (_: Exception) {}
        server = null
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)
        try { startService(stopIntent) } catch (_: Exception) {}
        status.text = "● Offline"; status.setTextColor(0xFFFF6B6B.toInt()); url.text = "Not running"; copy.isEnabled = false
        start.isEnabled = true; stop.isEnabled = false
        log.text = "Remote access stopped."
    }

    private fun copyUrl() {
        val value = url.text?.toString().orEmpty()
        if (!value.startsWith("http")) return
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Remote URL", value))
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }

    private fun setError(message: String) {
        status.text = "● Error"; status.setTextColor(0xFFFF7777.toInt()); log.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        // Do not leave an internet-facing tunnel alive if Android destroys the UI unexpectedly.
        stopRemote()
        super.onDestroy()
    }
}
