package com.example.cameracontrol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MjpegTextureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var running = false
    private var streamUrl: String? = null
    private var readerThread: Thread? = null
    private var lastFrameTime = 0L
    private val minFrameDelay = 33L // Max ~30fps
    private val textureLock = Any() // Synchronisierung für Texture-Zugriff
    
    // Retry-Logik
    private var retryThread: Thread? = null
    private var isConnected = false
    private val retryInterval = 5000L // 5 Sekunden
    private var lastSuccessfulFrame = 0L
    
    // Status-Text
    private val statusPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Letzter empfangener JPEG-Frame (raw bytes) – für Auto-Tune-Analyse
    @Volatile private var latestJpegBytes: ByteArray? = null
    // One-shot Callback: wird genau einmal für den nächsten Frame gefeuert, dann gelöscht
    @Volatile var onNextFrameCallback: ((ByteArray) -> Unit)? = null

    fun getLatestJpeg(): ByteArray? = latestJpegBytes

    // Pinch-to-Zoom-Zustand (UI-Thread schreibt, Render-Thread liest)
    @Volatile private var zoomScale = 1.0f
    @Volatile private var zoomTx = 0f
    @Volatile private var zoomTy = 0f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = (zoomScale * factor).coerceIn(1.0f, 5.0f)
                val realFactor = newScale / zoomScale
                // Fokuspunkt auf dem Bildschirm bleibt beim Zoomen an seiner Position
                zoomTx = zoomTx * realFactor + (detector.focusX - width / 2f) * (1f - realFactor)
                zoomTy = zoomTy * realFactor + (detector.focusY - height / 2f) * (1f - realFactor)
                zoomScale = newScale
                clampTranslate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                performClick()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (zoomScale > 1.0f) {
                    zoomScale = 1.0f; zoomTx = 0f; zoomTy = 0f
                } else {
                    zoomScale = 2.0f
                    clampTranslate()
                }
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                if (zoomScale > 1.0f) {
                    zoomTx -= dX
                    zoomTy -= dY
                    clampTranslate()
                    return true
                }
                return false
            }
        })

    init { surfaceTextureListener = this }

    fun startStream(url: String) {
        streamUrl = url
        running = true
        isConnected = false
        Log.d("MJPEG", "startStream called url=$url")
        if (isAvailable) {
            startReaderThread()
            startRetryMonitor()
        }
    }

    fun stopStream() {
        running = false
        readerThread?.interrupt()
        readerThread = null
        retryThread?.interrupt()
        retryThread = null
        isConnected = false
        
        // Zeige "Keine Vorschau" Nachricht wenn Stream gestoppt wird
        drawStatusText("Keine Vorschau\nWiederverbinden...")
    }

    /** Zoom auf 1:1 zurücksetzen, z.B. beim Verlassen des Vollbilds. */
    fun resetZoom() {
        zoomScale = 1.0f; zoomTx = 0f; zoomTy = 0f
    }

    private fun clampTranslate() {
        if (width == 0 || height == 0) return
        val maxTx = width * (zoomScale - 1f) / 2f
        val maxTy = height * (zoomScale - 1f) / 2f
        zoomTx = zoomTx.coerceIn(-maxTx, maxTx)
        zoomTy = zoomTy.coerceIn(-maxTy, maxTy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun startReaderThread() {
        if (readerThread != null) return
        readerThread = thread(start = true) {
            var connection: HttpURLConnection? = null
            var input: BufferedInputStream? = null

            try {
                val url = URL(streamUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.doInput = true
                connection.connect()
                
                if (connection.responseCode != 200) {
                    Log.e("MJPEG", "HTTP Fehler: ${connection.responseCode}")
                    isConnected = false
                    drawStatusText("Keine Vorschau\nHTTP ${connection.responseCode}")
                    return@thread
                }

                val contentType = connection.contentType
                Log.d("MJPEG", "Content-Type: $contentType")
                
                isConnected = true
                lastSuccessfulFrame = System.currentTimeMillis()

                input = BufferedInputStream(connection.inputStream, 8192)
                
                // Parse boundary aus Content-Type (z.B. "multipart/x-mixed-replace;boundary=myboundary")
                val boundary = contentType?.let {
                    val parts = it.split("boundary=")
                    if (parts.size > 1) "--${parts[1].trim()}" else null
                }
                
                Log.d("MJPEG", "Boundary: $boundary")

                if (boundary != null) {
                    parseMjpegWithBoundary(input, boundary)
                } else {
                    // Fallback: Versuche rohe JPEG-Frames zu parsen
                    parseRawJpegFrames(input)
                }

            } catch (e: Exception) {
                Log.e("MJPEG", "Stream error", e)
                isConnected = false
                drawStatusText("Keine Vorschau\n${e.message?.take(30) ?: "Verbindungsfehler"}")
            } finally {
                input?.close()
                connection?.disconnect()
                Log.d("MJPEG", "Stream beendet")
            }
        }
    }
    
    private fun startRetryMonitor() {
        retryThread?.interrupt()
        retryThread = thread(start = true) {
            while (running && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(retryInterval)
                    
                    // Prüfe ob Stream noch läuft (Frame in letzten 10 Sekunden)
                    val timeSinceLastFrame = System.currentTimeMillis() - lastSuccessfulFrame
                    if (timeSinceLastFrame > 10000 && !isConnected) {
                        Log.d("MJPEG", "Stream not connected, attempting reconnect...")
                        drawStatusText("Keine Vorschau\nVerbinde...")
                        
                        // Restart reader thread
                        readerThread?.interrupt()
                        readerThread = null
                        if (running && isAvailable) {
                            startReaderThread()
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }
    
    private fun drawStatusText(text: String) {
        if (!isAvailable || surfaceTexture == null) return
        
        try {
            val canvas = lockCanvas()
            canvas?.let {
                // Schwarzer Hintergrund
                it.drawColor(Color.BLACK)
                
                // Text zentriert
                val lines = text.split("\n")
                val y = it.height / 2f - (lines.size - 1) * 30f
                lines.forEachIndexed { index, line ->
                    it.drawText(line, it.width / 2f, y + index * 60f, statusPaint)
                }
                
                unlockCanvasAndPost(it)
            }
        } catch (e: Exception) {
            Log.d("MJPEG", "Could not draw status text: ${e.message}")
        }
    }

    private fun parseMjpegWithBoundary(input: BufferedInputStream, boundary: String) {
        val boundaryBytes = boundary.toByteArray()
        val buffer = ByteArray(65536)
        var frameBuffer = ByteArray(512 * 1024) // 512KB initial
        var framePos = 0
        var frameCount = 0

        while (running) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break

            for (i in 0 until bytesRead) {
                // Vergrößere Buffer wenn nötig
                if (framePos >= frameBuffer.size) {
                    frameBuffer = frameBuffer.copyOf(frameBuffer.size * 2)
                }
                
                frameBuffer[framePos++] = buffer[i]

                // Prüfe auf Boundary am Ende
                if (framePos >= boundaryBytes.size) {
                    var match = true
                    for (j in boundaryBytes.indices) {
                        if (frameBuffer[framePos - boundaryBytes.size + j] != boundaryBytes[j]) {
                            match = false
                            break
                        }
                    }
                    
                    if (match && framePos > boundaryBytes.size) {
                        // Frame gefunden
                        val frameData = frameBuffer.copyOfRange(0, framePos - boundaryBytes.size)
                        processFrame(frameData, ++frameCount)
                        framePos = 0
                    }
                }
            }
        }
    }

    private fun parseRawJpegFrames(input: BufferedInputStream) {
        val buffer = ByteArray(65536)
        var frameBuffer = ByteArray(512 * 1024) // 512KB initial
        var framePos = 0
        var inFrame = false
        var frameCount = 0

        while (running) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break

            for (i in 0 until bytesRead) {
                val b = buffer[i]
                
                if (!inFrame) {
                    // Suche JPEG SOI Marker: 0xFF 0xD8
                    if (framePos == 0 && b.toInt() and 0xFF == 0xFF) {
                        frameBuffer[framePos++] = b
                    } else if (framePos == 1 && b.toInt() and 0xFF == 0xD8) {
                        frameBuffer[framePos++] = b
                        inFrame = true
                    } else {
                        framePos = 0
                    }
                } else {
                    // Vergrößere Buffer wenn nötig
                    if (framePos >= frameBuffer.size) {
                        frameBuffer = frameBuffer.copyOf(frameBuffer.size * 2)
                    }
                    
                    frameBuffer[framePos++] = b
                    
                    // Suche JPEG EOI Marker: 0xFF 0xD9
                    if (framePos >= 2 &&
                        frameBuffer[framePos - 2].toInt() and 0xFF == 0xFF &&
                        frameBuffer[framePos - 1].toInt() and 0xFF == 0xD9
                    ) {
                        val jpegData = frameBuffer.copyOfRange(0, framePos)
                        processFrame(jpegData, ++frameCount)
                        framePos = 0
                        inFrame = false
                    }
                }
            }
        }
    }

    private fun processFrame(data: ByteArray, frameCount: Int) {
        // Synchronisiere den gesamten Zugriff um Race Conditions zu vermeiden
        synchronized(textureLock) {
            // Prüfe ZUERST ob die Texture noch verfügbar ist
            if (!isAvailable || !running || surfaceTexture == null) {
                return
            }
            
            // Frame-Skipping bei zu schneller Rate
            val now = System.currentTimeMillis()
            if (now - lastFrameTime < minFrameDelay) {
                return
            }
            lastFrameTime = now
        
        // Finde JPEG-Daten (überspringen von HTTP-Headern falls vorhanden)
        var jpegStart = 0
        for (i in 0 until data.size - 1) {
            if (data[i].toInt() and 0xFF == 0xFF && data[i + 1].toInt() and 0xFF == 0xD8) {
                jpegStart = i
                break
            }
        }

        if (jpegStart >= data.size) {
            return
        }

        val jpegData = if (jpegStart > 0) data.copyOfRange(jpegStart, data.size) else data
        
        try {
            // Berechne passende inSampleSize abhängig von der tatsächlichen View-Größe.
            // Verhindert volle 4K-Dekodierung wenn das View klein ist (z.B. BEIDE-Modus).
            val viewW = width.takeIf { it > 0 } ?: 1920
            val viewH = height.takeIf { it > 0 } ?: 1080
            val sizeOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, sizeOpts)
            var sampleSize = 1
            if (sizeOpts.outWidth > 0 && sizeOpts.outHeight > 0) {
                var hw = sizeOpts.outWidth / 2
                var hh = sizeOpts.outHeight / 2
                while (hw >= viewW && hh >= viewH) {
                    sampleSize *= 2
                    hw /= 2
                    hh /= 2
                }
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Weniger Speicher
            }

            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, options)
            if (bitmap != null) {
                if (frameCount % 60 == 0) {
                    Log.d("MJPEG", "Frame $frameCount: ${bitmap.width}x${bitmap.height}")
                }

                // Letzten Frame cachen + One-Shot-Callback feuern (für Auto-Tune)
                latestJpegBytes = jpegData
                val cb = onNextFrameCallback
                if (cb != null) {
                    onNextFrameCallback = null
                    cb(jpegData)
                }

                // Frame erfolgreich dekodiert
                lastSuccessfulFrame = System.currentTimeMillis()
                isConnected = true
                
                // Prüfe NOCHMAL ob SurfaceTexture noch verfügbar ist (doppelte Absicherung)
                if (isAvailable && running && surfaceTexture != null) {
                    try {
                        val canvas = lockCanvas()
                        canvas?.let {
                            try {
                                val cw = it.width.toFloat()
                                val ch = it.height.toFloat()
                                val s  = zoomScale
                                val tx = zoomTx
                                val ty = zoomTy
                                it.drawColor(Color.BLACK)
                                it.save()
                                it.translate(cw / 2f + tx, ch / 2f + ty)
                                it.scale(s, s)
                                // Bitmap zentriert auf (0,0) zeichnen, skaliert auf Canvas-Größe
                                it.drawBitmap(
                                    bitmap,
                                    android.graphics.Rect(0, 0, bitmap.width, bitmap.height),
                                    RectF(-cw / 2f, -ch / 2f, cw / 2f, ch / 2f),
                                    null
                                )
                                it.restore()
                                unlockCanvasAndPost(it)
                            } catch (e: Exception) {
                                Log.e("MJPEG", "Draw error", e)
                            }
                        }
                    } catch (e: Exception) {
                        // Surface ist nicht mehr verfügbar - einfach überspringen
                        Log.d("MJPEG", "Surface not available, skipping frame")
                    }
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            if (frameCount % 30 == 0) {
                Log.e("MJPEG", "Decode error frame $frameCount", e)
            }
        }
        } // End synchronized block
    }

    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        Log.d("MJPEG", "Texture available $width x $height")
        if (running) {
            startReaderThread()
            startRetryMonitor()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        Log.d("MJPEG", "Texture size changed $width x $height")
    }
    
    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
        // WICHTIG: false zurückgeben damit SurfaceTexture NICHT zerstört wird
        // Das ermöglicht, dass der Stream weiterlaufen kann wenn die View verschoben wird (z.B. Fullscreen)
        synchronized(textureLock) {
            Log.d("MJPEG", "Texture destroyed - keeping surface")
        }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
}
