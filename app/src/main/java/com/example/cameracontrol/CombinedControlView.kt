package com.example.cameracontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 2D Control View: Horizontal = Stepper (relativ), Vertikal = Servo (absolut)
 * Professionelles Design mit visuellen Guides
 */
class CombinedControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Servo-Winkel-Bereich (aus zentraler Config)
    var minAngle = HardwareConfig.SERVO_MIN_ANGLE
    var maxAngle = HardwareConfig.SERVO_MAX_ANGLE
    
    // Callbacks
    var onStepperControl: ((steps: Int) -> Unit)? = null  // Kontinuierlich während Touch
    var onServoControl: ((angle: Float) -> Unit)? = null  // Nur bei ACTION_UP
    
    // Touch-Status
    private var isTouching = false
    private var touchX = 0f
    private var touchY = 0f
    private var startTouchY = 0f  // Initial finger Y position
    private var startAngleY = 0f  // Initial visual Y position from angle
    private var currentAngle = HardwareConfig.SERVO_DEFAULT_ANGLE
    private var currentStepperSpeed = 0
    
    // Visuals
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1a1a1a.toInt()
        style = Paint.Style.FILL
    }
    
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val touchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt()
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    private val anglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt()
        style = Paint.Style.FILL
        alpha = 80
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        
        // Hintergrund
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        
        // Grid (horizontal und vertikal)
        for (i in 1..3) {
            val x = w * i / 4f
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }
        for (i in 1..3) {
            val y = h * i / 4f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        
        // Zentrale Linien
        canvas.drawLine(centerX, 0f, centerX, h, centerLinePaint)
        canvas.drawLine(0f, centerY, w, centerY, centerLinePaint)
        
        // Winkel-Indikator (vertikaler Balken) - immer anzeigen
        val anglePercent = (currentAngle - minAngle) / (maxAngle - minAngle)
        val barHeight = h * anglePercent
        val rect = RectF(0f, h - barHeight, 40f, h)
        canvas.drawRect(rect, anglePaint)
        
        // Labels
        canvas.drawText("← LINKS", w * 0.25f, h - 20f, labelPaint)
        canvas.drawText("RECHTS →", w * 0.75f, h - 20f, labelPaint)
        
        canvas.save()
        canvas.rotate(-90f, 20f, h * 0.25f)
        canvas.drawText("↑ HOCH", 20f, h * 0.25f + 8f, labelPaint)
        canvas.restore()
        
        canvas.save()
        canvas.rotate(-90f, 20f, h * 0.75f)
        canvas.drawText("↓ RUNTER", 20f, h * 0.75f + 8f, labelPaint)
        canvas.restore()
        
        // Touch-Punkt und aktuelle Werte
        if (isTouching) {
            // Touch-Indikator
            canvas.drawCircle(touchX, touchY, 30f, touchPaint)
            canvas.drawCircle(touchX, touchY, 35f, Paint(touchPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
            })
            
            // Werte anzeigen
            val stepperText = when {
                currentStepperSpeed > 0 -> "→ +${currentStepperSpeed}"
                currentStepperSpeed < 0 -> "← ${currentStepperSpeed}"
                else -> "• STOP"
            }
            
            canvas.drawText(stepperText, centerX, 60f, textPaint)
            canvas.drawText("${currentAngle.toInt()}°", centerX, 100f, textPaint)
        } else {
            // Idle-Text mit aktuellem Winkel
            canvas.drawText("Berühren zum Steuern", centerX, centerY - 40f, labelPaint)
            canvas.drawText("← → Stepper  |  ↑ ↓ Servo", centerX, centerY, labelPaint)
            canvas.drawText("Aktuell: ${currentAngle.toInt()}°", centerX, centerY + 40f, textPaint.apply { 
                color = 0xFF00FF00.toInt()
                textSize = 28f
            })
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                touchX = event.x
                startTouchY = event.y
                
                // Calculate visual Y position from current angle
                val anglePercent = (currentAngle - minAngle) / (maxAngle - minAngle)
                startAngleY = height * (1f - anglePercent)
                touchY = startAngleY
                
                // Horizontale Position → Stepper-Geschwindigkeit
                val centerX = width / 2f
                val offsetX = touchX - centerX
                val normalizedX = (offsetX / centerX).coerceIn(-1f, 1f)
                
                // Dead-Zone in der Mitte
                currentStepperSpeed = if (abs(normalizedX) < HardwareConfig.STEPPER_DEAD_ZONE) {
                    0
                } else {
                    // Min/Max Steps aus Config
                    (normalizedX * HardwareConfig.STEPPER_MAX_SPEED).toInt()
                }
                
                // Stepper kontinuierlich senden (aber Servo-Winkel NICHT ändern)
                onStepperControl?.invoke(currentStepperSpeed)
                
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                isTouching = true
                touchX = event.x
                
                // Calculate Y relative to start position
                val deltaY = event.y - startTouchY
                touchY = (startAngleY + deltaY).coerceIn(0f, height.toFloat())
                
                // Horizontale Position → Stepper-Geschwindigkeit
                val centerX = width / 2f
                val offsetX = touchX - centerX
                val normalizedX = (offsetX / centerX).coerceIn(-1f, 1f)
                
                // Dead-Zone in der Mitte
                currentStepperSpeed = if (abs(normalizedX) < HardwareConfig.STEPPER_DEAD_ZONE) {
                    0
                } else {
                    // Min/Max Steps aus Config
                    (normalizedX * HardwareConfig.STEPPER_MAX_SPEED).toInt()
                }
                
                // Vertikale Position → Servo-Winkel (relativ zur Startposition)
                val anglePercent = (1f - (touchY / height)).coerceIn(0f, 1f)
                currentAngle = minAngle + anglePercent * (maxAngle - minAngle)
                
                // Stepper kontinuierlich senden
                onStepperControl?.invoke(currentStepperSpeed)
                
                invalidate()
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                
                // Stepper stoppen
                onStepperControl?.invoke(0)
                
                // Servo-Position senden (nur beim Loslassen)
                onServoControl?.invoke(currentAngle)
                
                currentStepperSpeed = 0
                invalidate()
            }
        }
        return true
    }
    
    fun setAngle(angle: Float) {
        currentAngle = angle.coerceIn(minAngle, maxAngle)
        invalidate()
    }
}
