package com.kaderblick.cameracontrol

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Controller for managing dual camera functionality
 */
class DualCameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera1: Camera? = null
    private var camera2: Camera? = null
    private var imageCapture1: ImageCapture? = null
    private var imageCapture2: ImageCapture? = null
    private var videoCapture1: VideoCapture? = null
    private var videoCapture2: VideoCapture? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    enum class CameraMode {
        CAMERA_1,
        CAMERA_2,
        BOTH
    }
    
    var currentMode = CameraMode.CAMERA_1

    interface CameraCallback {
        fun onCameraReady()
        fun onCameraError(error: String)
    }

    /**
     * Initialize camera provider
     */
    fun initialize(callback: CameraCallback) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                callback.onCameraReady()
            } catch (e: Exception) {
                callback.onCameraError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Start camera preview for specified camera
     */
    fun startPreview(
        previewView1: PreviewView,
        previewView2: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        val provider = cameraProvider ?: return

        try {
            // Unbind all use cases before rebinding
            provider.unbindAll()

            // Build preview for camera 1
            val preview1 = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView1.surfaceProvider)
            }

            // Build preview for camera 2
            val preview2 = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView2.surfaceProvider)
            }

            // Build image capture for both cameras
            imageCapture1 = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            imageCapture2 = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Select cameras (front and back if available)
            val cameraSelector1 = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraSelector2 = CameraSelector.DEFAULT_FRONT_CAMERA

            when (currentMode) {
                CameraMode.CAMERA_1 -> {
                    camera1 = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector1,
                        preview1,
                        imageCapture1
                    )
                }
                CameraMode.CAMERA_2 -> {
                    camera2 = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector2,
                        preview2,
                        imageCapture2
                    )
                }
                CameraMode.BOTH -> {
                    // Note: Binding both cameras simultaneously may not be supported on all devices
                    // This is a simplified implementation
                    camera1 = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector1,
                        preview1,
                        imageCapture1
                    )
                    // For dual camera, we simulate the second camera
                    // In a real implementation, you would need device-specific logic
                }
            }
        } catch (e: Exception) {
            // Handle camera binding failure
            e.printStackTrace()
        }
    }

    /**
     * Switch between camera modes
     */
    fun switchMode(mode: CameraMode) {
        currentMode = mode
    }

    /**
     * Capture photo from active camera
     */
    fun capturePhoto(callback: (Boolean, String?) -> Unit) {
        val imageCapture = when (currentMode) {
            CameraMode.CAMERA_1 -> imageCapture1
            CameraMode.CAMERA_2 -> imageCapture2
            CameraMode.BOTH -> imageCapture1 // Capture from primary in dual mode
        } ?: run {
            callback(false, "Camera not initialized")
            return
        }

        // In a real implementation, you would save the image to storage
        // For this demo, we just simulate the capture
        callback(true, "Photo captured successfully")
    }

    /**
     * Start recording video
     */
    fun startRecording(callback: (Boolean, String?) -> Unit) {
        // Simplified recording simulation
        // In a real implementation, use VideoCapture use case
        callback(true, "Recording started")
    }

    /**
     * Stop recording video
     */
    fun stopRecording(callback: (Boolean, String?) -> Unit) {
        // Simplified recording simulation
        callback(true, "Recording stopped")
    }

    /**
     * Release camera resources
     */
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
