package com.example.cameracontrol

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * Step-basiertes Rad-Interface für präzise Servo-Steuerung.
 * Bewegung nach oben/unten sendet feste Schritte (z.B. +3° oder -3°).
 */
class StepperWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Konfiguration
    private var stepSize = 3f // Grad pro Schritt
    private var sensitivity = 20f // Pixel die man bewegen muss für einen Schritt
    
    // Callbacks
    var onStepUp: (() -> Unit)? = null
    var onStepDown: (() -> Unit)? = null
    var onStepX: ((steps: Int) -> Unit)? = null // Für X-Achse (Stepper)
    
    // Touch-Tracking
    private var lastTouchY = 0f
    private var lastTouchX = 0f
    private var accumulatedY = 0f
    private var accumulatedX = 0f
    private var isTouching = false
    
    // Visuelle Elemente
    private val centerX: Float
        get() = width / 2f
    private val centerY: Float
        get() = height / 2f
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#018606")
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#02b008")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    
    // Animation für visuelles Feedback
    private var flashAlpha = 0
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val radius = min(width, height) / 2f - 20f
        
        // Basis-Kreis
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        
        // Pfeile für Hoch/Runter
        drawArrow(canvas, centerX, centerY - radius * 0.5f, true) // Hoch
        drawArrow(canvas, centerX, centerY + radius * 0.5f, false) // Runter
        
        // Pfeile für Links/Rechts (optional für X-Achse)
        drawSideArrow(canvas, centerX - radius * 0.6f, centerY, true) // Links
        drawSideArrow(canvas, centerX + radius * 0.6f, centerY, false) // Rechts
        
        // Touch-Feedback
        if (isTouching) {
            canvas.drawCircle(centerX, centerY, radius * 0.3f, strokePaint)
        }
        
        // Flash-Effekt bei Schritt
        if (flashAlpha > 0) {
            flashPaint.alpha = flashAlpha
            canvas.drawCircle(centerX, centerY, radius * 0.8f, flashPaint)
            flashAlpha = max(0, flashAlpha - 15)
            invalidate()
        }
        
        // Text
        textPaint.textSize = 32f
        canvas.drawText("${stepSize.toInt()}° pro Schritt", centerX, centerY + radius + 35f, textPaint)
    }
    
    private fun drawArrow(canvas: Canvas, x: Float, y: Float, isUp: Boolean) {
        val size = 30f
        val direction = if (isUp) -1f else 1f
        
        val path = Path().apply {
            moveTo(x, y + direction * size)
            lineTo(x - size * 0.6f, y - direction * size * 0.3f)
            moveTo(x, y + direction * size)
            lineTo(x + size * 0.6f, y - direction * size * 0.3f)
        }
        canvas.drawPath(path, arrowPaint)
    }
    
    private fun drawSideArrow(canvas: Canvas, x: Float, y: Float, isLeft: Boolean) {
        val size = 25f
        val direction = if (isLeft) -1f else 1f
        
        val path = Path().apply {
            moveTo(x + direction * size, y)
            lineTo(x - direction * size * 0.3f, y - size * 0.6f)
            moveTo(x + direction * size, y)
            lineTo(x - direction * size * 0.3f, y + size * 0.6f)
        }
        canvas.drawPath(path, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                lastTouchX = event.x
                accumulatedY = 0f
                accumulatedX = 0f
                isTouching = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = lastTouchY - event.y // Invertiert: nach oben = positiv
                val deltaX = event.x - lastTouchX
                
                accumulatedY += deltaY
                accumulatedX += deltaX
                
                // Y-Achse (Servo) - Schritte nach oben/unten
                while (abs(accumulatedY) >= sensitivity) {
                    if (accumulatedY > 0) {
                        // Nach oben gewischt
                        onStepUp?.invoke()
                        triggerFlash()
                    } else {
                        // Nach unten gewischt
                        onStepDown?.invoke()
                        triggerFlash()
                    }
                    accumulatedY -= sensitivity * sign(accumulatedY)
                }
                
                // X-Achse (Stepper) - Links/Rechts
                val xSteps = (accumulatedX / sensitivity).toInt()
                if (xSteps != 0) {
                    onStepX?.invoke(xSteps * 10) // 10 Steps pro Geste
                    accumulatedX -= xSteps * sensitivity
                    triggerFlash()
                }
                
                lastTouchY = event.y
                lastTouchX = event.x
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun triggerFlash() {
        flashAlpha = 100
        invalidate()
    }
    
    fun setStepSize(size: Float) {
        stepSize = size
        invalidate()
    }
    
    fun setSensitivity(sens: Float) {
        sensitivity = sens
    }
}
