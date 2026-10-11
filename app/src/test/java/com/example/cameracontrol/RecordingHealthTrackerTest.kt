package com.example.cameracontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingHealthTrackerTest {
    private fun snapshot(
        hardFailure: Boolean = false,
        vararg counters: Pair<String, Long>,
    ) = RecordingHealthSnapshot(hardFailure, counters.toMap())

    @Test
    fun healthySamplesRemainClear() {
        val tracker = RecordingHealthTracker()
        assertFalse(tracker.sample(snapshot(false, "health_event_count" to 0)))
        assertFalse(tracker.sample(snapshot(false, "health_event_count" to 0)))
    }

    @Test
    fun counterIncreaseCreatesOneProblemPulse() {
        val tracker = RecordingHealthTracker()
        assertFalse(tracker.sample(snapshot(false, "invalid_mjpeg_frames" to 5)))
        assertTrue(tracker.sample(snapshot(false, "invalid_mjpeg_frames" to 6)))
        assertFalse(tracker.sample(snapshot(false, "invalid_mjpeg_frames" to 6)))
    }

    @Test
    fun hardFailureRemainsVisibleUntilRecovery() {
        val tracker = RecordingHealthTracker()
        assertTrue(tracker.sample(snapshot(hardFailure = true)))
        assertTrue(tracker.sample(snapshot(hardFailure = true)))
        assertFalse(tracker.sample(snapshot()))
    }

    @Test
    fun deadCaptureThreadIsAProblem() {
        assertTrue(RecordingHealthTracker().sample(snapshot(hardFailure = true)))
    }

    @Test
    fun audioRecoveryIncreaseCreatesProblemPulse() {
        val tracker = RecordingHealthTracker()
        assertFalse(tracker.sample(snapshot(false, "audio_recovery_restarts" to 0)))
        assertTrue(tracker.sample(snapshot(false, "audio_recovery_restarts" to 1)))
    }

    @Test
    fun currentUndervoltageIsHardFailure() {
        val health = org.json.JSONObject().apply {
            put("health_status", "ok")
            put("capture_thread_alive", true)
            put("power_undervoltage_now", true)
        }
        assertTrue(RecordingHealthTracker().sample(RecordingHealthSnapshot.from(health)))
    }
}
