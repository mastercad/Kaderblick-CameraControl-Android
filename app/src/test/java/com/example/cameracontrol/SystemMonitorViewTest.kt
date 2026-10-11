package com.example.cameracontrol

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemMonitorViewTest {
    private lateinit var view: SystemMonitorView

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        view = SystemMonitorView(activity)
    }

    @Test
    fun warningButtonIsHiddenBeforeFirstStatus() {
        assertEquals(View.GONE, healthButton().visibility)
    }

    @Test
    fun warningButtonRemainsHiddenWithoutQualityEvents() {
        view.updateData(systemInfo(recordingActive = true, recordingEventCount = 0))

        assertEquals(View.GONE, healthButton().visibility)
    }

    @Test
    fun warningButtonAppearsForEventInCurrentRecording() {
        view.updateData(systemInfo(recordingActive = true, recordingEventCount = 1))

        assertEquals(View.VISIBLE, healthButton().visibility)
    }

    @Test
    fun warningButtonIsHiddenWithoutActiveRecordingDespiteOldEvents() {
        view.updateData(systemInfo(
            recordingActive = false,
            recordingEventCount = 0,
            globalEventCount = 42,
            healthStatus = "warning",
        ))

        assertEquals(View.GONE, healthButton().visibility)
    }

    @Test
    fun previewFrameDropsRemainVisibleInChartWithoutShowingWarningButton() {
        view.updateData(systemInfo(recordingActive = false, cameraEventCount = 0))
        view.updateData(systemInfo(recordingActive = false, cameraEventCount = 1))

        assertEquals(listOf(0f, 100f), healthHistory().toList())
        assertEquals(View.GONE, healthButton().visibility)
    }

    @Test
    fun warningButtonIsClearedWhenRecordingStops() {
        view.updateData(systemInfo(recordingActive = true, recordingEventCount = 1))
        view.updateData(systemInfo(recordingActive = false, recordingEventCount = 1))

        assertEquals(View.GONE, healthButton().visibility)
    }

    @Test
    fun eventResponseIsRenderedWithTimestampPhaseAndMessage() {
        view.updateData(systemInfo(recordingActive = true, recordingEventCount = 1))
        val response = JSONObject().apply {
            put("events", org.json.JSONArray().put(JSONObject().apply {
                put("timestamp", "2026-10-11T12:14:18.074+02:00")
                put("severity", "warning")
                put("recording", true)
                put("recording_health_log", "aufnahme_current_health.jsonl")
                put("message", "V4L2-Sequenzsprung: 10 Frame(s) fehlen")
            }))
        }

        val text = view.formatHealthEvents(response)

        assertTrue(text.contains("2026-10-11 12:14:18.074+02:00 · Aufnahme · WARNUNG"))
        assertTrue(text.contains("V4L2-Sequenzsprung: 10 Frame(s) fehlen"))
        assertTrue(!text.contains("Keine Ereignisse gespeichert"))
    }

    @Test
    fun eventDialogContainsNoOldDataWithoutRecording() {
        view.updateData(systemInfo(recordingActive = false, globalEventCount = 12))
        val response = JSONObject().apply {
            put("events", org.json.JSONArray().put(JSONObject().apply {
                put("recording", true)
                put("message", "Altes Ereignis")
            }))
        }

        assertEquals("Keine laufende Aufnahme.", view.formatHealthEvents(response))
    }

    private fun systemInfo(
        recordingActive: Boolean,
        recordingEventCount: Long = 0,
        globalEventCount: Long = 0,
        cameraEventCount: Long = 0,
        healthStatus: String = "ok",
    ) = JSONObject().apply {
        put("cpu_percent", 0)
        put("ram_percent", 0)
        put("temperature_celsius", 0)
        put("camera_health", JSONObject().apply {
            put("health_status", healthStatus)
            put("capture_thread_alive", true)
            put("recording_active", recordingActive)
            put("health_log_file", if (recordingActive) "aufnahme_current_health.jsonl" else "")
            put("recording_health_event_count", recordingEventCount)
            put("health_event_count", cameraEventCount)
            put("health_log_event_count", globalEventCount)
        })
    }

    private fun healthButton(): ImageButton {
        val field = SystemMonitorView::class.java.getDeclaredField("healthButton")
        field.isAccessible = true
        return field.get(view) as ImageButton
    }

    @Suppress("UNCHECKED_CAST")
    private fun healthHistory(): java.util.LinkedList<Float> {
        val field = SystemMonitorView::class.java.getDeclaredField("healthHistory")
        field.isAccessible = true
        return field.get(view) as java.util.LinkedList<Float>
    }
}
