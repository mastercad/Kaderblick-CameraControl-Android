package com.kaderblick.cameracontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kaderblick.cameracontrol.databinding.ActivityMainBinding

/**
 * Main Activity for Kaderblick Dual Camera Wireless Control
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: DualCameraController
    private var networkClient: CameraNetworkClient? = null
    private var isRecording = false
    private var isConnected = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize camera controller
        cameraController = DualCameraController(this)

        // Request permissions
        if (allPermissionsGranted()) {
            initializeCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupUI()
    }

    private fun setupUI() {
        // Connect button
        binding.connectButton.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                connect()
            }
        }

        // Camera selector radio buttons
        binding.cameraSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.selectCamera1 -> {
                    cameraController.switchMode(DualCameraController.CameraMode.CAMERA_1)
                    startCameraPreview()
                }
                R.id.selectCamera2 -> {
                    cameraController.switchMode(DualCameraController.CameraMode.CAMERA_2)
                    startCameraPreview()
                }
                R.id.selectBothCameras -> {
                    cameraController.switchMode(DualCameraController.CameraMode.BOTH)
                    startCameraPreview()
                }
            }
        }

        // Capture button
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        // Record button
        binding.recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // Switch camera button
        binding.switchCameraButton.setOnClickListener {
            switchCamera()
        }
    }

    private fun initializeCamera() {
        cameraController.initialize(object : DualCameraController.CameraCallback {
            override fun onCameraReady() {
                runOnUiThread {
                    startCameraPreview()
                }
            }

            override fun onCameraError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun startCameraPreview() {
        cameraController.startPreview(
            binding.camera1Preview,
            binding.camera2Preview,
            this
        )
    }

    private fun connect() {
        val ipAddress = binding.cameraIpInput.text.toString().trim()
        
        if (ipAddress.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_ip_address), Toast.LENGTH_SHORT).show()
            return
        }

        binding.statusText.text = getString(R.string.status_connecting)
        binding.connectButton.isEnabled = false

        val baseUrl = "http://$ipAddress:8080"
        networkClient = CameraNetworkClient(baseUrl)

        networkClient?.testConnection(object : CameraNetworkClient.CameraCallback {
            override fun onSuccess(response: String) {
                runOnUiThread {
                    isConnected = true
                    binding.statusText.text = getString(R.string.status_connected, ipAddress)
                    binding.connectButton.text = getString(R.string.disconnect)
                    binding.connectButton.isEnabled = true
                    enableControls(true)
                    Toast.makeText(this@MainActivity, getString(R.string.connected_successfully), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    binding.statusText.text = getString(R.string.status_disconnected)
                    binding.connectButton.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.error_connection, error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun disconnect() {
        isConnected = false
        networkClient = null
        binding.statusText.text = getString(R.string.status_disconnected)
        binding.connectButton.text = getString(R.string.connect)
        enableControls(false)
        Toast.makeText(this, getString(R.string.disconnected_message), Toast.LENGTH_SHORT).show()
    }

    private fun enableControls(enabled: Boolean) {
        binding.captureButton.isEnabled = enabled
        binding.recordButton.isEnabled = enabled
        binding.switchCameraButton.isEnabled = enabled
    }

    private fun capturePhoto() {
        val cameraId = when (cameraController.currentMode) {
            DualCameraController.CameraMode.CAMERA_1 -> 1
            DualCameraController.CameraMode.CAMERA_2 -> 2
            DualCameraController.CameraMode.BOTH -> 0 // Both cameras
        }

        // Local camera capture
        cameraController.capturePhoto { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.photo_captured_locally), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Network command to wireless camera
        networkClient?.capturePhoto(cameraId, object : CameraNetworkClient.CameraCallback {
            override fun onSuccess(response: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.photo_captured_wireless), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.wireless_capture_error, error), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun startRecording() {
        val cameraId = when (cameraController.currentMode) {
            DualCameraController.CameraMode.CAMERA_1 -> 1
            DualCameraController.CameraMode.CAMERA_2 -> 2
            DualCameraController.CameraMode.BOTH -> 0
        }

        cameraController.startRecording { success, message ->
            if (success) {
                runOnUiThread {
                    isRecording = true
                    binding.recordButton.text = getString(R.string.stop_recording)
                    Toast.makeText(this, getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                }
            }
        }

        networkClient?.startRecording(cameraId, object : CameraNetworkClient.CameraCallback {
            override fun onSuccess(response: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.wireless_recording_started), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                // Silent fail for network recording
            }
        })
    }

    private fun stopRecording() {
        val cameraId = when (cameraController.currentMode) {
            DualCameraController.CameraMode.CAMERA_1 -> 1
            DualCameraController.CameraMode.CAMERA_2 -> 2
            DualCameraController.CameraMode.BOTH -> 0
        }

        cameraController.stopRecording { success, message ->
            if (success) {
                runOnUiThread {
                    isRecording = false
                    binding.recordButton.text = getString(R.string.start_recording)
                    Toast.makeText(this, getString(R.string.recording_stopped), Toast.LENGTH_SHORT).show()
                }
            }
        }

        networkClient?.stopRecording(cameraId, object : CameraNetworkClient.CameraCallback {
            override fun onSuccess(response: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.wireless_recording_stopped), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                // Silent fail for network recording
            }
        })
    }

    private fun switchCamera() {
        val newMode = when (cameraController.currentMode) {
            DualCameraController.CameraMode.CAMERA_1 -> {
                binding.selectCamera2.isChecked = true
                DualCameraController.CameraMode.CAMERA_2
            }
            DualCameraController.CameraMode.CAMERA_2 -> {
                binding.selectBothCameras.isChecked = true
                DualCameraController.CameraMode.BOTH
            }
            DualCameraController.CameraMode.BOTH -> {
                binding.selectCamera1.isChecked = true
                DualCameraController.CameraMode.CAMERA_1
            }
        }

        cameraController.switchMode(newMode)
        startCameraPreview()

        val cameraId = when (newMode) {
            DualCameraController.CameraMode.CAMERA_1 -> 1
            DualCameraController.CameraMode.CAMERA_2 -> 2
            DualCameraController.CameraMode.BOTH -> 0
        }

        networkClient?.switchCamera(cameraId, object : CameraNetworkClient.CameraCallback {
            override fun onSuccess(response: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, getString(R.string.camera_switched), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                // Silent fail for network switch
            }
        })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_camera_permission),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.shutdown()
    }
}
