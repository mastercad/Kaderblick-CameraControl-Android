package com.example.cameracontrol

import org.json.JSONObject

/**
 * Converts cumulative camera-service counters into point-in-time problem samples.
 * Hard failures remain active until the service reports recovery; counter changes
 * create a single pulse so the chart shows when a drop or damaged frame occurred.
 */
class RecordingHealthTracker {
    private val previousCounters = mutableMapOf<String, Long>()
    private var initialized = false

    fun sample(systemInfo: JSONObject): Boolean {
        val health = systemInfo.optJSONObject("camera_health") ?: return false
        return sample(RecordingHealthSnapshot.from(health))
    }

    fun sample(snapshot: RecordingHealthSnapshot): Boolean {
        val hardFailure = snapshot.hardFailure

        var counterIncreased = false
        snapshot.counters.forEach { (key, current) ->
            val previous = previousCounters.put(key, current)
            if (initialized && previous != null && current > previous) {
                counterIncreased = true
            }
        }
        initialized = true
        return hardFailure || counterIncreased
    }

    fun reset() {
        previousCounters.clear()
        initialized = false
    }

    companion object {
        internal val COUNTERS = listOf(
            "health_event_count",
            "v4l2_sequence_frames_lost",
            "v4l2_timestamp_gaps",
            "v4l2_buffer_errors",
            "invalid_mjpeg_frames",
            "writer_queue_drops",
            "health_log_drops",
            "health_log_event_count",
            "audio_xruns",
            "audio_recovery_restarts",
            "power_event_count",
        )
    }
}

data class RecordingHealthSnapshot(
    val hardFailure: Boolean,
    val counters: Map<String, Long>,
) {
    companion object {
        fun from(health: JSONObject): RecordingHealthSnapshot {
            fun hasText(key: String): Boolean =
                !health.isNull(key) && health.optString(key).isNotBlank() && health.optString(key) != "null"

            return RecordingHealthSnapshot(
                hardFailure = health.optString("health_status") == "error" ||
                    !health.optBoolean("capture_thread_alive", true) ||
                    hasText("capture_error") ||
                    hasText("writer_error") ||
                    hasText("audio_error") ||
                    health.optBoolean("power_undervoltage_now", false) ||
                    health.optBoolean("power_throttled_now", false) ||
                    health.optDouble("last_frame_age_s", 0.0) > 2.0,
                counters = RecordingHealthTracker.COUNTERS.associateWith { health.optLong(it, 0L) },
            )
        }
    }
}
