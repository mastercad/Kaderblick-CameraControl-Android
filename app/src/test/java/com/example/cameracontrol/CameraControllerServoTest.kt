package com.example.cameracontrol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests für die Y-Achsen-Invert-Logik (Servo) in CameraController.
 * Die Invert-Flags sind pro Kamera getrennt und werden rein im Speicher gehalten.
 */
class CameraControllerServoTest {

    private val cam1 = "http://192.168.178.47:8000"
    private val cam2 = "http://192.168.178.48:8000"
    private lateinit var controller: CameraController

    @Before
    fun setUp() {
        controller = CameraController(cam1, cam2, CoroutineScope(SupervisorJob()))
    }

    // ===========================================================================
    // Y-Achse (Servo) invertieren
    // ===========================================================================

    @Test
    fun servoInvertDefaultIsFalseForCam1() {
        assertFalse(controller.getInvertServo(cam1))
    }

    @Test
    fun servoInvertDefaultIsFalseForCam2() {
        assertFalse(controller.getInvertServo(cam2))
    }

    @Test
    fun setInvertServoTrueForCam1() {
        controller.setInvertServo(cam1, true)
        assertTrue(controller.getInvertServo(cam1))
    }

    @Test
    fun setInvertServoCam1DoesNotAffectCam2() {
        controller.setInvertServo(cam1, true)
        assertFalse(controller.getInvertServo(cam2))
    }

    @Test
    fun setInvertServoTrueForCam2() {
        controller.setInvertServo(cam2, true)
        assertTrue(controller.getInvertServo(cam2))
    }

    @Test
    fun setInvertServoCam2DoesNotAffectCam1() {
        controller.setInvertServo(cam2, true)
        assertFalse(controller.getInvertServo(cam1))
    }

    @Test
    fun setInvertServoFalseResetsCam1() {
        controller.setInvertServo(cam1, true)
        controller.setInvertServo(cam1, false)
        assertFalse(controller.getInvertServo(cam1))
    }

    @Test
    fun setInvertServoFalseResetsCam2() {
        controller.setInvertServo(cam2, true)
        controller.setInvertServo(cam2, false)
        assertFalse(controller.getInvertServo(cam2))
    }

    @Test
    fun invertServoBothCamerasAreIndependent() {
        controller.setInvertServo(cam1, true)
        controller.setInvertServo(cam2, false)
        assertTrue(controller.getInvertServo(cam1))
        assertFalse(controller.getInvertServo(cam2))

        controller.setInvertServo(cam1, false)
        controller.setInvertServo(cam2, true)
        assertFalse(controller.getInvertServo(cam1))
        assertTrue(controller.getInvertServo(cam2))
    }

    @Test
    fun unknownUrlDefaultsToFalseForServo() {
        // Eine URL die weder cam1 noch cam2 ist, fällt in den cam2-Zweig
        val unknown = "http://192.168.178.99:8000"
        assertFalse(controller.getInvertServo(unknown))
    }

    // ===========================================================================
    // X-Achse (Stepper) invertieren
    // ===========================================================================

    @Test
    fun stepperInvertDefaultIsFalseForCam1() {
        assertFalse(controller.getInvertStepper(cam1))
    }

    @Test
    fun stepperInvertDefaultIsFalseForCam2() {
        assertFalse(controller.getInvertStepper(cam2))
    }

    @Test
    fun setInvertStepperTrueForCam1() {
        controller.setInvertStepper(cam1, true)
        assertTrue(controller.getInvertStepper(cam1))
    }

    @Test
    fun setInvertStepperCam1DoesNotAffectCam2() {
        controller.setInvertStepper(cam1, true)
        assertFalse(controller.getInvertStepper(cam2))
    }

    @Test
    fun setInvertStepperTrueForCam2() {
        controller.setInvertStepper(cam2, true)
        assertTrue(controller.getInvertStepper(cam2))
    }

    @Test
    fun setInvertStepperCam2DoesNotAffectCam1() {
        controller.setInvertStepper(cam2, true)
        assertFalse(controller.getInvertStepper(cam1))
    }

    @Test
    fun invertStepperBothCamerasAreIndependent() {
        controller.setInvertStepper(cam1, true)
        controller.setInvertStepper(cam2, false)
        assertTrue(controller.getInvertStepper(cam1))
        assertFalse(controller.getInvertStepper(cam2))
    }

    @Test
    fun setInvertStepperFalseResetsCam1() {
        controller.setInvertStepper(cam1, true)
        controller.setInvertStepper(cam1, false)
        assertFalse(controller.getInvertStepper(cam1))
    }

    // ===========================================================================
    // Servo- und Stepper-Invert sind unabhängig voneinander
    // ===========================================================================

    @Test
    fun servoAndStepperInvertAreIndependentPerCamera() {
        controller.setInvertServo(cam1, true)
        controller.setInvertStepper(cam1, false)
        assertTrue(controller.getInvertServo(cam1))
        assertFalse(controller.getInvertStepper(cam1))

        controller.setInvertServo(cam1, false)
        controller.setInvertStepper(cam1, true)
        assertFalse(controller.getInvertServo(cam1))
        assertTrue(controller.getInvertStepper(cam1))
    }
}
