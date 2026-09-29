package com.example.chromebookremotehost.capture

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "start_capture"
        const val ACTION_STOP = "stop_capture"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        val latestJpeg = AtomicReference<ByteArray?>(null)
        @Volatile var active = false
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var callback: MediaProjection.Callback? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("capture", "Remote screen", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopCapture(); stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_START) {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (data == null || code != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
            startForeground(1001, Notification.Builder(this, "capture")
                .setContentTitle("Chromebook Remote Host")
                .setContentText("Remote screen sharing is active")
                .setSmallIcon(android.R.drawable.ic_menu_view).build())
            startCapture(code, data)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        stopCapture()
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtMost(1600)
        val height = metrics.heightPixels.coerceAtMost(1000)
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                val cropped = if (rowPadding > 0) Bitmap.createBitmap(bmp, 0, 0, width, height) else bmp
                val out = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 65, out)
                latestJpeg.set(out.toByteArray())
                if (cropped !== bmp) cropped.recycle()
                bmp.recycle()
            }
        }, Handler(Looper.getMainLooper()))
        callback = object : MediaProjection.Callback() { override fun onStop() { stopCapture() } }
        projection!!.registerCallback(callback!!, Handler(Looper.getMainLooper()))
        display = projection!!.createVirtualDisplay("ChromebookRemoteHost", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null)
        active = true
    }

    private fun stopCapture() {
        active = false
        latestJpeg.set(null)
        display?.release(); display = null
        reader?.close(); reader = null
        callback?.let { projection?.unregisterCallback(it) }
        callback = null
        projection?.stop(); projection = null
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
