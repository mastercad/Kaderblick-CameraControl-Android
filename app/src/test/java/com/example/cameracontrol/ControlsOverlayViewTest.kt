package com.example.cameracontrol

import android.app.Activity
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Robolectric-Tests für ControlsOverlayView.
 * Deckt die Änderungen ab:
 *   - Y-Invert in Joystick-Section (nicht in Quick-Section)
 *   - isDirty-Tracking
 *   - onMeasure begrenzt Höhe auf 82% des Bildschirms
 *   - Spinner zeigt immer tatsächliche Kamera-Auflösung
 *   - "Ist:"-Label zeigt Ist-Auflösung
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControlsOverlayViewTest {

    private lateinit var activity: Activity
    private lateinit var view: ControlsOverlayView
    private lateinit var controller: CameraController

    private val cam1 = listOf("http://192.168.178.47:8000")

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        controller = mock()
        whenever(controller.getInvertServo(any())).thenReturn(false)
        // Netzwerk-Callbacks nie aufrufen – verhindert Abstürze durch fehlende Verbindung
        whenever(controller.getResolution(any(), any())).thenAnswer { }
        whenever(controller.fetchControls(any(), any())).thenAnswer { }

        view = ControlsOverlayView(activity)
    }

    // ===========================================================================
    // Joystick-Section: enthält Y-Invert-Switch
    // ===========================================================================

    @Test
    fun joystickSectionContainsYInvertSwitch() {
        view.attach(controller, cam1, "Kamera 1")

        val joystick = view.findViewById<LinearLayout>(R.id.controls_joystick_section)
        assertNotNull("controls_joystick_section muss existieren", joystick)
        assertTrue(
            "Joystick-Section muss einen Switch (Y-Invert) enthalten",
            containsViewOfType(joystick, Switch::class.java)
        )
    }

    @Test
    fun joystickSectionHeaderContainsWordJoystick() {
        view.attach(controller, cam1, "Kamera 1")

        val joystick = view.findViewById<LinearLayout>(R.id.controls_joystick_section)
        val header = findFirstTextView(joystick)
        assertNotNull("Joystick-Section muss eine Überschrift haben", header)
        assertTrue(
            "Joystick-Überschrift muss 'Joystick' enthalten",
            header!!.text.toString().contains("Joystick", ignoreCase = true)
        )
    }

    @Test
    fun joystickSwitchReflectsGetInvertServoResult() {
        whenever(controller.getInvertServo(cam1.first())).thenReturn(true)
        view.attach(controller, cam1, "Kamera 1")

        val joystick = view.findViewById<LinearLayout>(R.id.controls_joystick_section)
        val switch = findFirstViewOfType(joystick, Switch::class.java)
        assertNotNull("Switch muss vorhanden sein", switch)
        assertTrue("Switch muss den Wert von getInvertServo widerspiegeln", switch!!.isChecked)
    }

    // ===========================================================================
    // Quick-Section: enthält KEINEN Y-Invert-Switch
    // ===========================================================================

    @Test
    fun quickSectionDoesNotContainYInvertSwitch() {
        view.attach(controller, cam1, "Kamera 1")

        val quickSection = view.findViewById<LinearLayout>(R.id.controls_quick_section)
        assertNotNull("controls_quick_section muss existieren", quickSection)
        assertFalse(
            "Quick-Section darf KEINEN Switch enthalten (Y-Invert wurde in Joystick-Section verschoben)",
            containsViewOfType(quickSection, Switch::class.java)
        )
    }

    // ===========================================================================
    // isDirty
    // ===========================================================================

    @Test
    fun closingWhenDirtyHidesOverlay() {
        view.attach(controller, cam1, "Kamera 1")
        view.visibility = View.VISIBLE
        setIsDirty(view, true)

        view.findViewById<Button>(R.id.btnCloseControls).performClick()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun closingWithoutChangesHidesOverlay() {
        view.attach(controller, cam1, "Kamera 1")
        view.visibility = View.VISIBLE

        view.findViewById<Button>(R.id.btnCloseControls).performClick()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun isDirtyResetToFalseAfterReAttach() {
        view.attach(controller, cam1, "Kamera 1")
        setIsDirty(view, true)

        view.attach(controller, cam1, "Kamera 1")

        assertFalse("isDirty muss nach erneutem attach() false sein", getIsDirty(view))
    }

    @Test
    fun isDirtyInitiallyFalseAfterFirstAttach() {
        view.attach(controller, cam1, "Kamera 1")
        assertFalse("isDirty muss nach attach() false sein", getIsDirty(view))
    }

    // ===========================================================================
    // onMeasure: Höhe auf 82% des Bildschirms begrenzt
    // ===========================================================================

    @Test
    fun onMeasureConstrainsHeightTo82PercentOfScreen() {
        val screenH = activity.resources.displayMetrics.heightPixels
        val expectedMax = (screenH * 0.82f).toInt()

        view.measure(
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(9999, View.MeasureSpec.AT_MOST)
        )

        assertTrue(
            "Gemessene Höhe ${view.measuredHeight} muss ≤ $expectedMax (82% von $screenH) sein",
            view.measuredHeight <= expectedMax
        )
    }

    // ===========================================================================
    // Hilfsfunktionen
    // ===========================================================================

    private fun setIsDirty(target: ControlsOverlayView, value: Boolean) {
        val field = ControlsOverlayView::class.java.getDeclaredField("isDirty")
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getIsDirty(target: ControlsOverlayView): Boolean {
        val field = ControlsOverlayView::class.java.getDeclaredField("isDirty")
        field.isAccessible = true
        return field.getBoolean(target)
    }

    private fun <T : View> containsViewOfType(parent: ViewGroup, type: Class<T>): Boolean {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (type.isInstance(child)) return true
            if (child is ViewGroup && containsViewOfType(child, type)) return true
        }
        return false
    }

    private fun <T : View> findFirstViewOfType(parent: ViewGroup, type: Class<T>): T? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            @Suppress("UNCHECKED_CAST")
            if (type.isInstance(child)) return child as T
            if (child is ViewGroup) {
                val found = findFirstViewOfType(child, type)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findFirstTextView(parent: ViewGroup): TextView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView) return child
            if (child is ViewGroup) {
                val found = findFirstTextView(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun buildInactiveIntControlJson(): JSONObject = JSONObject().apply {
        put("device", "/dev/video0")
        put("controls", JSONObject().apply {
            put("exposure_time_absolute", JSONObject().apply {
                put("name", "exposure_time_absolute")
                put("type", "int")
                put("min", 10)
                put("max", 660)
                put("step", 1)
                put("default", 10)
                put("value", 10)
                put("supported", true)
                put("flags", "inactive")
            })
        })
    }

    /** Findet die direkte Kind-ViewGroup von controls_container, die [widget] enthält. */
    private fun findRowForWidget(widget: View): ViewGroup? {
        val container = view.findViewById<ViewGroup>(R.id.controls_container) ?: return null
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? ViewGroup ?: continue
            if (viewIsDescendant(child, widget)) return child
        }
        return null
    }

    private fun viewIsDescendant(parent: ViewGroup, target: View): Boolean {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child === target) return true
            if (child is ViewGroup && viewIsDescendant(child, target)) return true
        }
        return false
    }

    private fun buildMenuControlJson(): JSONObject = JSONObject().apply {
        put("device", "/dev/video0")
        put("controls", JSONObject().apply {
            put("auto_exposure", JSONObject().apply {
                put("name", "auto_exposure")
                put("type", "menu")
                put("min", 0)
                put("max", 3)
                put("default", 3)
                put("value", 3)
                put("supported", true)
                put("menu_entries", JSONObject().apply {
                    put("1", "Manual Mode")
                    put("3", "Aperture Priority Mode")
                })
            })
        })
    }

    private fun buildIntControlJson(): JSONObject = JSONObject().apply {
        put("device", "/dev/video0")
        put("controls", JSONObject().apply {
            put("contrast", JSONObject().apply {
                put("name", "contrast")
                put("type", "int")
                put("min", 0)
                put("max", 4)
                put("step", 1)
                put("default", 2)
                put("value", 2)
                put("supported", true)
            })
        })
    }

    // ===========================================================================
    // Tests: Menu-Control → Spinner / Int-Control → SeekBar
    // ===========================================================================

    @Test
    fun menuControlRendersSpinnerNotSeekBar() {
        val json = buildMenuControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val controlView = view.findViewWithTag<View>("auto_exposure")
        assertTrue(
            "Menu-Control muss als Spinner gerendert werden (tag='auto_exposure')",
            controlView is Spinner
        )
    }

    @Test
    fun menuSpinnerHasCorrectEntryCount() {
        val json = buildMenuControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val spinner = view.findViewWithTag<View>("auto_exposure") as? Spinner
        assertNotNull("Spinner für auto_exposure muss vorhanden sein", spinner)
        assertEquals("Spinner muss 2 Einträge haben (1: Manual, 3: Aperture)", 2, spinner!!.adapter.count)
    }

    @Test
    fun intControlRendersSeekBarNotSpinner() {
        val json = buildIntControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val controlView = view.findViewWithTag<View>("contrast")
        assertTrue(
            "Int-Control muss als SeekBar gerendert werden (tag='contrast')",
            controlView is SeekBar
        )
    }

    // ===========================================================================
    // Tests: flags=inactive → Widget disabled + Row gedimmt
    // ===========================================================================

    @Test
    fun inactiveControlWidgetIsDisabled() {
        val json = buildInactiveIntControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val widget = view.findViewWithTag<View>("exposure_time_absolute")
        assertNotNull("Widget für exposure_time_absolute muss vorhanden sein", widget)
        assertFalse("Inactive-Control muss isEnabled=false haben", widget!!.isEnabled)
    }

    @Test
    fun inactiveControlRowIsDimmed() {
        val json = buildInactiveIntControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val widget = view.findViewWithTag<View>("exposure_time_absolute")
        assertNotNull("Widget für exposure_time_absolute muss vorhanden sein", widget)
        val row = findRowForWidget(widget!!)
        assertNotNull("Row für exposure_time_absolute muss vorhanden sein", row)
        assertEquals("Inactive-Control-Row muss alpha=0.4 haben", 0.4f, row!!.alpha, 0.01f)
    }

    @Test
    fun activeControlWidgetIsEnabled() {
        val json = buildIntControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val widget = view.findViewWithTag<View>("contrast")
        assertNotNull("Widget für contrast muss vorhanden sein", widget)
        assertTrue("Aktives Control muss isEnabled=true haben", widget!!.isEnabled)
    }

    @Test
    fun activeControlRowIsNotDimmed() {
        val json = buildIntControlJson()
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[1] as (JSONObject?) -> Unit
            callback(json)
        }
        view.attach(controller, cam1, "Kamera 1")

        val widget = view.findViewWithTag<View>("contrast")
        assertNotNull("Widget für contrast muss vorhanden sein", widget)
        val row = findRowForWidget(widget!!)
        assertNotNull("Row für contrast muss vorhanden sein", row)
        assertEquals("Aktive-Control-Row muss alpha=1.0 haben", 1.0f, row!!.alpha, 0.01f)
    }

    // ===========================================================================
    // Tests: Spinner zeigt tatsächliche Kamera-Auflösung (Ist-Auflösung)
    // ===========================================================================

    @Test
    fun spinnerSetsToActualResolutionReturnedByCamera() {
        // getResolution gibt 1080p@30fps zurück (Kamera hat eine andere Auflösung als Default)
        whenever(controller.getResolution(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[1] as ((CameraController.ResolutionInfo?) -> Unit)
            cb(CameraController.ResolutionInfo(1920, 1080, 30))
        }
        view.attach(controller, cam1, "Kamera 1")
        // Looper leeren (handler.post-Runnables ausführen)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val quickSection = view.findViewById<LinearLayout>(R.id.controls_quick_section)
        val spinner = findFirstViewOfType(quickSection, Spinner::class.java)
        assertNotNull("Spinner muss in Quick-Section vorhanden sein", spinner)

        val expectedIdx = ControlsOverlayView.RESOLUTION_OPTIONS.indexOfFirst {
            it.width == 1920 && it.height == 1080 && it.fps == 30
        }
        assertTrue("1080p@30fps muss in RESOLUTION_OPTIONS enthalten sein", expectedIdx >= 0)
        assertEquals(
            "Spinner muss auf tatsächliche Kamera-Auflösung (1080p@30fps) gesetzt sein",
            expectedIdx,
            spinner!!.selectedItemPosition
        )
    }

    @Test
    fun istLabelShowsActualResolutionFromCamera() {
        whenever(controller.getResolution(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[1] as ((CameraController.ResolutionInfo?) -> Unit)
            cb(CameraController.ResolutionInfo(1920, 1080, 30))
        }
        view.attach(controller, cam1, "Kamera 1")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val quickSection = view.findViewById<LinearLayout>(R.id.controls_quick_section)
        val allTvs = mutableListOf<TextView>()
        collectAllTextViews(quickSection, allTvs)
        val istLabel = allTvs.find { it.text.toString().startsWith("Ist:") }

        assertNotNull("'Ist:'-Label muss in Quick-Section vorhanden sein", istLabel)
        assertTrue(
            "Ist-Label muss '1920' enthalten (tatsächliche Breite)",
            istLabel!!.text.toString().contains("1920")
        )
        assertTrue(
            "Ist-Label muss '30' enthalten (tatsächliche fps)",
            istLabel.text.toString().contains("30")
        )
    }

    @Test
    fun istLabelShowsNotReachableWhenCameraUnreachable() {
        whenever(controller.getResolution(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val cb = invocation.arguments[1] as ((CameraController.ResolutionInfo?) -> Unit)
            cb(null)  // Kamera nicht erreichbar
        }
        view.attach(controller, cam1, "Kamera 1")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val quickSection = view.findViewById<LinearLayout>(R.id.controls_quick_section)
        val allTvs = mutableListOf<TextView>()
        collectAllTextViews(quickSection, allTvs)
        val istLabel = allTvs.find { it.text.toString().startsWith("Ist:") }

        assertNotNull("'Ist:'-Label muss in Quick-Section vorhanden sein", istLabel)
        assertTrue(
            "Ist-Label muss 'nicht erreichbar' anzeigen wenn Kamera nicht antwortet",
            istLabel!!.text.toString().contains("nicht erreichbar")
        )
    }

    @Test
    fun istLabelInitiallyShowsLoadingText() {
        // getResolution antwortet NICHT (simuliert langsame Verbindung)
        whenever(controller.getResolution(any(), any())).thenAnswer { }
        view.attach(controller, cam1, "Kamera 1")
        // Looper NICHT leeren – simuliert: Antwort noch nicht angekommen

        val quickSection = view.findViewById<LinearLayout>(R.id.controls_quick_section)
        val allTvs = mutableListOf<TextView>()
        collectAllTextViews(quickSection, allTvs)
        val istLabel = allTvs.find { it.text.toString().startsWith("Ist:") }

        assertNotNull("'Ist:'-Label muss direkt nach attach() vorhanden sein", istLabel)
        assertTrue(
            "Ist-Label muss initial 'wird geladen' anzeigen",
            istLabel!!.text.toString().contains("wird geladen")
        )
    }

    private fun collectAllTextViews(parent: ViewGroup, result: MutableList<TextView>) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView) result.add(child)
            if (child is ViewGroup) collectAllTextViews(child, result)
        }
    }

    // ===========================================================================
    // Tests: switchCamera() – Kamera wechseln ohne Scroll-Reset
    // ===========================================================================

    @Test
    fun switchCameraUpdatesTitleToNewLabel() {
        val cam2Url = "http://192.168.178.48:8000"
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            val url = invocation.arguments[0] as String
            if (url == cam2Url) {
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[1] as (JSONObject?) -> Unit
                callback(JSONObject().apply { put("controls", JSONObject()) })
            }
        }

        view.attach(controller, cam1, "Kamera 1")
        view.switchCamera(listOf(cam2Url), "Kamera 2")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val titleTv = view.findViewById<TextView>(R.id.controls_title)
        assertEquals(
            "Titel muss nach switchCamera() auf die neue Bezeichnung gesetzt werden",
            "Controls: Kamera 2",
            titleTv.text.toString()
        )
    }

    @Test
    fun switchCameraDoesNotHideOverlay() {
        view.attach(controller, cam1, "Kamera 1")
        view.visibility = View.VISIBLE

        view.switchCamera(listOf("http://192.168.178.48:8000"), "Kamera 2")

        assertEquals(
            "Overlay darf nach switchCamera() nicht ausgeblendet werden",
            View.VISIBLE,
            view.visibility
        )
    }

    @Test
    fun switchCameraFetchesControlsForNewUrl() {
        val cam2Url = "http://192.168.178.48:8000"
        var fetchedUrl: String? = null
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            fetchedUrl = invocation.arguments[0] as String
        }

        view.attach(controller, cam1, "Kamera 1")
        view.switchCamera(listOf(cam2Url), "Kamera 2")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "fetchControls muss mit der neuen Kamera-URL aufgerufen werden",
            cam2Url,
            fetchedUrl
        )
    }

    @Test
    fun switchCameraFetchesResolutionForNewUrl() {
        val cam2Url = "http://192.168.178.48:8000"
        var resolutionUrl: String? = null
        whenever(controller.getResolution(any(), any())).thenAnswer { invocation ->
            resolutionUrl = invocation.arguments[0] as String
        }

        view.attach(controller, cam1, "Kamera 1")
        view.switchCamera(listOf(cam2Url), "Kamera 2")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "getResolution muss mit der neuen Kamera-URL aufgerufen werden",
            cam2Url,
            resolutionUrl
        )
    }

    @Test
    fun switchCameraReloadsControlsFromNewCamera() {
        val cam2Url = "http://192.168.178.48:8000"
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            val url = invocation.arguments[0] as String
            if (url == cam2Url) {
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[1] as (JSONObject?) -> Unit
                callback(buildIntControlJson()) // liefert "contrast"-Control
            }
        }

        view.attach(controller, cam1, "Kamera 1")
        view.switchCamera(listOf(cam2Url), "Kamera 2")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val controlView = view.findViewWithTag<View>("contrast")
        assertNotNull(
            "Control aus Kamera 2 muss nach switchCamera() im Container vorhanden sein",
            controlView
        )
    }

    @Test
    fun switchCameraKeepsOldControlsUntilFetchCompletes() {
        // cam1 liefert "contrast", cam2 antwortet nicht (simuliert laufenden Request)
        val cam2Url = "http://192.168.178.48:8000"
        whenever(controller.fetchControls(any(), any())).thenAnswer { invocation ->
            val url = invocation.arguments[0] as String
            if (url == cam1.first()) {
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[1] as (JSONObject?) -> Unit
                callback(buildIntControlJson()) // "contrast" für Kamera 1
            }
            // cam2Url antwortet absichtlich nicht → Container soll noch alte Views zeigen
        }

        view.attach(controller, cam1, "Kamera 1")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val containerBefore = view.findViewById<ViewGroup>(R.id.controls_container)
        val childCountBefore = containerBefore.childCount
        val titleTv = view.findViewById<TextView>(R.id.controls_title)

        // Jetzt switchCamera() – cam2 antwortet NICHT
        view.switchCamera(listOf(cam2Url), "Kamera 2")
        // Looper nicht leeren – fetch-Callback kommt noch nicht zurück

        assertEquals(
            "Container darf NICHT geleert werden bevor der Fetch abgeschlossen ist",
            childCountBefore,
            containerBefore.childCount
        )
        assertEquals(
            "Titel darf NICHT geändert werden bevor der Fetch abgeschlossen ist",
            "Controls: Kamera 1",
            titleTv.text.toString()
        )
    }
}
