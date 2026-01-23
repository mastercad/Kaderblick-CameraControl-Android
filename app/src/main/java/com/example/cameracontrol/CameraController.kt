package com.example.cameracontrol

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CameraController(
    private val camera1BaseUrl: String,
    private val camera2BaseUrl: String,
    private val scope: CoroutineScope
) {
    enum class ControlMode {
        CAMERA_1, CAMERA_2, BOTH
    }
    
    // Control-Modi: 0 = Kamera 1, 1 = Kamera 2, 2 = Beide
    private var controlMode = ControlMode.CAMERA_1
    private var temporaryMode: ControlMode? = null // Temporärer Override (z.B. im Fullscreen)
    
    // Aktuell aktiver Modus (berücksichtigt temporary override)
    private val activeMode: ControlMode
        get() = temporaryMode ?: controlMode
    
    // Konfiguration
    var stepperDelay = HardwareConfig.STEPPER_DEFAULT_DELAY
    var servoThreshold = HardwareConfig.SERVO_THRESHOLD // Mindestschwelle gegen Zucken
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .writeTimeout(1500, TimeUnit.MILLISECONDS)
        .build()
    
    // Separater Client mit längerem Timeout für Status-Abfragen
    private val statusClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()
    
    // Separater Client für Recording-Operationen (kann länger dauern)
    private val recordingClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    // Letzte gesendete Werte für Throttling
    private var lastStepperValue = 0
    private var lastServoValue = 0f
    private var lastStepperTime = 0L
    private var lastServoTime = 0L
    private val stepperInterval = HardwareConfig.STEPPER_UPDATE_INTERVAL
    private val servoInterval = HardwareConfig.SERVO_UPDATE_INTERVAL
    
    // Request-Blocking: Verhindert parallele Requests
    private var isStepperRequestRunning = false
    private var isServoRequestRunning = false
    
    // Für absoluten Servo-Winkel
    private var lastAbsoluteServoAngle = -1
    private var isSettingServoAngle = false
    
    // Server-Status (von SystemMonitor aktualisiert)
    private var camera1Online = false
    private var camera2Online = false
    
    // Server-Status aktualisieren (von MainActivity aufgerufen)
    fun updateServerStatus(cam1Online: Boolean, cam2Online: Boolean) {
        camera1Online = cam1Online
        camera2Online = cam2Online
        Log.d("CameraController", "Server status updated: CAM1=$cam1Online, CAM2=$cam2Online")
    }
    
    // Setze temporären Modus (z.B. im Fullscreen)
    fun setTemporaryMode(mode: ControlMode) {
        temporaryMode = mode
        Log.d("CameraController", "Temporary mode set to: $mode")
    }
    
    // Lösche temporären Modus und kehre zum normalen Modus zurück
    fun clearTemporaryMode() {
        temporaryMode = null
        Log.d("CameraController", "Temporary mode cleared, back to: $controlMode")
    }
    
    // Setze dauerhaften Modus (z.B. durch Button)
    fun setControlMode(mode: ControlMode) {
        controlMode = mode
        Log.d("CameraController", "Control mode set to: $mode")
    }
    
    // Getter für aktuellen Modus (für Debug-Anzeige)
    fun getControlMode(): ControlMode = activeMode
    
    // Hole aktuelle Servo-Position vom Server
    fun getCurrentServoAngle(onResult: (Float?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val baseUrl = when (activeMode) {
                    ControlMode.CAMERA_1 -> camera1BaseUrl
                    ControlMode.CAMERA_2 -> camera2BaseUrl
                    ControlMode.BOTH -> camera1BaseUrl // Bei BOTH: Kamera 1 nehmen
                }
                
                val request = Request.Builder()
                    .url("$baseUrl/servo")
                    .get()
                    .build()
                
                statusClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val angle = json.optDouble("angle", 90.0).toFloat()
                        
                        // Callback auf Main-Thread
                        scope.launch(Dispatchers.Main) {
                            onResult(angle)
                        }
                    } else {
                        scope.launch(Dispatchers.Main) {
                            onResult(null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraController", "Error getting servo angle: ${e.message}")
                scope.launch(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }
    
    fun processJoystick(xSpeed: Int, ySpeed: Float) {
        // Prüfen ob Server(s) erreichbar sind
        val canSend = when (activeMode) {
            ControlMode.CAMERA_1 -> camera1Online
            ControlMode.CAMERA_2 -> camera2Online
            ControlMode.BOTH -> camera1Online && camera2Online
        }
        
        if (!canSend) {
            Log.d("CameraController", "Skipping control - server(s) offline")
            return
        }
        
        val now = System.currentTimeMillis()
        
        // Stepper-Steuerung (X-Achse) - alle 100ms wie in Python
        if (xSpeed != 0) {
            if (now - lastStepperTime >= stepperInterval || xSpeed != lastStepperValue) {
                lastStepperTime = now
                lastStepperValue = xSpeed
                
                when (activeMode) {
                    ControlMode.CAMERA_1 -> controlStepper(camera1BaseUrl, xSpeed)
                    ControlMode.CAMERA_2 -> controlStepper(camera2BaseUrl, xSpeed)
                    ControlMode.BOTH -> {
                        // Beide Kameras parallel, aber entgegengesetzt
                        controlStepper(camera1BaseUrl, xSpeed)
                        controlStepper(camera2BaseUrl, -xSpeed)
                    }
                }
            }
        } else {
            lastStepperValue = 0
        }
        
        // Servo-Steuerung (Y-Achse) - alle 300ms wie in Python (langsamer!)
        // und nur wenn >= Schwelle (2 Grad)
        if (abs(ySpeed) >= servoThreshold) {
            if (now - lastServoTime >= servoInterval || abs(ySpeed - lastServoValue) > 0.5f) {
                lastServoTime = now
                lastServoValue = ySpeed
                
                when (activeMode) {
                    ControlMode.CAMERA_1 -> controlServo(camera1BaseUrl, -ySpeed) // Invertiert
                    ControlMode.CAMERA_2 -> controlServo(camera2BaseUrl, ySpeed)
                    ControlMode.BOTH -> {
                        // Beide Kameras parallel
                        controlServo(camera1BaseUrl, -ySpeed)
                        controlServo(camera2BaseUrl, ySpeed)
                    }
                }
            }
        } else {
            lastServoValue = 0f
        }
    }
    
    private fun controlStepper(baseUrl: String, steps: Int) {
        // Blockiere wenn bereits ein Request läuft
        if (isStepperRequestRunning) {
            Log.d("CameraController", "Stepper request skipped - already running")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                isStepperRequestRunning = true
                
                val json = JSONObject().apply {
                    put("steps", steps)
                    put("delay", stepperDelay)
                }
                
                val request = Request.Builder()
                    .url("$baseUrl/stepper")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CameraController", "Stepper request failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.d("CameraController", "Stepper error: ${e.message}")
            } finally {
                isStepperRequestRunning = false
            }
        }
    }
    
    private fun controlServo(baseUrl: String, speed: Float) {
        // Blockiere wenn bereits ein Request läuft
        if (isServoRequestRunning) {
            Log.d("CameraController", "Servo request skipped - already running")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                isServoRequestRunning = true
                
                val degrees = speed.toInt()
                val json = JSONObject().apply {
                    put("degrees", degrees)
                }
                
                val request = Request.Builder()
                    .url("$baseUrl/servo")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CameraController", "Servo request failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // Silently fail wie im Python-Code
                Log.d("CameraController", "Servo error: ${e.message}")
            } finally {
                isServoRequestRunning = false
            }
        }
    }
    
    // Setze Servo auf absoluten Winkel (0-180°)
    fun setServoAngle(angle: Float) {
        // Prüfen ob Server(s) erreichbar sind
        val canSend = when (activeMode) {
            ControlMode.CAMERA_1 -> camera1Online
            ControlMode.CAMERA_2 -> camera2Online
            ControlMode.BOTH -> camera1Online && camera2Online
        }
        
        if (!canSend) {
            Log.d("CameraController", "Skipping servo angle - server(s) offline")
            return
        }
        
        val angleInt = angle.toInt().coerceIn(
            HardwareConfig.SERVO_MIN_ANGLE.toInt(),
            HardwareConfig.SERVO_MAX_ANGLE.toInt()
        )
        
        // Verhindere mehrfache/redundante Calls
        if (isSettingServoAngle || angleInt == lastAbsoluteServoAngle) {
            Log.d("CameraController", "Skipping servo angle $angleInt° (already setting or same)")
            return
        }
        
        isSettingServoAngle = true
        lastAbsoluteServoAngle = angleInt
        
        scope.launch(Dispatchers.IO) {
            try {
                when (activeMode) {
                    ControlMode.CAMERA_1 -> setServoAngleForCamera(camera1BaseUrl, angleInt)
                    ControlMode.CAMERA_2 -> setServoAngleForCamera(camera2BaseUrl, angleInt)
                    ControlMode.BOTH -> {
                        // Beide Kameras auf gleichen Winkel setzen
                        setServoAngleForCamera(camera1BaseUrl, angleInt)
                        setServoAngleForCamera(camera2BaseUrl, angleInt)
                    }
                }
                
                Log.d("CameraController", "✓ Servo angle set to $angleInt° (mode: $activeMode)")
            } catch (e: Exception) {
                Log.e("CameraController", "Error setting servo angle: ${e.message}")
            } finally {
                // Nach kurzer Verzögerung wieder freigeben
                kotlinx.coroutines.delay(500)
                isSettingServoAngle = false
            }
        }
    }
    
    private suspend fun setServoAngleForCamera(baseUrl: String, angle: Int) {
        try {
            val json = JSONObject().apply {
                put("angle", angle)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/servo/absolute")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CameraController", "Absolute servo request failed: ${response.code}")
                } else {
                    Log.d("CameraController", "✓ Servo $baseUrl set to $angle°")
                }
            }
        } catch (e: Exception) {
            Log.d("CameraController", "Absolute servo error: ${e.message}")
        }
    }
    
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
        statusClient.dispatcher.executorService.shutdown()
        recordingClient.dispatcher.executorService.shutdown()
    }
    
    // === AUFNAHME-STEUERUNG ===
    
    fun startRecording(onResult: (success: Boolean, message: String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val cameras = when (activeMode) {
                ControlMode.CAMERA_1 -> listOf(camera1BaseUrl)
                ControlMode.CAMERA_2 -> listOf(camera2BaseUrl)
                ControlMode.BOTH -> listOf(camera1BaseUrl, camera2BaseUrl)
            }
            
            var allSuccess = true
            val messages = mutableListOf<String>()
            
            cameras.forEach { baseUrl ->
                try {
                    val url = "$baseUrl/start_record"
                    Log.d("CameraController", "Starting recording on: $url")
                    
                    val request = Request.Builder()
                        .url(url)
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    
                    recordingClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        Log.d("CameraController", "Response code: ${response.code}, body: $responseBody")
                        
                        if (response.isSuccessful) {
                            messages.add("✓ Aufnahme gestartet")
                        } else {
                            allSuccess = false
                            messages.add("✗ Fehler ${response.code}: $responseBody")
                        }
                    }
                } catch (e: Exception) {
                    allSuccess = false
                    val errorMsg = "✗ Fehler: ${e.javaClass.simpleName}: ${e.message}"
                    messages.add(errorMsg)
                    Log.e("CameraController", "Start recording error", e)
                }
            }
            
            scope.launch(Dispatchers.Main) {
                onResult(allSuccess, messages.joinToString("\n"))
            }
        }
    }
    
    fun stopRecording(onResult: (success: Boolean, message: String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val cameras = when (activeMode) {
                ControlMode.CAMERA_1 -> listOf(camera1BaseUrl)
                ControlMode.CAMERA_2 -> listOf(camera2BaseUrl)
                ControlMode.BOTH -> listOf(camera1BaseUrl, camera2BaseUrl)
            }
            
            var allSuccess = true
            val messages = mutableListOf<String>()
            
            cameras.forEach { baseUrl ->
                try {
                    val url = "$baseUrl/stop_record"
                    Log.d("CameraController", "Stopping recording on: $url")
                    
                    val request = Request.Builder()
                        .url(url)
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    
                    recordingClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        Log.d("CameraController", "Response code: ${response.code}, body: $responseBody")
                        
                        if (response.isSuccessful) {
                            messages.add("✓ Aufnahme gestoppt")
                        } else {
                            allSuccess = false
                            messages.add("✗ Fehler ${response.code}: $responseBody")
                        }
                    }
                } catch (e: Exception) {
                    allSuccess = false
                    val errorMsg = "✗ Fehler: ${e.javaClass.simpleName}: ${e.message}"
                    messages.add(errorMsg)
                    Log.e("CameraController", "Stop recording error", e)
                }
            }
            
            scope.launch(Dispatchers.Main) {
                onResult(allSuccess, messages.joinToString("\n"))
            }
        }
    }
    
    fun retrieveRecordingStatus(onResult: (isRecording: Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val baseUrl = when (activeMode) {
                ControlMode.CAMERA_1 -> camera1BaseUrl
                ControlMode.CAMERA_2 -> camera2BaseUrl
                ControlMode.BOTH -> camera1BaseUrl // Bei BOTH: Kamera 1 prüfen
            }
            
            retrieveRecordingStatus(baseUrl, onResult)
        }
    }
    
    fun retrieveRecordingStatus(baseUrl: String, onResult: (isRecording: Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/status")
                    .get()
                    .build()
                
                statusClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val recording = json.optJSONObject("recording")
                        val isActive = recording?.optBoolean("active", false) ?: false
                        
                        scope.launch(Dispatchers.Main) {
                            onResult(isActive)
                        }
                    } else {
                        scope.launch(Dispatchers.Main) {
                            onResult(false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraController", "Error getting recording status: ${e.message}")
                scope.launch(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    // Fetch camera controls JSON from /camera/controls
    fun fetchControls(baseUrl: String, onResult: (controlsJson: JSONObject?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/camera/controls")
                    .get()
                    .build()

                statusClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        scope.launch(Dispatchers.Main) { onResult(json) }
                    } else {
                        scope.launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraController", "fetchControls error: ${e.message}")
                scope.launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    // Set a single control: POST {control,value} to /camera/controls
    fun setControl(baseUrl: String, control: String, value: Number, onResult: (success: Boolean) -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("control", control)
                    put("value", value)
                }
                val request = Request.Builder()
                    .url("$baseUrl/camera/controls")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    scope.launch(Dispatchers.Main) { onResult(response.isSuccessful) }
                }
            } catch (e: Exception) {
                Log.e("CameraController", "setControl error: ${e.message}")
                scope.launch(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    // Reset control(s): POST /camera/controls/reset with optional {control}
    fun resetControl(baseUrl: String, control: String? = null, onResult: (success: Boolean, errorDetails: String?) -> Unit = { _, _ -> }) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject()
                if (control != null) json.put("control", control)

                val url = "$baseUrl/camera/controls/reset"
                val body = json.toString()
                Log.d("CameraController", "resetControl: POST $url with body: $body")

                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    Log.d("CameraController", "resetControl response: code=${response.code}, body=$responseBody")
                    
                    if (response.isSuccessful) {
                        scope.launch(Dispatchers.Main) { onResult(true, null) }
                    } else {
                        val errorMsg = "HTTP ${response.code}: $responseBody"
                        scope.launch(Dispatchers.Main) { onResult(false, errorMsg) }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
                Log.e("CameraController", "resetControl error: $errorMsg", e)
                scope.launch(Dispatchers.Main) { onResult(false, errorMsg) }
            }
        }
    }
    
    // === SYSTEM-BEFEHLE ===
    
    fun shutdown(onResult: (success: Boolean, message: String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val cameras = when (activeMode) {
                ControlMode.CAMERA_1 -> listOf(camera1BaseUrl)
                ControlMode.CAMERA_2 -> listOf(camera2BaseUrl)
                ControlMode.BOTH -> listOf(camera1BaseUrl, camera2BaseUrl)
            }
            
            var allSuccess = true
            val messages = mutableListOf<String>()
            
            cameras.forEach { baseUrl ->
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/system/shutdown")
                        .post("".toRequestBody(jsonMediaType))
                        .build()
                    
                    recordingClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            messages.add("✓ Shutdown eingeleitet")
                        } else {
                            allSuccess = false
                            messages.add("✗ Fehler: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    allSuccess = false
                    messages.add("✗ Fehler: ${e.message}")
                }
            }
            
            scope.launch(Dispatchers.Main) {
                onResult(allSuccess, messages.joinToString("\n"))
            }
        }
    }
    
    fun reboot(onResult: (success: Boolean, message: String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val cameras = when (activeMode) {
                ControlMode.CAMERA_1 -> listOf(camera1BaseUrl)
                ControlMode.CAMERA_2 -> listOf(camera2BaseUrl)
                ControlMode.BOTH -> listOf(camera1BaseUrl, camera2BaseUrl)
            }
            
            var allSuccess = true
            val messages = mutableListOf<String>()
            
            cameras.forEach { baseUrl ->
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/system/reboot")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    
                    recordingClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            messages.add("✓ Reboot eingeleitet")
                        } else {
                            allSuccess = false
                            messages.add("✗ Fehler: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    allSuccess = false
                    messages.add("✗ Fehler: ${e.message}")
                }
            }
            
            scope.launch(Dispatchers.Main) {
                onResult(allSuccess, messages.joinToString("\n"))
            }
        }
    }
}
