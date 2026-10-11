package com.example.cameracontrol

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class SystemMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val chart: LineChart
    private val fileList: ScrollView
    private val fileListText: TextView
    private val viewToggle: ImageButton
    private val healthButton: ImageButton
    private val historySize = 100
    private var cameraBaseUrl: String? = null
    private var cameraLabel: String = "Kamera"
    private var latestSystemInfo: JSONObject? = null
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    // Datenhistorie
    private val cpuHistory = LinkedList<Float>()
    private val ramHistory = LinkedList<Float>()
    private val tempHistory = LinkedList<Float>()
    private val healthHistory = LinkedList<Float>()
    private val timestampHistory = LinkedList<Long>()
    private val diskHistories = mutableMapOf<String, LinkedList<Float>>()
    private val healthTracker = RecordingHealthTracker()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
    
    // Sichtbarkeitsstatus der Linien
    private val visibleLines = mutableMapOf<String, Boolean>(
        "CPU %" to true,
        "RAM %" to true,
        "Temp °C" to true,
        "Aufnahmeproblem" to true,
    )
    
    // Farben für die Linien
    private val colorCpu = Color.rgb(33, 150, 243) // Blau
    private val colorRam = Color.rgb(76, 175, 80) // Grün
    private val colorTemp = Color.rgb(244, 67, 54) // Rot
    private val colorHealth = Color.rgb(255, 0, 255) // Magenta
    private val colorDisk = listOf(
        Color.rgb(255, 152, 0),  // Orange
        Color.rgb(156, 39, 176), // Lila
        Color.rgb(121, 85, 72),  // Braun
        Color.rgb(233, 30, 99)   // Pink
    )

    init {
        chart = LineChart(context)
        addView(chart, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        fileListText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(12), dp(48), dp(12), dp(12))
        }
        fileList = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            visibility = View.GONE
            addView(fileListText)
        }
        addView(fileList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        viewToggle = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_agenda)
            setBackgroundColor(Color.parseColor("#CC333333"))
            contentDescription = "Dateien und Speicher anzeigen"
            setColorFilter(Color.WHITE)
            setOnClickListener { toggleStorageView() }
        }
        addView(viewToggle, LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(4), dp(4), 0)
        })

        healthButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setBackgroundColor(Color.parseColor("#CC333333"))
            setColorFilter(Color.GRAY)
            contentDescription = "Qualitätsereignisse anzeigen"
            setOnClickListener { showHealthDialog() }
        }
        addView(healthButton, LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(4), dp(48), 0)
        })
        
        setupChart()
    }

    fun configureHealth(baseUrl: String, label: String) {
        cameraBaseUrl = baseUrl.trimEnd('/')
        cameraLabel = label
        healthButton.contentDescription = "Qualitätsereignisse für $label anzeigen"
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            
            // X-Achse
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.DKGRAY
                textColor = Color.WHITE
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return timestampHistory.getOrNull(index)?.let { timeFormat.format(Date(it)) } ?: ""
                    }
                }
            }
            
            // Linke Y-Achse (Prozent) - wird dynamisch angepasst
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.DKGRAY
                textColor = Color.WHITE
                setDrawZeroLine(true)
                granularity = 10f // 10%-Schritte für bessere Lesbarkeit
            }
            
            // Rechte Y-Achse (Temperatur)
            axisRight.apply {
                setDrawGridLines(false)
                textColor = colorTemp
                axisMinimum = 0f
                axisMaximum = 100f
            }
            
            // Legende
            legend.apply {
                isEnabled = true
                textColor = Color.WHITE
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false)
                form = Legend.LegendForm.LINE
            }
            
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            
            // Click-Listener für Legende - vereinfachter Ansatz
            setOnChartValueSelectedListener(null)
        }
        
        setupLegendClickListener()
    }
    
    private fun setupLegendClickListener() {
        chart.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                // Berechne ungefähren Legendenbereich (oben links)
                val legendStartY = 50f
                val legendEndY = legendStartY + (visibleLines.size * 40f)
                val legendStartX = 20f
                val legendEndX = 250f
                
                // Prüfe ob im Legendenbereich geklickt wurde
                if (event.x in legendStartX..legendEndX && event.y in legendStartY..legendEndY) {
                    val lineHeight = 40f
                    val clickedIndex = ((event.y - legendStartY) / lineHeight).toInt()
                    
                    val labelOrder = mutableListOf("CPU %", "RAM %", "Temp °C", "Aufnahmeproblem")
                    labelOrder.addAll(diskHistories.keys)
                    
                    if (clickedIndex in labelOrder.indices) {
                        val clickedLabel = labelOrder[clickedIndex]
                        toggleLineVisibility(clickedLabel)
                        return@setOnTouchListener true // Event konsumiert
                    }
                }
            }
            false // Event nicht konsumiert - lässt Parent-Click-Listener durchkommen
        }
    }
    
    private fun toggleLineVisibility(label: String) {
        // Toggle Sichtbarkeit
        val currentVisibility = visibleLines[label] ?: true
        visibleLines[label] = !currentVisibility
        
        // Chart neu zeichnen
        updateChart()
    }

    fun updateData(jsonData: JSONObject) {
        try {
            latestSystemInfo = jsonData
            updateHealthButton(jsonData)
            addToHistory(timestampHistory, System.currentTimeMillis())

            // CPU
            val cpuPercent = jsonData.optDouble("cpu_percent", 0.0).toFloat()
            addToHistory(cpuHistory, cpuPercent)
            android.util.Log.d("SystemMonitorView", "CPU: $cpuPercent% (history size: ${cpuHistory.size})")
            
            // RAM
            val ramPercent = jsonData.optDouble("ram_percent", 0.0).toFloat()
            addToHistory(ramHistory, ramPercent)
            android.util.Log.d("SystemMonitorView", "RAM: $ramPercent% (history size: ${ramHistory.size})")
            
            // Temperature
            val temp = jsonData.optDouble("temperature_celsius", 0.0).toFloat()
            addToHistory(tempHistory, temp)
            android.util.Log.d("SystemMonitorView", "Temp: $temp°C (history size: ${tempHistory.size})")

            // 100 = in diesem Messintervall trat ein Aufnahmeproblem auf, 0 = unauffällig.
            addToHistory(healthHistory, if (healthTracker.sample(jsonData)) 100f else 0f)
            
            // Disks
            val disks = jsonData.optJSONArray("disks")
            if (disks != null) {
                for (i in 0 until disks.length()) {
                    val disk = disks.getJSONObject(i)
                    val mountpoint = disk.optString("mountpoint", "")
                    val percent = disk.optDouble("percent", 0.0).toFloat()
                    
                    // Nur relevante Mountpoints
                    if (isRelevantDisk(mountpoint, disk.optString("device", ""))) {
                        if (!diskHistories.containsKey(mountpoint)) {
                            diskHistories[mountpoint] = LinkedList()
                            visibleLines[mountpoint] = true // Neue Disk standardmäßig sichtbar
                        }
                        addToHistory(diskHistories[mountpoint]!!, percent)
                    }
                }
            }

            updateStorageText(jsonData)
            
            updateChart()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateHealthButton(systemInfo: JSONObject) {
        val health = systemInfo.optJSONObject("camera_health")
        val status = health?.optString("health_status", "ok") ?: "ok"
        val hasQualityProblem = health != null && RecordingHealthTracker.COUNTERS.any {
            health.optLong(it, 0L) > 0L
        }
        val color = when {
            status == "error" || health?.optBoolean("power_undervoltage_now", false) == true ->
                Color.rgb(244, 67, 54)
            status == "warning" || hasQualityProblem -> Color.rgb(255, 152, 0)
            else -> Color.GRAY
        }
        healthButton.setColorFilter(color)
    }

    private fun showHealthDialog() {
        val baseUrl = cameraBaseUrl
        if (baseUrl == null) {
            showHealthText("Qualitätsdiagnose ist noch nicht konfiguriert.")
            return
        }

        val loading = AlertDialog.Builder(context)
            .setTitle("$cameraLabel – Qualitätsereignisse")
            .setMessage("Ereignisse werden geladen …")
            .setNegativeButton("Schließen", null)
            .show()
        val request = Request.Builder().url("$baseUrl/health/events?limit=200").build()
        healthClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                post {
                    loading.dismiss()
                    showHealthText("Ereignisse konnten nicht geladen werden:\n${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string()
                    val text = if (it.isSuccessful && body != null) {
                        formatHealthEvents(JSONObject(body))
                    } else {
                        "Ereignisse konnten nicht geladen werden (HTTP ${it.code})."
                    }
                    post {
                        loading.dismiss()
                        showHealthText(text)
                    }
                }
            }
        })
    }

    private fun formatHealthEvents(response: JSONObject): String {
        val lines = mutableListOf<String>()
        val health = latestSystemInfo?.optJSONObject("camera_health")
        lines += if (health?.optBoolean("recording_active", false) == true) {
            "Aktuelle Aufnahme: läuft"
        } else {
            "Aktuelle Aufnahme: nicht aktiv"
        }
        val issues = health?.optJSONArray("health_issues")
        if (issues != null && issues.length() > 0) {
            lines += "Aktueller Zustand:"
            for (index in 0 until issues.length()) {
                lines += "• ${issues.optString(index)}"
            }
        } else {
            lines += "Aktueller Zustand: keine gemeldete Qualitätsbeeinträchtigung"
        }

        lines += ""
        lines += "Gespeicherte Ereignisse (neueste zuerst):"
        val events = response.optJSONArray("events")
        if (events == null || events.length() == 0) {
            lines += "Keine Ereignisse gespeichert."
        } else {
            for (index in events.length() - 1 downTo 0) {
                val event = events.optJSONObject(index) ?: continue
                val timestamp = event.optString("timestamp")
                    .replace('T', ' ')
                val phase = if (event.optBoolean("recording", false)) "Aufnahme" else "Vorschau"
                val severity = if (event.optString("severity") == "error") "FEHLER" else "WARNUNG"
                lines += "$timestamp · $phase · $severity"
                lines += event.optString("message", event.optString("type", "Unbekanntes Ereignis"))
                lines += ""
            }
        }
        return lines.joinToString("\n").trimEnd()
    }

    private fun showHealthText(text: String) {
        val content = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setTextIsSelectable(true)
            this.text = text
        }
        val scroll = ScrollView(context).apply { addView(content) }
        AlertDialog.Builder(context)
            .setTitle("$cameraLabel – Qualitätsereignisse")
            .setView(scroll)
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun isRelevantDisk(mountpoint: String, device: String): Boolean {
        // Filter wie im Python-Code
        if (mountpoint.startsWith("/snap") || 
            mountpoint.startsWith("/run") ||
            mountpoint.startsWith("/boot")) {
            return false
        }
        
        if (device.startsWith("/dev/loop") || 
            device.startsWith("/dev/ram") ||
            device.startsWith("/dev/zram")) {
            return false
        }
        
        return mountpoint == "/" || 
               mountpoint.startsWith("/media") || 
               mountpoint.startsWith("/mnt") ||
               mountpoint.startsWith("/srv") ||
               mountpoint.startsWith("/data") ||
               mountpoint.startsWith("/home")
    }

    private fun addToHistory(history: LinkedList<Float>, value: Float) {
        history.add(value)
        if (history.size > historySize) {
            history.removeFirst()
        }
    }

    private fun addToHistory(history: LinkedList<Long>, value: Long) {
        history.add(value)
        if (history.size > historySize) history.removeFirst()
    }

    private fun updateChart() {
        val dataSets = mutableListOf<LineDataSet>()
        
        android.util.Log.d("SystemMonitorView", "updateChart: CPU history=${cpuHistory.size}, RAM history=${ramHistory.size}, Temp history=${tempHistory.size}")
        android.util.Log.d("SystemMonitorView", "CPU visible=${visibleLines["CPU %"]}, RAM visible=${visibleLines["RAM %"]}, Temp visible=${visibleLines["Temp °C"]}")
        
        // CPU Dataset (linke Y-Achse)
        if (cpuHistory.isNotEmpty() && visibleLines["CPU %"] == true) {
            val cpuEntries = cpuHistory.mapIndexed { index, value ->
                Entry(index.toFloat(), value)
            }
            android.util.Log.d("SystemMonitorView", "Creating CPU dataset with ${cpuEntries.size} entries")
            val cpuDataSet = LineDataSet(cpuEntries, "CPU %").apply {
                color = colorCpu
                lineWidth = 3f // Dicker für bessere Sichtbarkeit
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                axisDependency = YAxis.AxisDependency.LEFT
                setDrawFilled(true) // Füllung aktivieren
                fillColor = colorCpu
                fillAlpha = 50 // Halbtransparent
            }
            dataSets.add(cpuDataSet)
            android.util.Log.d("SystemMonitorView", "CPU dataset added")
        } else {
            android.util.Log.d("SystemMonitorView", "CPU dataset SKIPPED: isEmpty=${cpuHistory.isEmpty()}, visible=${visibleLines["CPU %"]}")
        }
        
        // RAM Dataset (linke Y-Achse)
        if (ramHistory.isNotEmpty() && visibleLines["RAM %"] == true) {
            val ramEntries = ramHistory.mapIndexed { index, value ->
                Entry(index.toFloat(), value)
            }
            android.util.Log.d("SystemMonitorView", "Creating RAM dataset with ${ramEntries.size} entries")
            val ramDataSet = LineDataSet(ramEntries, "RAM %").apply {
                color = colorRam
                lineWidth = 3f // Dicker für bessere Sichtbarkeit
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                axisDependency = YAxis.AxisDependency.LEFT
                setDrawFilled(true) // Füllung aktivieren
                fillColor = colorRam
                fillAlpha = 50 // Halbtransparent
            }
            dataSets.add(ramDataSet)
            android.util.Log.d("SystemMonitorView", "RAM dataset added")
        } else {
            android.util.Log.d("SystemMonitorView", "RAM dataset SKIPPED: isEmpty=${ramHistory.isEmpty()}, visible=${visibleLines["RAM %"]}")
        }
        
        // Temperature Dataset (rechte Y-Achse)
        if (tempHistory.isNotEmpty() && visibleLines["Temp °C"] == true) {
            val tempEntries = tempHistory.mapIndexed { index, value ->
                Entry(index.toFloat(), value)
            }
            val tempDataSet = LineDataSet(tempEntries, "Temp °C").apply {
                color = colorTemp
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                axisDependency = YAxis.AxisDependency.RIGHT
            }
            dataSets.add(tempDataSet)
            
            // Dynamische Temperatur-Achse
            val minTemp = tempHistory.minOrNull() ?: 0f
            val maxTemp = tempHistory.maxOrNull() ?: 100f
            val padding = (maxTemp - minTemp) * 0.1f
            chart.axisRight.apply {
                axisMinimum = (minTemp - padding).coerceAtLeast(0f)
                axisMaximum = maxTemp + padding
            }
        }

        if (healthHistory.isNotEmpty() && visibleLines["Aufnahmeproblem"] == true) {
            dataSets.add(LineDataSet(
                healthHistory.mapIndexed { index, value -> Entry(index.toFloat(), value) },
                "Aufnahmeproblem"
            ).apply {
                color = colorHealth
                lineWidth = 3f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.STEPPED
                axisDependency = YAxis.AxisDependency.LEFT
            })
        }
        
        // Disk Datasets (linke Y-Achse)
        diskHistories.entries.forEachIndexed { idx, (mountpoint, history) ->
            if (history.isNotEmpty() && visibleLines[mountpoint] == true) {
                val diskEntries = history.mapIndexed { index, value ->
                    Entry(index.toFloat(), value)
                }
                val diskDataSet = LineDataSet(diskEntries, mountpoint).apply {
                    color = colorDisk[idx % colorDisk.size]
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR
                    axisDependency = YAxis.AxisDependency.LEFT
                }
                dataSets.add(diskDataSet)
            }
        }
        
        // Dynamische Y-Achse (links) für bessere Sichtbarkeit niedriger Werte
        val allLeftValues = mutableListOf<Float>()
        allLeftValues.addAll(cpuHistory)
        allLeftValues.addAll(ramHistory)
        allLeftValues.addAll(healthHistory)
        diskHistories.values.forEach { allLeftValues.addAll(it) }
        
        if (allLeftValues.isNotEmpty()) {
            val minValue = allLeftValues.minOrNull() ?: 0f
            val maxValue = allLeftValues.maxOrNull() ?: 100f
            
            // Wenn die Werte niedrig sind, Achse auf sinnvollen Bereich setzen
            val axisMin = if (maxValue < 30f) 0f else (minValue - 10f).coerceAtLeast(0f)
            val axisMax = if (maxValue < 30f) 50f else (maxValue + 10f).coerceAtMost(100f)
            
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
            }
        }
        
        // Legende mit visueller Anzeige für ausgeblendete Linien
        val legendEntries = mutableListOf<LegendEntry>()
        
        // CPU
        legendEntries.add(LegendEntry().apply {
            label = "CPU %"
            formColor = if (visibleLines["CPU %"] == true) colorCpu else Color.argb(80, 33, 150, 243)
        })
        
        // RAM
        legendEntries.add(LegendEntry().apply {
            label = "RAM %"
            formColor = if (visibleLines["RAM %"] == true) colorRam else Color.argb(80, 76, 175, 80)
        })
        
        // Temp
        legendEntries.add(LegendEntry().apply {
            label = "Temp °C"
            formColor = if (visibleLines["Temp °C"] == true) colorTemp else Color.argb(80, 244, 67, 54)
        })

        legendEntries.add(LegendEntry().apply {
            label = "Aufnahmeproblem"
            formColor = if (visibleLines["Aufnahmeproblem"] == true) colorHealth else Color.argb(80, 255, 0, 255)
        })
        
        // Disks
        diskHistories.keys.forEachIndexed { idx, mount ->
            legendEntries.add(LegendEntry().apply {
                label = mount
                val diskColor = colorDisk[idx % colorDisk.size]
                formColor = if (visibleLines[mount] == true) diskColor else Color.argb(80, 
                    Color.red(diskColor), Color.green(diskColor), Color.blue(diskColor))
            })
        }
        
        chart.legend.setCustom(legendEntries)
        
        android.util.Log.d("SystemMonitorView", "Final dataSets count: ${dataSets.size}")
        if (dataSets.isNotEmpty()) {
            val lineData = LineData(dataSets.toList())
            chart.data = lineData
            android.util.Log.d("SystemMonitorView", "Chart data set with ${lineData.dataSetCount} datasets")
            chart.notifyDataSetChanged()
            chart.invalidate()
        } else {
            android.util.Log.d("SystemMonitorView", "WARNING: No datasets to display!")
        }
    }
    
    fun clear() {
        cpuHistory.clear()
        ramHistory.clear()
        tempHistory.clear()
        healthHistory.clear()
        timestampHistory.clear()
        diskHistories.clear()
        healthTracker.reset()
        chart.clear()
    }

    private fun toggleStorageView() {
        val showStorage = fileList.visibility != View.VISIBLE
        fileList.visibility = if (showStorage) View.VISIBLE else View.GONE
        chart.visibility = if (showStorage) View.GONE else View.VISIBLE
        viewToggle.setImageResource(
            if (showStorage) android.R.drawable.ic_menu_view else android.R.drawable.ic_menu_agenda
        )
        viewToggle.contentDescription = if (showStorage) "Diagramm anzeigen" else "Dateien und Speicher anzeigen"
    }

    private fun updateStorageText(jsonData: JSONObject) {
        val lines = mutableListOf<String>()
        lines += "SPEICHER"
        val disks = jsonData.optJSONArray("disks")
        if (disks != null) {
            for (i in 0 until disks.length()) {
                val disk = disks.optJSONObject(i) ?: continue
                if (isRelevantDisk(disk.optString("mountpoint"), disk.optString("device"))) {
                    lines += "${disk.optString("mountpoint")}: ${disk.optString("used")} / ${disk.optString("total")} (${disk.optDouble("percent").toInt()} %)"
                }
            }
        }
        jsonData.optString("disk_space_error").takeIf { it.isNotBlank() }?.let {
            lines += "⚠ $it"
        }

        lines += ""
        lines += "AUFNAHMEN"
        val files = jsonData.optJSONArray("output_files")
        if (files == null || files.length() == 0) {
            lines += "Keine Dateien vorhanden"
        } else {
            val entries = mutableListOf<Pair<String, String>>()
            for (i in 0 until files.length()) {
                files.optJSONObject(i)?.let { entries += it.optString("name") to it.optString("size") }
            }
            entries.sortedByDescending { it.first }.forEach { (name, size) -> lines += "$name  ·  $size" }
        }
        fileListText.text = lines.joinToString("\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
