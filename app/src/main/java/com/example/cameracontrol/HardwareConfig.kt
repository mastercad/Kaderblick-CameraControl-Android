package com.example.cameracontrol

object HardwareConfig {
    
    // =============================
    // SERVO KONFIGURATION
    // =============================

    const val SERVO_MIN_ANGLE = 0f
    const val SERVO_MAX_ANGLE = 180f

    val SERVO_DEFAULT_ANGLE = (SERVO_MIN_ANGLE + SERVO_MAX_ANGLE) / 2f

    /** Servo-Schwelle für Bewegungen (Mindeständerung in Grad) */
    const val SERVO_THRESHOLD = 2f
    
    
    // =============================
    // STEPPER KONFIGURATION
    // =============================
    
    /** Standard-Verzögerung zwischen Stepper-Schritten (Sekunden) */
    const val STEPPER_DEFAULT_DELAY = 0.002
    
    /** Minimale Stepper-Geschwindigkeit (Steps/Tick) */
    const val STEPPER_MIN_SPEED = -30
    
    /** Maximale Stepper-Geschwindigkeit (Steps/Tick) */
    const val STEPPER_MAX_SPEED = 30
    
    /** Dead-Zone für Stepper-Kontrolle (normalisiert 0-1) */
    const val STEPPER_DEAD_ZONE = 0.15f
    
    
    // =============================
    // TIMING KONFIGURATION
    // =============================
    
    /** Intervall für Stepper-Updates (Millisekunden) - Angepasst für Video-Aufnahme */
    const val STEPPER_UPDATE_INTERVAL = 500L
    
    /** Intervall für Servo-Updates (Millisekunden) */
    const val SERVO_UPDATE_INTERVAL = 500L
    
    /** Debounce-Verzögerung für Servo-Angle-Setter (Millisekunden) */
    const val SERVO_DEBOUNCE_DELAY = 500L
    
    
    // =============================
    // SYSTEM MONITORING
    // =============================
    
    /** Status-Polling-Intervall (Millisekunden) */
    const val STATUS_POLL_INTERVAL = 2000L
    
    /** Recording-Status-Polling-Intervall (Millisekunden) */
    const val RECORDING_POLL_INTERVAL = 2000L
}
