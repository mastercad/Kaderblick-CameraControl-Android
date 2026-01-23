package com.example.cameracontrol

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
import kotlin.math.max
import kotlin.math.min

class ControlsOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val title: TextView
    private val container: LinearLayout
    private val btnClose: Button
    private val btnResetAll: Button
    private var baseUrl: String? = null
    private var cameraController: CameraController? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastX = 0f
    private var lastY = 0f

    // debounce map per control
    private val pendingRunnables = mutableMapOf<String, Runnable>()
    // status TextViews per control for feedback
    private val statusViews = mutableMapOf<String, TextView>()

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.controls_overlay, this, true)
        title = v.findViewById(R.id.controls_title)
        container = v.findViewById(R.id.controls_container)
        btnClose = v.findViewById(R.id.btnCloseControls)
        btnResetAll = v.findViewById(R.id.btnResetAll)

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
                    translationX = translationX + dx
                    translationY = translationY + dy
                    lastX = ev.rawX
                    lastY = ev.rawY
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener { visibility = View.GONE }
        btnResetAll.setOnClickListener { baseUrl?.let { resetAll(it) } }
    }

    fun attach(controller: CameraController, baseUrl: String, label: String) {
        this.cameraController = controller
        this.baseUrl = baseUrl
        title.text = "Controls: $label"
        loadControls()
    }

    private fun resetAll(baseUrl: String) {
        cameraController?.resetControl(baseUrl, null) { success, errorDetails ->
            if (success) {
                loadControls()
                Toast.makeText(context, "Controls reset", Toast.LENGTH_SHORT).show()
            } else {
                val msg = "Reset fehlgeschlagen:\n${errorDetails ?: "Unbekannter Fehler"}"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadControls() {
        container.removeAllViews()
        val b = baseUrl ?: return
        cameraController?.fetchControls(b) { json ->
            if (json == null) {
                Toast.makeText(context, "Could not load controls", Toast.LENGTH_SHORT).show()
                return@fetchControls
            }

            val controls = json.optJSONObject("controls") ?: JSONObject()
            val keys = controls.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = controls.optJSONObject(k) ?: continue
                addControlRow(k, obj)
            }
        }
    }

    private fun addControlRow(key: String, obj: JSONObject) {
        val name = obj.optString("name", key)
        val supported = obj.optBoolean("supported", true)
        val min = obj.optInt("min", 0)
        val max = obj.optInt("max", 1)
        val step = if (obj.has("step")) obj.optInt("step", 1) else 1
        val default = obj.optInt("default", min)
        val value = obj.optInt("value", default)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6)
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val tv = TextView(context).apply {
            text = name
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        labelRow.addView(tv)

        // Status indicator
        val statusTv = TextView(context).apply {
            text = ""
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(40, LayoutParams.WRAP_CONTENT)
        }
        statusViews[key] = statusTv
        labelRow.addView(statusTv)

        // Controls: 0/1 => checkbox
        if (min == 0 && max == 1) {
            val cb = CheckBox(context).apply {
                isChecked = value != 0
                setOnCheckedChangeListener { _, isChecked ->
                    scheduleSetControl(key, if (isChecked) 1 else 0)
                }
            }
            labelRow.addView(cb)
            row.addView(labelRow)
        } else {
            // Slider with value display  
            val valueTv = TextView(context).apply {
                text = value.toString()
                setTextColor(android.graphics.Color.LTGRAY)
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
            labelRow.addView(valueTv)
            row.addView(labelRow)

            val seek = SeekBar(context)
            // SeekBar is integer; compute steps
            val range = max - min
            val discrete = range <= 10 // snap for small ranges
            val steps = if (step <= 0) 1 else step
            val sbMax = range / steps
            seek.max = max(1, sbMax)

            // set initial progress
            val prog = ((value - min) / steps)
            seek.progress = prog

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val actual = min + progress * steps
                    valueTv.text = actual.toString()
                    if (fromUser) {
                        // live update (debounced)
                        scheduleSetControl(key, actual)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    // if discrete small range, ensure exact snap
                    if (discrete) {
                        val p = sb?.progress ?: 0
                        val actual = min + p * steps
                        scheduleSetControl(key, actual)
                    }
                }
            })

            row.addView(seek)

            // Reset single control button
            val resetBtn = Button(context).apply {
                text = "Reset"
                setOnClickListener {
                    statusViews[key]?.text = "⏳"
                    baseUrl?.let { url ->
                        cameraController?.resetControl(url, key) { success, errorDetails ->
                            if (success) {
                                statusViews[key]?.text = "✓"
                                handler.postDelayed({ loadControls() }, 300)
                            } else {
                                statusViews[key]?.text = "✗"
                                val msg = "Reset $name:\n${errorDetails ?: "Unbekannter Fehler"}"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                Log.e("ControlsOverlay", "Reset failed for $key: $errorDetails")
                            }
                        }
                    }
                }
            }
            row.addView(resetBtn)
        }

        container.addView(row)
    }

    private fun scheduleSetControl(key: String, value: Int) {
        // cancel previous
        pendingRunnables[key]?.let { handler.removeCallbacks(it) }
        
        // Show waiting indicator
        statusViews[key]?.text = "⏳"
        
        val r = Runnable {
            baseUrl?.let { url ->
                cameraController?.setControl(url, key, value) { success ->
                    if (success) {
                        statusViews[key]?.text = "✓"
                        // Clear success icon after 2 seconds
                        handler.postDelayed({ statusViews[key]?.text = "" }, 2000)
                    } else {
                        statusViews[key]?.text = "✗"
                        Log.w("ControlsOverlay", "setControl $key=$value failed")
                        handler.postDelayed({ statusViews[key]?.text = "" }, 3000)
                    }
                }
            }
        }
        pendingRunnables[key] = r
        handler.postDelayed(r, 300)
    }
}
