package com.example.cameracontrol

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * Vertikaler Slider für absolute Grad-Einstellung (Servo).
 * Zeigt 0° bis 180° an, Benutzer kann Zielwinkel direkt wählen.
 */
class VerticalAngleSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Winkel-Bereich
    var minAngle = 0f
    var maxAngle = 180f
    
    // Aktueller Zielwinkel
    private var targetAngle = 90f // Mitte als Start
    
    // Callbacks
    var onAngleChanged: ((angle: Float) -> Unit)? = null // Wird bei Bewegung aufgerufen (für UI-Update)
    var onAngleFinished: ((angle: Float) -> Unit)? = null // Wird nur beim Loslassen aufgerufen (für API-Call)
    
    // Visuelle Elemente
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }
    
    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#018606")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#018606")
        style = Paint.Style.FILL
    }
    
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#02b008")
        style = Paint.Style.FILL
    }
    
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.LEFT
    }
    
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val trackWidth = 60f
        val trackLeft = width / 2f - trackWidth / 2f
        val trackRight = width / 2f + trackWidth / 2f
        val trackTop = 80f
        val trackBottom = height - 80f
        val trackHeight = trackBottom - trackTop
        
        // Track (Hintergrund)
        canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, 15f, 15f, trackPaint)
        canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, 15f, 15f, trackStrokePaint)
        
        // Füllstand bis zur aktuellen Position
        val angleRatio = (targetAngle - minAngle) / (maxAngle - minAngle)
        val handleY = trackTop + (1f - angleRatio) * trackHeight // Invertiert: oben = 180°
        canvas.drawRoundRect(trackLeft, handleY, trackRight, trackBottom, 15f, 15f, fillPaint)
        
        // Grad-Markierungen
        for (angle in minAngle.toInt()..maxAngle.toInt() step 30) {
            val ratio = (angle - minAngle) / (maxAngle - minAngle)
            val y = trackTop + (1f - ratio) * trackHeight
            
            canvas.drawLine(trackRight + 5f, y, trackRight + 15f, y, markerPaint)
            textPaint.textSize = 20f
            canvas.drawText("${angle}°", trackRight + 20f, y + 7f, textPaint)
        }
        
        // Handle (Schieber)
        val handleRadius = 35f
        canvas.drawCircle(width / 2f, handleY, handleRadius, handlePaint)
        canvas.drawCircle(width / 2f, handleY, handleRadius, handleStrokePaint)
        
        // Aktueller Winkel im Handle
        textPaint.textSize = 24f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${targetAngle.toInt()}°", width / 2f, handleY + 8f, textPaint)
        
        // Beschriftung
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Servo Winkel", width / 2f, 50f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val trackTop = 80f
                val trackBottom = height - 80f
                val trackHeight = trackBottom - trackTop
                
                // Y-Position in Winkel umrechnen (invertiert)
                val y = event.y.coerceIn(trackTop, trackBottom)
                val ratio = 1f - (y - trackTop) / trackHeight // Invertiert
                targetAngle = (minAngle + ratio * (maxAngle - minAngle)).coerceIn(minAngle, maxAngle)
                
                onAngleChanged?.invoke(targetAngle) // Für UI-Update
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Nur beim Loslassen den finalen Winkel senden
                onAngleFinished?.invoke(targetAngle)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    fun setAngle(angle: Float) {
        targetAngle = angle.coerceIn(minAngle, maxAngle)
        invalidate()
    }
    
    fun getAngle(): Float = targetAngle
}
