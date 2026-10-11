package com.example.cameracontrol

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        view.updateData(systemInfo(healthStatus = "ok", eventCount = 0))

        assertEquals(View.GONE, healthButton().visibility)
    }

    @Test
    fun warningButtonAppearsWhenQualityEventExists() {
        view.updateData(systemInfo(healthStatus = "warning", eventCount = 1))

        assertEquals(View.VISIBLE, healthButton().visibility)
    }

    private fun systemInfo(healthStatus: String, eventCount: Long) = JSONObject().apply {
        put("cpu_percent", 0)
        put("ram_percent", 0)
        put("temperature_celsius", 0)
        put("camera_health", JSONObject().apply {
            put("health_status", healthStatus)
            put("capture_thread_alive", true)
            put("health_log_event_count", eventCount)
        })
    }

    private fun healthButton(): ImageButton {
        val field = SystemMonitorView::class.java.getDeclaredField("healthButton")
        field.isAccessible = true
        return field.get(view) as ImageButton
    }
}
