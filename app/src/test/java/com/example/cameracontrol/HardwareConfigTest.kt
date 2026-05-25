package com.example.cameracontrol

import org.junit.Assert.*
import org.junit.Test

class HardwareConfigTest {

    // ===========================================================================
    // Servo-Konstanten
    // ===========================================================================

    @Test
    fun servoMinAngleIsZero() {
        assertEquals(0f, HardwareConfig.SERVO_MIN_ANGLE)
    }

    @Test
    fun servoMaxAngleIs180() {
        assertEquals(180f, HardwareConfig.SERVO_MAX_ANGLE)
    }

    @Test
    fun servoDefaultAngleIsMidpointOfMinAndMax() {
        val expected = (HardwareConfig.SERVO_MIN_ANGLE + HardwareConfig.SERVO_MAX_ANGLE) / 2f
        assertEquals(expected, HardwareConfig.SERVO_DEFAULT_ANGLE, 0.001f)
    }

    @Test
    fun servoDefaultAngleIs90() {
        assertEquals(90f, HardwareConfig.SERVO_DEFAULT_ANGLE, 0.001f)
    }

    @Test
    fun servoThresholdIsPositive() {
        assertTrue(HardwareConfig.SERVO_THRESHOLD > 0f)
    }

    @Test
    fun servoMinIsLessThanMax() {
        assertTrue(HardwareConfig.SERVO_MIN_ANGLE < HardwareConfig.SERVO_MAX_ANGLE)
    }

    // ===========================================================================
    // Stepper-Konstanten
    // ===========================================================================

    @Test
    fun stepperMinSpeedIsNegative() {
        assertTrue(HardwareConfig.STEPPER_MIN_SPEED < 0)
    }

    @Test
    fun stepperMaxSpeedIsPositive() {
        assertTrue(HardwareConfig.STEPPER_MAX_SPEED > 0)
    }

    @Test
    fun stepperSpeedRangeIsSymmetric() {
        assertEquals(-HardwareConfig.STEPPER_MIN_SPEED, HardwareConfig.STEPPER_MAX_SPEED)
    }

    @Test
    fun stepperDeadZoneIsBetweenZeroAndOne() {
        assertTrue(HardwareConfig.STEPPER_DEAD_ZONE >= 0f)
        assertTrue(HardwareConfig.STEPPER_DEAD_ZONE < 1f)
    }

    @Test
    fun stepperDefaultDelayIsPositive() {
        assertTrue(HardwareConfig.STEPPER_DEFAULT_DELAY > 0.0)
    }

    // ===========================================================================
    // Timing-Konstanten
    // ===========================================================================

    @Test
    fun stepperUpdateIntervalIsPositive() {
        assertTrue(HardwareConfig.STEPPER_UPDATE_INTERVAL > 0L)
    }

    @Test
    fun servoUpdateIntervalIsPositive() {
        assertTrue(HardwareConfig.SERVO_UPDATE_INTERVAL > 0L)
    }

    @Test
    fun servoDebounceDelayIsPositive() {
        assertTrue(HardwareConfig.SERVO_DEBOUNCE_DELAY > 0L)
    }

    @Test
    fun statusPollIntervalIsPositive() {
        assertTrue(HardwareConfig.STATUS_POLL_INTERVAL > 0L)
    }

    @Test
    fun recordingPollIntervalIsPositive() {
        assertTrue(HardwareConfig.RECORDING_POLL_INTERVAL > 0L)
    }
}
