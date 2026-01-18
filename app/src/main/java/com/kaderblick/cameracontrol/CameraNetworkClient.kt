package com.kaderblick.cameracontrol

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Network client for communicating with Kaderblick wireless cameras
 */
class CameraNetworkClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    interface CameraCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    /**
     * Send a command to the camera
     */
    fun sendCommand(endpoint: String, command: String, callback: CameraCallback) {
        val url = "$baseUrl/$endpoint"
        
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val body = command.toRequestBody(jsonMediaType)
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback.onError("HTTP ${response.code}: ${response.message}")
                        return
                    }
                    callback.onSuccess(response.body?.string() ?: "")
                }
            }
        })
    }

    /**
     * Test connection to camera
     */
    fun testConnection(callback: CameraCallback) {
        val request = Request.Builder()
            .url("$baseUrl/status")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Connection failed")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        callback.onSuccess("Connected")
                    } else {
                        callback.onError("Connection failed: ${response.code}")
                    }
                }
            }
        })
    }

    /**
     * Capture photo from specified camera
     */
    fun capturePhoto(cameraId: Int, callback: CameraCallback) {
        val command = """{"action":"capture","camera":$cameraId}"""
        sendCommand("capture", command, callback)
    }

    /**
     * Start recording from specified camera
     */
    fun startRecording(cameraId: Int, callback: CameraCallback) {
        val command = """{"action":"start_recording","camera":$cameraId}"""
        sendCommand("record", command, callback)
    }

    /**
     * Stop recording from specified camera
     */
    fun stopRecording(cameraId: Int, callback: CameraCallback) {
        val command = """{"action":"stop_recording","camera":$cameraId}"""
        sendCommand("record", command, callback)
    }

    /**
     * Switch active camera
     */
    fun switchCamera(cameraId: Int, callback: CameraCallback) {
        val command = """{"action":"switch","camera":$cameraId}"""
        sendCommand("switch", command, callback)
    }
}
