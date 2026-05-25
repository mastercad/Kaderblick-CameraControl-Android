package com.example.cameracontrol

import org.junit.Assert.*
import org.junit.Test

class CameraHealthStatusTest {

    // -----------------------------------------------------------------------
    // Hilfsfunktion: minimales Objekt für übersichtliche Tests
    // -----------------------------------------------------------------------

    private fun status(
        isRecording: Boolean = false,
        captureThreadAlive: Boolean = true,
        framesWritten: Int = 100,
        lastFrameAgeS: Double? = 0.1,
        captureError: String? = null,
        audioAvailable: Boolean = true,
        audioError: String? = null,
        videoFileBytes: Long? = 1024L,
    ) = CameraHealthStatus(
        isRecording = isRecording,
        captureThreadAlive = captureThreadAlive,
        framesWritten = framesWritten,
        lastFrameAgeS = lastFrameAgeS,
        captureError = captureError,
        audioAvailable = audioAvailable,
        audioError = audioError,
        videoFileBytes = videoFileBytes,
    )

    // ===========================================================================
    // isFullyHealthy
    // ===========================================================================

    @Test
    fun isFullyHealthy_trueWhenRecordingAndNoErrors() {
        val s = status(isRecording = true, captureThreadAlive = true, captureError = null)
        assertTrue(s.isFullyHealthy)
    }

    @Test
    fun isFullyHealthy_falseWhenNotRecording() {
        val s = status(isRecording = false, captureThreadAlive = true, captureError = null)
        assertFalse(s.isFullyHealthy)
    }

    @Test
    fun isFullyHealthy_falseWhenCaptureThreadDead() {
        val s = status(isRecording = true, captureThreadAlive = false, captureError = null)
        assertFalse(s.isFullyHealthy)
    }

    @Test
    fun isFullyHealthy_falseWhenCaptureError() {
        val s = status(isRecording = true, captureThreadAlive = true, captureError = "DQBUF failed")
        assertFalse(s.isFullyHealthy)
    }

    @Test
    fun isFullyHealthy_falseWhenAllBad() {
        val s = status(isRecording = false, captureThreadAlive = false, captureError = "timeout")
        assertFalse(s.isFullyHealthy)
    }

    // ===========================================================================
    // hasAudioProblem
    // ===========================================================================

    @Test
    fun hasAudioProblem_falseWhenNotRecording() {
        val s = status(isRecording = false, audioAvailable = false, audioError = "crash")
        assertFalse(s.hasAudioProblem)
    }

    @Test
    fun hasAudioProblem_falseWhenRecordingAndAudioOk() {
        val s = status(isRecording = true, audioAvailable = true, audioError = null)
        assertFalse(s.hasAudioProblem)
    }

    @Test
    fun hasAudioProblem_trueWhenRecordingAndAudioUnavailable() {
        val s = status(isRecording = true, audioAvailable = false, audioError = null)
        assertTrue(s.hasAudioProblem)
    }

    @Test
    fun hasAudioProblem_trueWhenRecordingAndAudioError() {
        val s = status(isRecording = true, audioAvailable = true, audioError = "arecord crashed")
        assertTrue(s.hasAudioProblem)
    }

    @Test
    fun hasAudioProblem_trueWhenRecordingAndBothAudioProblems() {
        val s = status(isRecording = true, audioAvailable = false, audioError = "exit 1")
        assertTrue(s.hasAudioProblem)
    }

    // ===========================================================================
    // errorSummary
    // ===========================================================================

    @Test
    fun errorSummary_nullWhenEverythingHealthy() {
        val s = status(
            isRecording = true,
            captureThreadAlive = true,
            captureError = null,
            audioError = null,
            framesWritten = 10,
            videoFileBytes = 1024L,
        )
        assertNull(s.errorSummary)
    }

    @Test
    fun errorSummary_containsDeadThreadMessage() {
        val s = status(captureThreadAlive = false)
        assertTrue(s.errorSummary!!.contains("Capture-Thread tot"))
    }

    @Test
    fun errorSummary_containsCaptureErrorText() {
        val s = status(captureError = "select timeout")
        assertTrue(s.errorSummary!!.contains("select timeout"))
        assertTrue(s.errorSummary!!.contains("Video:"))
    }

    @Test
    fun errorSummary_containsAudioErrorText() {
        val s = status(audioError = "arecord exit 1")
        assertTrue(s.errorSummary!!.contains("arecord exit 1"))
        assertTrue(s.errorSummary!!.contains("Audio:"))
    }

    @Test
    fun errorSummary_containsNoFramesMessageWhenRecordingButZeroFrames() {
        val s = status(
            isRecording = true,
            framesWritten = 0,
            videoFileBytes = 0L,
            captureThreadAlive = true,
            captureError = null,
            audioError = null,
        )
        assertNotNull(s.errorSummary)
        assertTrue(s.errorSummary!!.contains("Keine Frames"))
    }

    @Test
    fun errorSummary_noFramesMessageWhenNotRecording() {
        val s = status(isRecording = false, framesWritten = 0, videoFileBytes = 0L)
        // "Keine Frames" erscheint nur wenn isRecording=true
        val summary = s.errorSummary
        if (summary != null) {
            assertFalse(summary.contains("Keine Frames"))
        }
    }

    @Test
    fun errorSummary_multipleErrorsSeparatedByPipe() {
        val s = status(
            captureThreadAlive = false,
            captureError = "DQBUF failed",
        )
        val summary = s.errorSummary!!
        assertTrue(summary.contains("|"))
        assertTrue(summary.contains("Capture-Thread tot"))
        assertTrue(summary.contains("DQBUF failed"))
    }

    @Test
    fun errorSummary_nullWhenNotRecordingAndNoErrors() {
        // captureThreadAlive=true, keine Fehler → kein errorSummary (auch wenn nicht recording)
        val s = status(isRecording = false, captureThreadAlive = true, captureError = null, audioError = null)
        assertNull(s.errorSummary)
    }
}
