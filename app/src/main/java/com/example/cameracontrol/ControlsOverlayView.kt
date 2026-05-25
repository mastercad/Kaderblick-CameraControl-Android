package com.example.cameracontrol

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.view.setPadding
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ControlsOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        // Bekannte V4L2- / Arducam-Steuernamen für HDR / Wide Dynamic Range
        private val HDR_CONTROL_KEYS = setOf(
            "wide_dynamic_range",
            "wdr",
            "hdr",
            "hdr_mode",
            "arducam_hdr"
        )

        // Vordefinierte Auflösungs-/FPS-Optionen (nur tatsächlich unterstützte MJPG-Modi)
        data class ResolutionOption(val label: String, val width: Int, val height: Int, val fps: Int)
        val RESOLUTION_OPTIONS = listOf(
            ResolutionOption("4K  – 30 fps", 3840, 2160, 30),
            ResolutionOption("4K  – 15 fps", 3840, 2160, 15),
            ResolutionOption("1080p – 60 fps", 1920, 1080, 60),
            ResolutionOption("1080p – 30 fps", 1920, 1080, 30),
            ResolutionOption("720p – 60 fps", 1280, 720, 60),
            ResolutionOption("720p – 30 fps", 1280, 720, 30),
        )

        // V4L2-Schlüssel → deutsche Bezeichnung
        val CONTROL_LABELS = mapOf(
            "gain"                       to "Verstärkung",
            "exposure_time_absolute"     to "Belichtungszeit",
            "auto_exposure"              to "Belichtungsmodus",
            "contrast"                   to "Kontrast",
            "saturation"                 to "Farbsättigung",
            "sharpness"                  to "Schärfe",
            "brightness"                 to "Helligkeit",
            "white_balance_temperature"  to "Weißabgleich-Temperatur",
            "white_balance_automatic"    to "Weißabgleich automatisch",
            "backlight_compensation"     to "Gegenlichtkorrektur",
            "power_line_frequency"       to "Netzfrequenz (Flicker)",
            "gamma"                      to "Gamma",
            "wide_dynamic_range"         to "HDR",
            "wdr"                        to "HDR",
            "hdr"                        to "HDR",
            "hdr_mode"                   to "HDR-Modus",
            "arducam_hdr"                to "HDR",
            "focus_absolute"             to "Fokus",
            "focus_automatic_continuous" to "Autofokus",
            "zoom_absolute"              to "Zoom",
            "pan_absolute"               to "Schwenk (horizontal)",
            "tilt_absolute"              to "Neigung (vertikal)"
        )
        // Factory-Presets (hardcoded im Controller — Kameras erhalten nur einzelne Control-Werte)
        val FACTORY_PRESETS = mapOf(
            "☀ Vollsonne"        to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 30,  "gain" to 1,  "contrast" to 2, "saturation" to 2, "sharpness" to 4),
            "🌤 Leicht bewölkt"  to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 60,  "gain" to 1,  "contrast" to 2, "saturation" to 2, "sharpness" to 4),
            "⛅ Bewölkt"         to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 120, "gain" to 2,  "contrast" to 2, "saturation" to 2, "sharpness" to 3),
            "🌥 Stark bewölkt"   to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 250, "gain" to 3,  "contrast" to 2, "saturation" to 2, "sharpness" to 3),
            "🌧 Regen"           to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 380, "gain" to 5,  "contrast" to 2, "saturation" to 2, "sharpness" to 3),
            "⛈ Starker Regen"   to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 550, "gain" to 8,  "contrast" to 2, "saturation" to 2, "sharpness" to 2),
            "❄ Schnee"           to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 40,  "gain" to 1,  "contrast" to 2, "saturation" to 1, "sharpness" to 4),
            "🌆 Gegenlicht"      to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 60,  "gain" to 2,  "contrast" to 3, "saturation" to 2, "sharpness" to 3),
            "💡 Flutlicht"       to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 200, "gain" to 6,  "contrast" to 2, "saturation" to 2, "sharpness" to 3),
            "🌙 Flutlicht (dunkel)" to mapOf("auto_exposure" to 1, "exposure_time_absolute" to 400, "gain" to 12, "contrast" to 2, "saturation" to 2, "sharpness" to 2)
        )
    }
    // Ergebnis eines Auto-Tune-Laufs pro Kamera
    private data class CamResult(
        val label: String,
        val before: Map<String, Int>,
        val after: Map<String, Int>
    )

    private val title: TextView
    private val container: LinearLayout
    private val quickSection: LinearLayout
    private val presetSection: LinearLayout
    private val joystickSection: LinearLayout

    private val btnClose: Button
    private val btnResetAll: Button
    private lateinit var scrollView: android.widget.ScrollView

    // Multi-URL support (CAMERA_1 = [url1], CAMERA_2 = [url2], BOTH = [url1, url2])
    private var baseUrls: List<String> = emptyList()
    private var frameViews: List<MjpegTextureView> = emptyList()
    private val primaryUrl get() = baseUrls.firstOrNull()

    private var cameraController: CameraController? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastX = 0f
    private var lastY = 0f
    private var isDirty = false

    // debounce map per control
    private val pendingRunnables = mutableMapOf<String, Runnable>()
    // status TextViews per control for feedback
    private val statusViews = mutableMapOf<String, TextView>()
    // Aktuelle Control-Werte für Preset-Matching und Auto-Tune
    private val currentValues = mutableMapOf<String, Int>()
    // Control-Ranges (min, max, step) — befüllt in addControlRow
    private val controlRanges = mutableMapOf<String, Triple<Int, Int, Int>>()
    // SeekBars + Value-Labels für programmatische Updates (Auto-Tune)
    private val seekBars = mutableMapOf<String, SeekBar>()
    private val valueTextViews = mutableMapOf<String, TextView>()
    // Interaktives Widget pro Control (für enable/disable bei flags=inactive)
    private val controlWidgets = mutableMapOf<String, View>()
    // Äußere Row pro Control (für alpha-Dimming)
    private val controlRows = mutableMapOf<String, View>()
    // Auflösungs-Spinner: Referenz, Listener und letzter committeter Index
    private var resSpinnerRef: Spinner? = null
    private var resSpinnerListener: AdapterView.OnItemSelectedListener? = null
    private var committedResolutionIdx = 0
    private var updatingResSpinnerProgrammatically = false
    // "Ist:"-Label: zeigt tatsächliche Kamera-Auflösung (periodisch aktualisiert)
    private var actualResolutionTv: TextView? = null
    private var resolutionPollRunnable: Runnable? = null
    init {
        val v = LayoutInflater.from(context).inflate(R.layout.controls_overlay, this, true)
        title = v.findViewById(R.id.controls_title)
        container = v.findViewById(R.id.controls_container)
        quickSection = v.findViewById(R.id.controls_quick_section)
        presetSection = v.findViewById(R.id.controls_preset_section)
        joystickSection = v.findViewById(R.id.controls_joystick_section)
        btnClose = v.findViewById(R.id.btnCloseControls)
        btnResetAll = v.findViewById(R.id.btnResetAll)
        scrollView = v.findViewById(R.id.controls_main_scroll)

        // drag
        setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX
                    lastY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lastX
                    val dy = ev.rawY - lastY
                    translationX += dx
                    translationY += dy
                    // Position auf Bildschirmgrenzen einschränken
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels
                    val loc = IntArray(2)
                    getLocationOnScreen(loc)
                    if (loc[0] < 0) translationX -= loc[0]
                    if (loc[1] < 0) translationY -= loc[1]
                    if (loc[0] + width > screenW) translationX -= (loc[0] + width - screenW)
                    if (loc[1] + height > screenH) translationY -= (loc[1] + height - screenH)
                    lastX = ev.rawX
                    lastY = ev.rawY
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener {
            isDirty = false
            visibility = View.GONE
        }
        btnResetAll.setOnClickListener { baseUrls.forEach { url -> resetAll(url) } }
    }

    fun attach(
        controller: CameraController,
        urls: List<String>,
        label: String,
        views: List<MjpegTextureView> = emptyList()
    ) {
        this.cameraController = controller
        this.baseUrls = urls
        this.frameViews = views
        isDirty = false
        currentValues.clear()
        controlRanges.clear()
        seekBars.clear()
        valueTextViews.clear()
        controlWidgets.clear()
        controlRows.clear()
        title.text = "Controls: $label"
        buildQuickSection(controller)
        buildJoystickSection(controller)
        loadControls()
        buildPresetSection()
        startResolutionPoll()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxH = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }

    // =============================
    // Quick-Section: Auflösung
    // =============================

    private fun buildQuickSection(controller: CameraController) {
        val firstUrl = primaryUrl ?: return
        quickSection.removeAllViews()

        // Trennlinie oben
        quickSection.addView(makeDividerView())

        // Auflösung / FPS
        val resRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(6)
        }
        val resLabel = TextView(context).apply {
            text = "🎥 Auflösung"
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
        }
        val resSpinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val resStatusTv = TextView(context).apply {
            text = ""
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(30, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val spinnerLabels = RESOLUTION_OPTIONS.map { it.label }
        resSpinner.adapter = makeDarkSpinnerAdapter(spinnerLabels)

        resSpinnerRef = resSpinner

        // "Ist:"-TextView VOR dem getResolution-Aufruf anlegen, damit der Callback darauf schreiben kann
        val actualResTv = TextView(context).apply {
            text = "Ist: wird geladen\u2026"
            setTextColor(0xFFFFBB33.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        actualResolutionTv = actualResTv

        // Listener als Klassen-Member, damit er bei programmatischen Updates abgehoben werden kann
        resSpinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingResSpinnerProgrammatically) return
                val prevIdx = committedResolutionIdx
                val opt = RESOLUTION_OPTIONS[position]
                resStatusTv.text = "⏳"
                var remaining = baseUrls.size
                var anyError: String? = null
                if (baseUrls.isEmpty()) return
                baseUrls.forEach { url ->
                    controller.setResolution(url, opt.width, opt.height, opt.fps) { success, error ->
                        if (!success) anyError = error
                        remaining--
                        if (remaining == 0) {
                            if (anyError == null) {
                                committedResolutionIdx = position
                                resStatusTv.text = "✓"
                                handler.postDelayed({ resStatusTv.text = "" }, 3000)
                                // Nach Auflösungswechsel Ist-Anzeige aktualisieren
                                handler.postDelayed({ pollActualResolution() }, 2000)
                            } else {
                                resStatusTv.text = "✗"
                                Toast.makeText(context, "Auflösung fehlgeschlagen: $anyError", Toast.LENGTH_LONG).show()
                                handler.postDelayed({ resStatusTv.text = "" }, 4000)
                                // Spinner auf letzten bestätigten Wert zurücksetzen
                                updatingResSpinnerProgrammatically = true
                                resSpinnerRef?.setSelection(prevIdx)
                                resSpinnerRef?.post { updatingResSpinnerProgrammatically = false }
                            }
                        }
                    }
                }
            }
        }

        controller.getResolution(firstUrl) { info ->
            if (info != null) {
                val idx = RESOLUTION_OPTIONS.indexOfFirst { it.width == info.width && it.height == info.height && it.fps == info.fps }
                if (idx >= 0) {
                    updatingResSpinnerProgrammatically = true
                    resSpinner.setSelection(idx)
                    committedResolutionIdx = idx
                    resSpinner.post { updatingResSpinnerProgrammatically = false }
                }
                actualResolutionTv?.text = "Ist: ${info.width}×${info.height} @ ${info.fps} fps"
            }
            resSpinner.post {
                resSpinner.onItemSelectedListener = resSpinnerListener
            }
        }

        resRow.addView(resLabel)
        resRow.addView(resSpinner)
        resRow.addView(resStatusTv)
        quickSection.addView(resRow)

        // "Ist:"-Zeile: zeigt tatsächliche Kamera-Auflösung (immer aktuell)
        val actualResRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(6)
        }
        actualResRow.addView(actualResTv)
        quickSection.addView(actualResRow)

        // Auto-Tune-Button (nur wenn Frame-Quellen vorhanden)
        if (frameViews.isNotEmpty()) {
            quickSection.addView(makeDividerView())
            val tuneBtn = Button(context).apply {
                text = "🎯 Einmessen"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    isEnabled = false
                    text = "⏳ Einmessen läuft…"
                    startAutoTune(this)
                }
            }
            quickSection.addView(tuneBtn)
        }

        quickSection.addView(makeDividerView())
    }

    // =============================
    // Joystick-Section: Y-Achse invertieren
    // =============================

    private fun buildJoystickSection(controller: CameraController) {
        val firstUrl = primaryUrl ?: return
        joystickSection.removeAllViews()
        joystickSection.addView(makeDividerView())

        val headerTv = TextView(context).apply {
            text = "Joystick / Steuerung"
            setTextColor(0xFFFFBB33.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(6, 8, 6, 4)
        }
        joystickSection.addView(headerTv)

        val invertRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(6, 0, 6, 6)
        }
        val invertLabel = TextView(context).apply {
            text = "Y-Achse invertieren"
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val invertSwitch = Switch(context).apply {
            isChecked = controller.getInvertServo(firstUrl)
            setOnCheckedChangeListener { _, checked ->
                baseUrls.forEach { url -> controller.setInvertServo(url, checked) }
            }
        }
        invertRow.addView(invertLabel)
        invertRow.addView(invertSwitch)
        joystickSection.addView(invertRow)
    }

    // =============================
    // Preset-Section (Factory-Presets, hardcoded im Controller)
    // =============================

    private fun buildPresetSection() {
        presetSection.removeAllViews()
        presetSection.addView(makeDividerView())

        val headerTv = TextView(context).apply {
            text = "Presets"
            setTextColor(0xFF4FC3F7.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(6, 8, 6, 4)
        }
        presetSection.addView(headerTv)

        val presetNames = FACTORY_PRESETS.keys.toList()
        val spinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            adapter = makeDarkSpinnerAdapter(presetNames)
        }
        presetSection.addView(spinner)

        val btnApply = Button(context).apply {
            text = "Anwenden"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val name = presetNames.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
                val controls = FACTORY_PRESETS[name] ?: return@setOnClickListener
                baseUrls.forEach { url ->
                    controls.forEach { (key, value) ->
                        cameraController?.setControl(url, key, value) { _ -> }
                    }
                }
                handler.postDelayed({ loadControls() }, 600)
                Toast.makeText(context, "✓ Preset '$name' angewendet", Toast.LENGTH_SHORT).show()
            }
        }
        presetSection.addView(btnApply)
    }

    private fun makeDarkSpinnerAdapter(labels: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(android.graphics.Color.WHITE)
                return v
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.apply {
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(0xFF1E1E1E.toInt())
                }
                return v
            }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun makeDividerView(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).also { it.setMargins(0, 6, 0, 6) }
        setBackgroundColor(0x554FC3F7.toInt())
    }

    private fun resetAll(url: String) {
        cameraController?.resetControl(url, null) { success, errorDetails ->
            if (success) {
                loadControls()
                Toast.makeText(context, "Controls reset", Toast.LENGTH_SHORT).show()
            } else {
                val msg = "Reset fehlgeschlagen:\n${errorDetails ?: "Unbekannter Fehler"}"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadControls(onComplete: (() -> Unit)? = null) {
        container.removeAllViews()
        val b = primaryUrl ?: return
        currentValues.clear()
        controlRanges.clear()
        seekBars.clear()
        valueTextViews.clear()
        controlWidgets.clear()
        controlRows.clear()
        cameraController?.fetchControls(b) { json ->
            if (json == null) {
                Toast.makeText(context, "Could not load controls", Toast.LENGTH_SHORT).show()
                return@fetchControls
            }

            val controls = json.optJSONObject("controls") ?: JSONObject()
            val allEntries = mutableListOf<Pair<String, JSONObject>>()
            val keys = controls.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = controls.optJSONObject(k) ?: continue
                allEntries.add(k to obj)
            }

            val hdrEntries = allEntries.filter { (k, _) -> k.lowercase() in HDR_CONTROL_KEYS }
            val regularEntries = allEntries.filter { (k, _) -> k.lowercase() !in HDR_CONTROL_KEYS }

            if (hdrEntries.isNotEmpty()) {
                addSectionHeader("HDR")
                hdrEntries.forEach { (k, obj) -> addControlRow(k, obj, isHdr = true) }
                if (regularEntries.isNotEmpty()) addDivider()
            }

            regularEntries.forEach { (k, obj) -> addControlRow(k, obj) }
            onComplete?.invoke()
        }
    }

    /**
     * Switches the panel to show a different camera's controls WITHOUT resetting the scroll position.
     * Call this when the panel is already visible and the user picks a different camera.
     */
    fun switchCamera(urls: List<String>, label: String, views: List<MjpegTextureView> = emptyList()) {
        this.baseUrls = urls
        this.frameViews = views
        val savedScrollY = scrollView.scrollY
        cameraController?.let { buildQuickSection(it) }

        // Fetch FIRST – Titel und Container bleiben mit alten Werten bis die neuen da sind (kein Leer-Flash)
        val b = primaryUrl ?: return
        cameraController?.fetchControls(b) { json ->
            title.text = "Controls: $label"
            container.removeAllViews()
            currentValues.clear()
            controlRanges.clear()
            seekBars.clear()
            valueTextViews.clear()
            controlWidgets.clear()
            controlRows.clear()
            if (json == null) {
                Toast.makeText(context, "Could not load controls", Toast.LENGTH_SHORT).show()
                return@fetchControls
            }
            val controls = json.optJSONObject("controls") ?: JSONObject()
            val allEntries = mutableListOf<Pair<String, JSONObject>>()
            val keys = controls.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = controls.optJSONObject(k) ?: continue
                allEntries.add(k to obj)
            }
            val hdrEntries = allEntries.filter { (k, _) -> k.lowercase() in HDR_CONTROL_KEYS }
            val regularEntries = allEntries.filter { (k, _) -> k.lowercase() !in HDR_CONTROL_KEYS }
            if (hdrEntries.isNotEmpty()) {
                addSectionHeader("HDR")
                hdrEntries.forEach { (k, obj) -> addControlRow(k, obj, isHdr = true) }
                if (regularEntries.isNotEmpty()) addDivider()
            }
            regularEntries.forEach { (k, obj) -> addControlRow(k, obj) }
            scrollView.post { scrollView.scrollTo(0, savedScrollY) }
        }
        startResolutionPoll()
    }

    private fun addSectionHeader(text: String) {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(0xFF4FC3F7.toInt()) // helles Blau
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(6, 10, 6, 4)
        }
        container.addView(tv)
    }

    private fun addDivider() {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 8, 0, 8) }
            setBackgroundColor(0x554FC3F7.toInt())
        }
        container.addView(divider)
    }

    private fun addControlRow(key: String, obj: JSONObject, isHdr: Boolean = false) {
        val min = obj.optInt("min", 0)
        val max = obj.optInt("max", 1)
        val step = if (obj.has("step")) obj.optInt("step", 1).coerceAtLeast(1) else 1
        val default = obj.optInt("default", min)
        val value = obj.optInt("value", default)
        val isInactive = obj.optString("flags").contains("inactive")

        // Track ranges and current values
        controlRanges[key] = Triple(min, max, step)
        currentValues[key] = value

        // German label (fallback: server-supplied name, then key)
        val serverName = obj.optString("name", key)
        val displayName = CONTROL_LABELS[key] ?: CONTROL_LABELS[serverName] ?: serverName

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6)
            if (isHdr) setBackgroundColor(0x1A4FC3F7.toInt())
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val labelColor = if (isHdr) 0xFF4FC3F7.toInt() else android.graphics.Color.WHITE
        val tv = TextView(context).apply {
            text = displayName
            setTextColor(labelColor)
            if (isHdr) typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        labelRow.addView(tv)

        val statusTv = TextView(context).apply {
            text = ""
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(40, LayoutParams.WRAP_CONTENT)
        }
        statusViews[key] = statusTv
        labelRow.addView(statusTv)

        // Menu-Einträge parsen (z.B. auto_exposure: {"1": "Manual Mode", "3": "Aperture Priority Mode"})
        val menuEntriesObj = if (obj.optString("type") == "menu") obj.optJSONObject("menu_entries") else null
        val menuEntries: List<Pair<Int, String>> = menuEntriesObj
            ?.keys()?.asSequence()
            ?.mapNotNull { k -> k.toIntOrNull()?.let { it to menuEntriesObj.getString(k) } }
            ?.sortedBy { it.first }
            ?.toList()
            ?: emptyList()

        // Controls: 0/1 ohne Menü → Checkbox
        if (min == 0 && max == 1 && menuEntries.isEmpty()) {
            val cb = CheckBox(context).apply {
                isChecked = value != 0
                isEnabled = !isInactive
                setOnCheckedChangeListener { _, isChecked ->
                    scheduleSetControl(key, if (isChecked) 1 else 0)
                }
            }
            controlWidgets[key] = cb
            labelRow.addView(cb)
            row.addView(labelRow)
        } else if (menuEntries.isNotEmpty()) {
            // Spinner für Menu-Controls: zeigt echte Bezeichnungen statt roher Zahlen
            val resetBtn = Button(context).apply {
                text = "↺"
                textSize = 11f
                setPadding(8, 0, 8, 0)
                minWidth = 0
                minimumWidth = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 0, 0) }
            }
            labelRow.addView(resetBtn)
            row.addView(labelRow)

            val spinner = Spinner(context).apply {
                tag = key
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                adapter = makeDarkSpinnerAdapter(menuEntries.map { (_, label) -> label })
                isEnabled = !isInactive
            }
            controlWidgets[key] = spinner
            val currentIdx = menuEntries.indexOfFirst { it.first == value }
            if (currentIdx >= 0) spinner.setSelection(currentIdx)

            resetBtn.setOnClickListener {
                statusViews[key]?.text = "⏳"
                baseUrls.forEach { url ->
                    cameraController?.resetControl(url, key) { success, errorDetails ->
                        if (success) {
                            handler.post {
                                val defaultIdx = menuEntries.indexOfFirst { it.first == default }
                                if (defaultIdx >= 0) spinner.setSelection(defaultIdx)
                                currentValues[key] = default
                                statusViews[key]?.text = "✓"
                                handler.postDelayed({ statusViews[key]?.text = "" }, 2000)
                            }
                        } else {
                            statusViews[key]?.text = "✗"
                            val msg = "Reset $displayName:\n${errorDetails ?: "Unbekannter Fehler"}"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            Log.e("ControlsOverlay", "Reset failed for $key: $errorDetails")
                        }
                    }
                }
            }

            spinner.post {
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val actualValue = menuEntries.getOrNull(position)?.first ?: return
                        scheduleSetControl(key, actualValue)
                    }
                }
            }
            row.addView(spinner)
        } else {
            // Slider + Wertanzeige
            val valueTv = TextView(context).apply {
                text = value.toString()
                setTextColor(android.graphics.Color.LTGRAY)
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
            valueTextViews[key] = valueTv
            labelRow.addView(valueTv)
            row.addView(labelRow)

            val seek = SeekBar(context)
            seek.tag = key
            seek.isEnabled = !isInactive
            seekBars[key] = seek
            controlWidgets[key] = seek
            val range = max - min
            val sbMax = range / step
            seek.max = max(1, sbMax)
            seek.progress = ((value - min) / step).coerceIn(0, seek.max)

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val actual = (min + progress * step).coerceIn(min, max)
                    valueTv.text = actual.toString()
                    if (fromUser) scheduleSetControl(key, actual)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    if (range <= 10) {
                        val actual = (min + (sb?.progress ?: 0) * step).coerceIn(min, max)
                        scheduleSetControl(key, actual)
                    }
                }
            })

            // Reset-Button
            val resetBtn = Button(context).apply {
                text = "↺"
                textSize = 11f
                setPadding(8, 0, 8, 0)
                minWidth = 0
                minimumWidth = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 0, 0) }
                setOnClickListener {
                    statusViews[key]?.text = "⏳"
                    baseUrls.forEach { url ->
                        cameraController?.resetControl(url, key) { success, errorDetails ->
                            if (success) {
                                handler.post {
                                    seek.progress = ((default - min) / step).coerceIn(0, seek.max)
                                    valueTv.text = default.toString()
                                    currentValues[key] = default
                                    statusViews[key]?.text = "✓"
                                    handler.postDelayed({ statusViews[key]?.text = "" }, 2000)
                                }
                            } else {
                                statusViews[key]?.text = "✗"
                                val msg = "Reset $displayName:\n${errorDetails ?: "Unbekannter Fehler"}"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                Log.e("ControlsOverlay", "Reset failed for $key: $errorDetails")
                            }
                        }
                    }
                }
            }
            labelRow.addView(resetBtn)
            row.addView(seek)
        }

        row.alpha = if (isInactive) 0.4f else 1.0f
        controlRows[key] = row
        container.addView(row)
    }

    private fun refreshControlStates() {
        val url = primaryUrl ?: return
        cameraController?.fetchControls(url) { json ->
            val controls = json?.optJSONObject("controls") ?: return@fetchControls
            handler.post {
                val keys = controls.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = controls.optJSONObject(k) ?: continue
                    val inactive = obj.optString("flags").contains("inactive")
                    controlWidgets[k]?.isEnabled = !inactive
                    controlRows[k]?.alpha = if (inactive) 0.4f else 1.0f
                }
            }
        }
    }

    private fun scheduleSetControl(key: String, value: Int) {
        isDirty = true
        pendingRunnables[key]?.let { handler.removeCallbacks(it) }
        statusViews[key]?.text = "⏳"

        val r = Runnable {
            currentValues[key] = value
            var remaining = baseUrls.size
            var anyFail = false
            if (baseUrls.isEmpty()) return@Runnable
            baseUrls.forEach { url ->
                cameraController?.setControl(url, key, value) { success ->
                    if (!success) anyFail = true
                    remaining--
                    if (remaining == 0) {
                        if (!anyFail) {
                            statusViews[key]?.text = "✓"
                            handler.postDelayed({ statusViews[key]?.text = "" }, 2000)
                            handler.postDelayed({ refreshControlStates() }, 500)
                        } else {
                            statusViews[key]?.text = "✗"
                            val label = CONTROL_LABELS[key] ?: key
                            Toast.makeText(context, "Setzen von \"$label\" fehlgeschlagen", Toast.LENGTH_SHORT).show()
                            Log.w("ControlsOverlay", "setControl $key=$value failed on at least one URL")
                            handler.postDelayed({ statusViews[key]?.text = "" }, 3000)
                        }
                    }
                }
            }
        }
        pendingRunnables[key] = r
        handler.postDelayed(r, 300)
    }

    // =============================
    // Auto-Tune (Helligkeits-Einmessung)
    // =============================

    private fun startAutoTune(btn: Button) {
        val pairs = baseUrls.zip(frameViews)
        if (pairs.isEmpty()) {
            handler.post {
                btn.isEnabled = true
                btn.text = "🎯 Einmessen"
            }
            return
        }

        val results = java.util.Collections.synchronizedList(mutableListOf<CamResult>())
        var outstanding = pairs.size

        // Ausstehende Slider-Debounce-Runnables löschen, damit sie kein Auto-Tune-Ergebnis überschreiben
        pendingRunnables.values.forEach { handler.removeCallbacks(it) }
        pendingRunnables.clear()

        pairs.forEachIndexed { idx, (url, view) ->
            val camLabel = if (pairs.size == 1) "" else "Kamera ${idx + 1}"
            val gainRange  = controlRanges["gain"] ?: Triple(1, 16, 1)
            val expRange   = controlRanges["exposure_time_absolute"] ?: Triple(10, 660, 1)

            val beforeGain = currentValues["gain"] ?: gainRange.first
            val beforeExp  = currentValues["exposure_time_absolute"] ?: expRange.first

            Thread {
                var gain = beforeGain
                var exp  = beforeExp
                val startMs = System.currentTimeMillis()
                val gainMin  = gainRange.first;  val gainMax  = gainRange.second; val gainStep  = gainRange.third
                val expMin   = expRange.first;   val expMax   = expRange.second;  val expStep   = expRange.third

                // Immer auf manuellen Belichtungsmodus umschalten — ohne Cache-Prüfung,
                // da ein veralteter Cache dazu führt dass die Kamera in Aperture Priority
                // bleibt und alle exposure_time_absolute-Befehle still ignoriert.
                setControlSyncAndWait(url, "auto_exposure", 1)

                // Neutralen Startpunkt setzen: min Gain, Mitte des Belichtungsbereichs.
                // Verhindert dass der Algorithmus sofort abbricht wenn currentValues
                // zufällig am Minimum liegen aber das Bild trotzdem überbelichtet ist.
                gain = gainMin
                exp  = (expMin + expMax) / 2
                setControlSyncAndWait(url, "gain", gain)
                setControlSyncAndWait(url, "exposure_time_absolute", exp)

                // 3 Frames überspringen: Moduswechsel auto_exposure + neue Belichtung
                // braucht mehrere Frames bis sie im Sensor sichtbar sind
                awaitNextFrame(view, 3_000)
                awaitNextFrame(view, 3_000)
                awaitNextFrame(view, 3_000)

                while (System.currentTimeMillis() - startMs < 120_000L) {
                    val frame = awaitNextFrame(view, 5_000) ?: break
                    val luma  = computeAverageLuma(frame)
                    if (abs(luma - 128) <= 15) break  // optimal

                    val atExpMin  = exp  <= expMin
                    val atExpMax  = exp  >= expMax
                    val atGainMin = gain <= gainMin
                    val atGainMax = gain >= gainMax

                    if (luma < 128) {
                        if (!atExpMax) {
                            // Multiplikativer Schritt: proportionale Annäherung ohne Überschwingen.
                            // Beispiel: luma=64, exp=335 → newExp = 335*128/64 = 670 → coerce zu expMax
                            val newExp = ((exp.toLong() * 128L) / luma).toInt()
                            exp = newExp.coerceIn(exp + expStep, expMax)
                            setControlSyncAndWait(url, "exposure_time_absolute", exp)
                        } else if (!atGainMax) {
                            gain = (gain + gainStep).coerceAtMost(gainMax)
                            setControlSyncAndWait(url, "gain", gain)
                        } else break
                    } else {
                        if (!atExpMin) {
                            // Multiplikativer Schritt: proportionale Reduktion ohne Überschwingen.
                            // Beispiel: luma=200, exp=335 → newExp = 335*128/200 = 214 (statt -311 linear)
                            val newExp = ((exp.toLong() * 128L) / luma).toInt()
                            exp = newExp.coerceIn(expMin, exp - expStep)
                            setControlSyncAndWait(url, "exposure_time_absolute", exp)
                        } else if (!atGainMin) {
                            gain = (gain - gainStep).coerceAtLeast(gainMin)
                            setControlSyncAndWait(url, "gain", gain)
                        } else break
                    }

                    // 2 Frames warten nach jeder Anpassung, damit der Sensor die neue
                    // Belichtungszeit anwenden kann, bevor wir erneut messen
                    awaitNextFrame(view, 3_000)
                    awaitNextFrame(view, 3_000)
                }

                results.add(CamResult(
                    camLabel,
                    mapOf("gain" to beforeGain, "exposure_time_absolute" to beforeExp),
                    mapOf("gain" to gain, "exposure_time_absolute" to exp)
                ))

                if (idx == 0) {
                    handler.post {
                        updateSliderUI("gain", gain)
                        updateSliderUI("exposure_time_absolute", exp)
                    }
                }

                outstanding--
                if (outstanding == 0) {
                    handler.post {
                        btn.isEnabled = true
                        btn.text = "🎯 Einmessen"
                        showAutoTuneDialog(results)
                    }
                }
            }.start()
        }
    }

    private fun awaitNextFrame(view: MjpegTextureView, timeoutMs: Long): ByteArray? {
        var result: ByteArray? = null
        val latch = CountDownLatch(1)
        view.onNextFrameCallback = { bytes ->
            result = bytes
            latch.countDown()
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (result == null) view.onNextFrameCallback = null  // Timeout: cleanup
        return result
    }

    private fun setControlSyncAndWait(url: String, key: String, value: Int) {
        val latch = CountDownLatch(1)
        cameraController?.setControl(url, key, value) { _ -> latch.countDown() }
            ?: latch.countDown()
        latch.await(5, TimeUnit.SECONDS)
        currentValues[key] = value
    }

    private fun computeAverageLuma(jpegBytes: ByteArray): Int {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return 128
        val sampleStep = max(1, bitmap.width / 40)
        var total = 0L
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                total += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                count++
                x += sampleStep
            }
            y += sampleStep
        }
        bitmap.recycle()
        return if (count == 0) 128 else (total / count).toInt()
    }

    private fun updateSliderUI(key: String, value: Int) {
        val (min, _, step) = controlRanges[key] ?: return
        val sb = seekBars[key] ?: return
        sb.progress = ((value - min) / step).coerceIn(0, sb.max)
        valueTextViews[key]?.text = value.toString()
        currentValues[key] = value
    }

    private fun showAutoTuneDialog(results: List<CamResult>) {
        val sb = StringBuilder()
        for (cr in results) {
            if (cr.label.isNotEmpty()) sb.append("${cr.label}:\n")
            val gainBefore = cr.before["gain"]
            val gainAfter  = cr.after["gain"]
            val expBefore  = cr.before["exposure_time_absolute"]
            val expAfter   = cr.after["exposure_time_absolute"]
            if (gainBefore != gainAfter)
                sb.append("  ${CONTROL_LABELS["gain"] ?: "Verstärkung"}: $gainBefore → $gainAfter\n")
            if (expBefore != expAfter)
                sb.append("  ${CONTROL_LABELS["exposure_time_absolute"] ?: "Belichtungszeit"}: $expBefore → $expAfter\n")
        }
        val msg = if (sb.isEmpty()) "Bild war bereits optimal." else sb.toString().trim()
        AlertDialog.Builder(context)
            .setTitle("🎯 Einmessen abgeschlossen")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // =============================
    // Auflösungs-Polling
    // =============================

    /** Einmalige Abfrage der tatsächlichen Kamera-Auflösung → aktualisiert "Ist:"-Label und Spinner. */
    private fun pollActualResolution() {
        val url = primaryUrl ?: return
        cameraController?.getResolution(url) { info ->
            if (info != null) {
                actualResolutionTv?.text = "Ist: ${info.width}×${info.height} @ ${info.fps} fps"
                val idx = RESOLUTION_OPTIONS.indexOfFirst {
                    it.width == info.width && it.height == info.height && it.fps == info.fps
                }
                if (idx >= 0 && idx != committedResolutionIdx) {
                    updatingResSpinnerProgrammatically = true
                    resSpinnerRef?.setSelection(idx)
                    committedResolutionIdx = idx
                    resSpinnerRef?.post { updatingResSpinnerProgrammatically = false }
                }
            } else {
                actualResolutionTv?.text = "Ist: nicht erreichbar"
            }
        }
    }

    /** Startet periodisches Polling der tatsächlichen Kamera-Auflösung (alle 10s). */
    private fun startResolutionPoll() {
        resolutionPollRunnable?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                pollActualResolution()
                if (visibility == View.VISIBLE) handler.postDelayed(this, 10_000L)
            }
        }
        resolutionPollRunnable = r
        handler.post(r)
    }

    /** Stoppt das periodische Polling. */
    private fun stopResolutionPoll() {
        resolutionPollRunnable?.let { handler.removeCallbacks(it) }
        resolutionPollRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopResolutionPoll()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) startResolutionPoll() else stopResolutionPoll()
    }
}
