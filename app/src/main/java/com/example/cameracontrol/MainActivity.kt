package com.example.cameracontrol

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cameracontrol.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import android.view.Gravity
import android.view.View

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private lateinit var systemMonitorService: SystemMonitorService
    private lateinit var fullscreenHelper: FullscreenHelper
    private lateinit var controlsOverlay: ControlsOverlayView

    private val camera1BaseUrl = "http://192.168.178.47:8000"
    private val camera2BaseUrl = "http://192.168.178.48:8000"

    private var camera1Online = false
    private var camera2Online = false

    private var camera1StreamRunning = false
    private var camera2StreamRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars()) // Status + Navigation Bar
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding.videoContainer1.startStream("$camera1BaseUrl/preview")
        binding.videoContainer2.startStream("$camera2BaseUrl/preview")

        // Controls overlay (hidden until needed) - MUST be created before fullscreenHelper
        controlsOverlay = ControlsOverlayView(this)
        controlsOverlay.visibility = View.GONE
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, 80, 24, 0)
        }
        binding.rootLayout.addView(controlsOverlay, lp)

        fullscreenHelper = FullscreenHelper(
            rootLayout = binding.rootLayout,
            onFullscreenChanged = { isFullscreen ->
                if (!isFullscreen) {
                    cameraController.clearTemporaryMode()
                }
            },
            additionalViews = listOf(
                binding.btnToggleControl, 
                binding.combinedControl, 
                binding.debugOverlay,
                controlsOverlay
            )
        )

        binding.videoContainer1.setOnClickListener {
            // Nur toggle wenn nicht bereits im Fullscreen (damit Klick im Fullscreen nicht wieder schließt)
            if (!fullscreenHelper.isInFullscreen()) {
                fullscreenHelper.toggleFullscreen(binding.videoFrame1)
                cameraController.setTemporaryMode(CameraController.ControlMode.CAMERA_1)
            }
        }
        binding.videoContainer2.setOnClickListener {
            // Nur toggle wenn nicht bereits im Fullscreen (damit Klick im Fullscreen nicht wieder schließt)
            if (!fullscreenHelper.isInFullscreen()) {
                fullscreenHelper.toggleFullscreen(binding.videoFrame2)
                cameraController.setTemporaryMode(CameraController.ControlMode.CAMERA_2)
            }
        }

        // Zentraler Settings-Button: öffnet Overlay für aktive Kamera(s)
        binding.btnSettings.setOnClickListener {
            val (urls, label, views) = when (cameraController.getControlMode()) {
                CameraController.ControlMode.CAMERA_1 -> Triple(
                    listOf(camera1BaseUrl), "Kamera 1",
                    listOf(binding.videoContainer1)
                )
                CameraController.ControlMode.CAMERA_2 -> Triple(
                    listOf(camera2BaseUrl), "Kamera 2",
                    listOf(binding.videoContainer2)
                )
                CameraController.ControlMode.BOTH -> Triple(
                    listOf(camera1BaseUrl, camera2BaseUrl), "Beide Kameras",
                    listOf(binding.videoContainer1, binding.videoContainer2)
                )
            }
            controlsOverlay.attach(cameraController, urls, label, views)
            controlsOverlay.visibility = View.VISIBLE
        }

        cameraController = CameraController(
            camera1BaseUrl = camera1BaseUrl,
            camera2BaseUrl = camera2BaseUrl,
            scope = lifecycleScope
        )

        systemMonitorService = SystemMonitorService(
            camera1BaseUrl = camera1BaseUrl,
            camera2BaseUrl = camera2BaseUrl,
            scope = lifecycleScope,
            onCamera1Data = { data ->
                binding.systemMonitor1.updateData(data)
            },
            onCamera2Data = { data ->
                binding.systemMonitor2.updateData(data)
            },
            onServerStatusChanged = { camera1Online, camera2Online ->
                updateUIBasedOnServerStatus(camera1Online, camera2Online)
                cameraController.updateServerStatus(camera1Online, camera2Online)
            }
        )
        systemMonitorService.start()

        binding.btnToggleRecording.setOnClickListener {
            toggleRecording()
        }
        
        binding.btnToggleControl.setOnClickListener {
            if (binding.combinedControl.visibility == android.view.View.VISIBLE) {
                // Ausblenden
                binding.combinedControl.visibility = android.view.View.GONE
                binding.invertYContainer.visibility = android.view.View.GONE
                binding.btnToggleControl.text = "🎮"
                binding.btnToggleControl.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#3498db")
                )
            } else {
                // Einblenden
                binding.combinedControl.visibility = android.view.View.VISIBLE
                binding.invertYContainer.visibility = android.view.View.VISIBLE
                binding.btnToggleControl.text = "✕"
                binding.btnToggleControl.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#e74c3c")
                )
            }
        }
        
        binding.btnShutdown.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Shutdown")
                .setMessage("Möchten Sie die ausgewählte(n) Kamera(s) wirklich herunterfahren?")
                .setPositiveButton("Ja") { _, _ ->
                    cameraController.shutdown { _, message ->
                        binding.debugOverlay.text = "SHUTDOWN:\n$message"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
        
        binding.btnReboot.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Reboot")
                .setMessage("Möchten Sie die ausgewählte(n) Kamera(s) wirklich neu starten?")
                .setPositiveButton("Ja") { _, _ ->
                    cameraController.reboot { _, message ->
                        binding.debugOverlay.text = "REBOOT:\n$message"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(500) // 0,5 Sekunden für schnelleres Feedback
                updateRecordingButton()
            }
        }

        binding.systemMonitor1.setOnClickListener {
            fullscreenHelper.toggleFullscreen(it)
        }
        binding.systemMonitor2.setOnClickListener {
            fullscreenHelper.toggleFullscreen(it)
        }

        binding.combinedControl.apply {
            minAngle = HardwareConfig.SERVO_MIN_ANGLE
            maxAngle = HardwareConfig.SERVO_MAX_ANGLE

            cameraController.getCurrentServoAngle { angle ->
                angle?.let { 
                    setAngle(it)
                    Log.d("MainActivity", "Loaded current servo angle: $it°")
                }
            }

            onStepperControl = { steps ->
                cameraController.processJoystick(steps, 0f)
                
                val mode = when(cameraController.getControlMode()) {
                    CameraController.ControlMode.CAMERA_1 -> "CAM1"
                    CameraController.ControlMode.CAMERA_2 -> "CAM2"
                    CameraController.ControlMode.BOTH -> "BEIDE"
                }
                
                if (steps == 0) {
                    binding.debugOverlay.text = "[$mode] Steuerung: STOP"
                } else {
                    val dir = if (steps > 0) "RECHTS →" else "← LINKS"
                    binding.debugOverlay.text = "[$mode] Stepper: $dir ($steps Steps/s)"
                }
            }

            onServoControl = { angle ->
                val mode = when(cameraController.getControlMode()) {
                    CameraController.ControlMode.CAMERA_1 -> "CAM1"
                    CameraController.ControlMode.CAMERA_2 -> "CAM2"
                    CameraController.ControlMode.BOTH -> "BEIDE"
                }
                
                binding.debugOverlay.text = """
                    |[$mode] SERVO
                    |━━━━━━━━━━━━━━━━━━━━
                    |Winkel: ${angle.toInt()}°
                    |API: /servo/absolute
                """.trimMargin()
                
                cameraController.setServoAngle(angle)
                
                Log.d("MainActivity", "Servo angle set to ${angle.toInt()}°")
            }
        }

        binding.camera1Button.apply {
            text = "Kamera 1"
            setOnClickListener {
                cameraController.setControlMode(CameraController.ControlMode.CAMERA_1)
                updateUIBasedOnServerStatus(camera1Online, camera2Online)
                cameraController.getCurrentServoAngle { angle ->
                    angle?.let { binding.combinedControl.setAngle(it) }
                }
                binding.switchInvertY.isChecked = cameraController.getInvertServo(camera1BaseUrl)
                if (controlsOverlay.visibility == View.VISIBLE) {
                    controlsOverlay.switchCamera(listOf(camera1BaseUrl), "Kamera 1", listOf(binding.videoContainer1))
                }
            }
        }
        
        binding.camera2Button.apply {
            text = "Kamera 2"
            setOnClickListener {
                cameraController.setControlMode(CameraController.ControlMode.CAMERA_2)
                updateUIBasedOnServerStatus(camera1Online, camera2Online)
                cameraController.getCurrentServoAngle { angle ->
                    angle?.let { binding.combinedControl.setAngle(it) }
                }
                binding.switchInvertY.isChecked = cameraController.getInvertServo(camera2BaseUrl)
                if (controlsOverlay.visibility == View.VISIBLE) {
                    controlsOverlay.switchCamera(listOf(camera2BaseUrl), "Kamera 2", listOf(binding.videoContainer2))
                }
            }
        }
        
        binding.bothCamerasButton.apply {
            text = "Beide Kameras"
            setOnClickListener {
                cameraController.setControlMode(CameraController.ControlMode.BOTH)
                updateUIBasedOnServerStatus(camera1Online, camera2Online)
                cameraController.getCurrentServoAngle { angle ->
                    angle?.let { binding.combinedControl.setAngle(it) }
                }
                binding.switchInvertY.isChecked = cameraController.getInvertServo(camera1BaseUrl)
            }
        }

        // Y-Achse invertieren: initialer Zustand + Listener
        binding.switchInvertY.isChecked = cameraController.getInvertServo(camera1BaseUrl)
        binding.switchInvertY.setOnCheckedChangeListener { _, isChecked ->
            when (cameraController.getControlMode()) {
                CameraController.ControlMode.CAMERA_1 -> cameraController.setInvertServo(camera1BaseUrl, isChecked)
                CameraController.ControlMode.CAMERA_2 -> cameraController.setInvertServo(camera2BaseUrl, isChecked)
                CameraController.ControlMode.BOTH -> {
                    cameraController.setInvertServo(camera1BaseUrl, isChecked)
                    cameraController.setInvertServo(camera2BaseUrl, isChecked)
                }
            }
        }

        // Ziehbarer Divider zwischen Buttons und Graphen
        var naturalPreviewHeight = 0
        var dividerDragStartY = 0f
        var dividerDragStartPreviewH = 0

        // Nach erstem Layout: Höhe der Preview-Section auf exaktes 16:9 setzen
        binding.previewSection.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (binding.previewSection.width > 0) {
                        binding.previewSection.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val eachFrameWidth = binding.previewSection.width / 2
                        naturalPreviewHeight = (eachFrameWidth * 9.0 / 16.0).toInt()
                        val lp = binding.previewSection.layoutParams as android.widget.LinearLayout.LayoutParams
                        lp.height = naturalPreviewHeight
                        lp.weight = 0f
                        binding.previewSection.layoutParams = lp
                    }
                }
            }
        )

        binding.dragDivider.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dividerDragStartY = event.rawY
                    dividerDragStartPreviewH = binding.previewSection.height
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - dividerDragStartY
                    val minH = (80 * resources.displayMetrics.density).toInt()
                    val maxH = if (naturalPreviewHeight > 0) naturalPreviewHeight else dividerDragStartPreviewH
                    val newH = (dividerDragStartPreviewH + delta.toInt()).coerceIn(minH, maxH)
                    val lp = binding.previewSection.layoutParams as android.widget.LinearLayout.LayoutParams
                    lp.height = newH
                    lp.weight = 0f
                    binding.previewSection.layoutParams = lp
                    true
                }
                else -> false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenHelper.isInFullscreen()) {
                    fullscreenHelper.exitFullscreenIfActive()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoContainer1.stopStream()
        binding.videoContainer2.stopStream()
        cameraController.cleanup()
        systemMonitorService.stop()
    }

    private fun toggleRecording() {
        cameraController.retrieveRecordingStatus { isRecording ->
            if (isRecording) {
                cameraController.stopRecording { _, message ->
                    binding.debugOverlay.text = "AUFNAHME STOP:\n$message"
                    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    updateRecordingButton()
                }
            } else {
                cameraController.startRecording { _, message ->
                    binding.debugOverlay.text = "AUFNAHME START:\n$message"
                    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    updateRecordingButton()
                }
            }
        }
    }
    
    private fun updateRecordingButton() {
        cameraController.retrieveRecordingStatus { isRecording ->
            if (isRecording) {
                binding.btnToggleRecording.text = "Stop Aufnahme"
                binding.btnToggleRecording.backgroundTintList = 
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#c0392b"))
            } else {
                binding.btnToggleRecording.text = "Start Aufnahme"
                binding.btnToggleRecording.backgroundTintList = 
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#27ae60"))
            }
            
            // Update Recording-Indikatoren
            updateRecordingIndicators()
        }
    }
    
    private fun updateRecordingIndicators() {
        updateCameraRecordingIndicator(camera1BaseUrl, binding.recordingIndicator1, 1)
        updateCameraRecordingIndicator(camera2BaseUrl, binding.recordingIndicator2, 2)
    }

    /**
     * Fragt den Detailstatus einer Kamera ab und aktualisiert den Aufnahmeindikator.
     *
     * Zustände:
     * - Voll gesund (isRecording=true, kein Fehler) → rot blinkend
     * - Aufnahme aktiv aber Audio-Problem          → orange blinkend + Warnung
     * - Aufnahme-Fehler (Thread tot / capture_error) → orange statisch + Fehler im Debug-Overlay
     * - Nicht aufnehmend                           → Indikator ausgeblendet
     */
    private fun updateCameraRecordingIndicator(
        baseUrl: String,
        indicator: android.view.View,
        camNumber: Int
    ) {
        cameraController.retrieveDetailedStatus(baseUrl) { status ->
            if (status == null) {
                // Server nicht erreichbar
                indicator.clearAnimation()
                indicator.visibility = android.view.View.GONE
                return@retrieveDetailedStatus
            }

            when {
                status.isFullyHealthy && !status.hasAudioProblem -> {
                    // Alles OK: rot blinkend
                    indicator.background = recordingBorderDrawable(android.graphics.Color.parseColor("#e74c3c"))
                    indicator.visibility = android.view.View.VISIBLE
                    if (indicator.animation == null) {
                        val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink)
                        indicator.startAnimation(anim)
                    }
                }

                status.isRecording && status.hasAudioProblem -> {
                    // Aufnahme läuft, aber Audio fehlt: orange blinkend
                    indicator.background = recordingBorderDrawable(android.graphics.Color.parseColor("#e67e22"))
                    indicator.visibility = android.view.View.VISIBLE
                    if (indicator.animation == null) {
                        val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink)
                        indicator.startAnimation(anim)
                    }
                    val errorText = status.audioError ?: "Kein Audio-Device"
                    Log.w("MainActivity", "CAM$camNumber Audio-Problem: $errorText")
                    binding.debugOverlay.text =
                        "⚠️ CAM$camNumber AUDIO FEHLER\n━━━━━━━━━━━━━━━━━━━━\n$errorText"
                }

                !status.isRecording && status.errorSummary != null -> {
                    // Aufnahme-Fehler: orange statisch, Fehler anzeigen
                    indicator.background = recordingBorderDrawable(android.graphics.Color.parseColor("#e67e22"))
                    indicator.clearAnimation()
                    indicator.visibility = android.view.View.VISIBLE
                    Log.e("MainActivity", "CAM$camNumber Aufnahme-Fehler: ${status.errorSummary}")
                    binding.debugOverlay.text =
                        "🔴 CAM$camNumber AUFNAHME FEHLER\n━━━━━━━━━━━━━━━━━━━━\n${status.errorSummary}"
                }

                else -> {
                    // Nicht aufnehmend, kein Fehler
                    indicator.clearAnimation()
                    indicator.visibility = android.view.View.GONE
                }
            }
        }
    }
    
    private fun recordingBorderDrawable(color: Int): android.graphics.drawable.GradientDrawable {
        val strokePx = (8 * resources.displayMetrics.density).toInt()
        val cornerPx = 4 * resources.displayMetrics.density
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(strokePx, color)
            cornerRadius = cornerPx
        }
    }

    private fun updateUIBasedOnServerStatus(camera1Online: Boolean, camera2Online: Boolean) {
        this.camera1Online = camera1Online
        this.camera2Online = camera2Online
        
        val currentMode = cameraController.getControlMode()
        
        Log.d("MainActivity", "updateUIBasedOnServerStatus: CAM1=$camera1Online, CAM2=$camera2Online, mode=$currentMode")

        val canControl = when (currentMode) {
            CameraController.ControlMode.CAMERA_1 -> camera1Online
            CameraController.ControlMode.CAMERA_2 -> camera2Online
            CameraController.ControlMode.BOTH -> camera1Online && camera2Online
        }
        
        Log.d("MainActivity", "canControl=$canControl (based on mode=$currentMode)")

        binding.combinedControl.isEnabled = canControl
        binding.btnToggleRecording.isEnabled = canControl
        binding.btnShutdown.isEnabled = canControl
        binding.btnReboot.isEnabled = canControl

        val alpha = if (canControl) 1.0f else 0.3f
        binding.combinedControl.alpha = alpha
        binding.btnToggleRecording.alpha = alpha
        binding.btnShutdown.alpha = alpha
        binding.btnReboot.alpha = alpha

        binding.camera1Button.isEnabled = camera1Online
        binding.camera2Button.isEnabled = camera2Online
        binding.bothCamerasButton.isEnabled = camera1Online && camera2Online

        if (currentMode == CameraController.ControlMode.CAMERA_1) {
            binding.camera1Button.alpha = 1.0f
            binding.camera2Button.alpha = if (camera2Online) 0.7f else 0.3f
            binding.bothCamerasButton.alpha = if (camera1Online && camera2Online) 0.7f else 0.3f
        } else if (currentMode == CameraController.ControlMode.CAMERA_2) {
            binding.camera1Button.alpha = if (camera1Online) 0.7f else 0.3f
            binding.camera2Button.alpha = 1.0f
            binding.bothCamerasButton.alpha = if (camera1Online && camera2Online) 0.7f else 0.3f
        } else {
            binding.camera1Button.alpha = if (camera1Online) 0.7f else 0.3f
            binding.camera2Button.alpha = if (camera2Online) 0.7f else 0.3f
            binding.bothCamerasButton.alpha = 1.0f
        }

        if (!canControl) {
            val modeStr = when (currentMode) {
                CameraController.ControlMode.CAMERA_1 -> "KAMERA 1"
                CameraController.ControlMode.CAMERA_2 -> "KAMERA 2"
                CameraController.ControlMode.BOTH -> "BEIDE KAMERAS"
            }
            binding.debugOverlay.text = """
                |⚠️ $modeStr OFFLINE ⚠️
                |━━━━━━━━━━━━━━━━━━━━
                |CAM1: ${if (camera1Online) "✓ ONLINE" else "✗ OFFLINE"}
                |CAM2: ${if (camera2Online) "✓ ONLINE" else "✗ OFFLINE"}
                |━━━━━━━━━━━━━━━━━━━━
                |Steuerung deaktiviert
                |Wähle eine online Kamera
            """.trimMargin()
        } else {
            // Show online status when both are available
            binding.debugOverlay.text = """
                |━━━━━━━━━━━━━━━━━━━━
                |CAM1: ${if (camera1Online) "✓ ONLINE" else "✗ OFFLINE"}
                |CAM2: ${if (camera2Online) "✓ ONLINE" else "✗ OFFLINE"}
            """.trimMargin()
        }

        if (!camera1Online && camera1StreamRunning) {
            Log.d("MainActivity", "Stopping CAM1 stream (offline)")
            binding.videoContainer1.stopStream()
            camera1StreamRunning = false
        } else if (camera1Online && !camera1StreamRunning) {
            Log.d("MainActivity", "Starting CAM1 stream (online)")
            binding.videoContainer1.startStream("$camera1BaseUrl/preview")
            camera1StreamRunning = true
        }
        
        if (!camera2Online && camera2StreamRunning) {
            Log.d("MainActivity", "Stopping CAM2 stream (offline)")
            binding.videoContainer2.stopStream()
            camera2StreamRunning = false
        } else if (camera2Online && !camera2StreamRunning) {
            Log.d("MainActivity", "Starting CAM2 stream (online)")
            binding.videoContainer2.startStream("$camera2BaseUrl/preview")
            camera2StreamRunning = true
        }
    }
}
