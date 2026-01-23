package com.example.cameracontrol

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import java.util.LinkedList
import kotlin.math.abs

class SystemMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val chart: LineChart
    private val historySize = 100
    
    // Datenhistorie
    private val cpuHistory = LinkedList<Float>()
    private val ramHistory = LinkedList<Float>()
    private val tempHistory = LinkedList<Float>()
    private val diskHistories = mutableMapOf<String, LinkedList<Float>>()
    
    // Sichtbarkeitsstatus der Linien
    private val visibleLines = mutableMapOf<String, Boolean>(
        "CPU %" to true,
        "RAM %" to true,
        "Temp °C" to true
    )
    
    // Farben für die Linien
    private val colorCpu = Color.rgb(33, 150, 243) // Blau
    private val colorRam = Color.rgb(76, 175, 80) // Grün
    private val colorTemp = Color.rgb(244, 67, 54) // Rot
    private val colorDisk = listOf(
        Color.rgb(255, 152, 0),  // Orange
        Color.rgb(156, 39, 176), // Lila
        Color.rgb(121, 85, 72),  // Braun
        Color.rgb(233, 30, 99)   // Pink
    )

    init {
        chart = LineChart(context)
        addView(chart, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        
        setupChart()
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
                    
                    val labelOrder = mutableListOf("CPU %", "RAM %", "Temp °C")
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
            
            updateChart()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        diskHistories.clear()
        chart.clear()
    }
}
