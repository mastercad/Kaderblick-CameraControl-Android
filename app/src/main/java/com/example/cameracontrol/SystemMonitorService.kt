package com.example.cameracontrol

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SystemMonitorService(
    private val camera1BaseUrl: String,
    private val camera2BaseUrl: String,
    private val scope: CoroutineScope,
    private val onCamera1Data: (JSONObject) -> Unit,
    private val onCamera2Data: (JSONObject) -> Unit,
    private val onServerStatusChanged: (camera1Online: Boolean, camera2Online: Boolean) -> Unit
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    private var monitoringJob: Job? = null
    private val updateInterval = 2000L // 2 Sekunden

    // Anzahl aufeinanderfolgender Fehler pro Kamera bevor sie als offline gilt
    private val offlineThreshold = 2
    private var cam1FailCount = 0
    private var cam2FailCount = 0

    // Server-Status
    private var camera1Online = false
    private var camera2Online = false
    
    fun start() {
        if (monitoringJob?.isActive == true) return
        
        // Initial UI-Status setzen (beide offline bis Server antworten)
        scope.launch(Dispatchers.Main) {
            onServerStatusChanged(false, false)
        }
        
        monitoringJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    var cam1Online = camera1Online  // Bisherigen Status beibehalten bis Threshold erreicht
                    var cam2Online = camera2Online

                    // Parallel beide Kameras abfragen
                    val job1 = launch {
                        val data = fetchSystemInfo(camera1BaseUrl)
                        if (data != null) {
                            cam1FailCount = 0
                            cam1Online = true
                            scope.launch(Dispatchers.Main) { onCamera1Data(data) }
                        } else {
                            cam1FailCount++
                            if (cam1FailCount >= offlineThreshold) cam1Online = false
                        }
                    }

                    val job2 = launch {
                        val data = fetchSystemInfo(camera2BaseUrl)
                        if (data != null) {
                            cam2FailCount = 0
                            cam2Online = true
                            scope.launch(Dispatchers.Main) { onCamera2Data(data) }
                        } else {
                            cam2FailCount++
                            if (cam2FailCount >= offlineThreshold) cam2Online = false
                        }
                    }

                    job1.join()
                    job2.join()

                    // Status aktualisieren und IMMER Callback aufrufen
                    camera1Online = cam1Online
                    camera2Online = cam2Online
                    scope.launch(Dispatchers.Main) {
                        onServerStatusChanged(camera1Online, camera2Online)
                    }
                    Log.d("SystemMonitor", "Server status: CAM1=${camera1Online} (fail=$cam1FailCount), CAM2=${camera2Online} (fail=$cam2FailCount)")
                    
                } catch (e: Exception) {
                    Log.e("SystemMonitor", "Error fetching data", e)
                }
                
                delay(updateInterval)
            }
        }
    }
    
    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    private fun fetchSystemInfo(baseUrl: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/system/info")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { body ->
                        JSONObject(body)
                    }
                } else {
                    Log.w("SystemMonitor", "Request failed: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.d("SystemMonitor", "Fetch error for $baseUrl: ${e.message}")
            null
        }
    }
}
