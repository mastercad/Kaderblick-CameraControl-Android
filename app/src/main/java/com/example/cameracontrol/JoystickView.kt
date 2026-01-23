package com.example.cameracontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow
import kotlin.math.sqrt

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Joystick-Eigenschaften
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var hatRadius = 0f
    
    // Aktuelle Joystick-Position
    private var joystickX = 0f
    private var joystickY = 0f
    
    // Dead-Zone Konfiguration
    var deadZoneMin = 0.47f // ~480/1023
    var deadZoneMax = 0.51f // ~520/1023
    
    // Stepper Konfiguration
    var stepperSpeedMin = 10
    var stepperSpeedMax = 100
    
    // Servo Konfiguration  
    var servoSpeedMin = 1f
    var servoSpeedMax = 10f
    
    // Callback für Bewegungen
    var onJoystickMove: ((xSpeed: Int, ySpeed: Float) -> Unit)? = null
    var onJoystickRelease: (() -> Unit)? = null
    
    // Paint-Objekte
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#404040")
        style = Paint.Style.FILL
    }
    
    private val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#606060")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    
    private val hatStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val deadZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FF0000")
        style = Paint.Style.FILL
    }
    
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = (w.coerceAtMost(h) / 2f) * 0.85f
        hatRadius = baseRadius * 0.4f
        
        // Joystick startet in der Mitte
        joystickX = centerX
        joystickY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Zeichne Basis
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(centerX, centerY, baseRadius, baseStrokePaint)
        
        // Zeichne Dead-Zone
        val deadZoneRadius = baseRadius * ((deadZoneMax + deadZoneMin) / 2f)
        canvas.drawCircle(centerX, centerY, deadZoneRadius, deadZonePaint)
        
        // Zeichne Fadenkreuz
        val crossSize = baseRadius * 0.1f
        canvas.drawLine(centerX - crossSize, centerY, centerX + crossSize, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - crossSize, centerX, centerY + crossSize, crosshairPaint)
        
        // Zeichne Joystick-Hat
        canvas.drawCircle(joystickX, joystickY, hatRadius, hatPaint)
        canvas.drawCircle(joystickX, joystickY, hatRadius, hatStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                handleJoystickMove(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetJoystick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleJoystickMove(touchX: Float, touchY: Float) {
        // Berechne Distanz vom Zentrum
        val deltaX = touchX - centerX
        val deltaY = touchY - centerY
        val distance = sqrt(deltaX.pow(2) + deltaY.pow(2))
        
        // Begrenze auf Basis-Radius
        if (distance < baseRadius) {
            joystickX = touchX
            joystickY = touchY
        } else {
            // Normalisiere auf Radius-Grenze
            val angle = kotlin.math.atan2(deltaY, deltaX)
            joystickX = centerX + baseRadius * kotlin.math.cos(angle)
            joystickY = centerY + baseRadius * kotlin.math.sin(angle)
        }
        
        invalidate()
        
        // Berechne Geschwindigkeiten und sende Callback
        calculateAndSendSpeeds()
    }

    private fun resetJoystick() {
        joystickX = centerX
        joystickY = centerY
        invalidate()
        onJoystickRelease?.invoke()
    }

    private fun calculateAndSendSpeeds() {
        // Normalisierte Position (-1.0 bis 1.0)
        val normX = (joystickX - centerX) / baseRadius
        val normY = (joystickY - centerY) / baseRadius
        
        // X-Achse für Stepper - wie in Python
        val xRaw = (normX + 1.0f) / 2.0f // 0.0 bis 1.0
        val xSpeed = calculateStepperSpeed(xRaw)
        
        // Y-Achse für Servo - wie in Python (nach unten = höherer Wert)
        // normY ist positiv wenn Joystick nach unten, negativ wenn nach oben
        val yRaw = (normY + 1.0f) / 2.0f // 0.0 bis 1.0, gleich wie X-Achse
        val ySpeed = calculateServoSpeed(yRaw)
        
        // Debug-Log für Y-Achsen-Probleme
        if (ySpeed != 0f) {
            android.util.Log.d("JoystickView", 
                "normY=${"%.3f".format(normY)} → yRaw=${"%.3f".format(yRaw)} → ySpeed=${"%.1f".format(ySpeed)}")
        }
        
        onJoystickMove?.invoke(xSpeed, ySpeed)
    }

    private fun calculateStepperSpeed(xRaw: Float): Int {
        // Dead-Zone Check
        if (xRaw in deadZoneMin..deadZoneMax) {
            return 0
        }
        
        // Berechne Auslenkung und Richtung
        val deflection: Float
        val direction: Int
        
        if (xRaw < deadZoneMin) {
            deflection = (deadZoneMin - xRaw) / deadZoneMin
            direction = -1
        } else {
            deflection = (xRaw - deadZoneMax) / (1.0f - deadZoneMax)
            direction = 1
        }
        
        // LINEARES Mapping wie in Python (kein .pow() mehr!)
        val normalizedDeflection = deflection.coerceIn(0f, 1f)
        val speed = stepperSpeedMin + (stepperSpeedMax - stepperSpeedMin) * normalizedDeflection
        
        return (speed * direction).toInt()
    }

    private fun calculateServoSpeed(yRaw: Float): Float {
        // Dead-Zone Check
        if (yRaw in deadZoneMin..deadZoneMax) {
            return 0f
        }
        
        // Berechne Auslenkung und Richtung
        val deflection: Float
        val direction: Int
        
        if (yRaw < deadZoneMin) {
            deflection = (deadZoneMin - yRaw) / deadZoneMin
            direction = -1
        } else {
            deflection = (yRaw - deadZoneMax) / (1.0f - deadZoneMax)
            direction = 1
        }
        
        val normalizedDeflection = deflection.coerceIn(0f, 1f)
        val speed = servoSpeedMin + (servoSpeedMax - servoSpeedMin) * normalizedDeflection
        
        return speed * direction
    }
    
    // Methode um Dead-Zone von außen zu setzen (z.B. aus Config)
    fun setDeadZone(min: Float, max: Float) {
        deadZoneMin = min
        deadZoneMax = max
        invalidate()
    }
    
    // Methode um Stepper-Geschwindigkeit zu setzen
    fun setStepperSpeedRange(min: Int, max: Int) {
        stepperSpeedMin = min
        stepperSpeedMax = max
    }
    
    // Methode um Servo-Geschwindigkeit zu setzen
    fun setServoSpeedRange(min: Float, max: Float) {
        servoSpeedMin = min
        servoSpeedMax = max
    }
}
