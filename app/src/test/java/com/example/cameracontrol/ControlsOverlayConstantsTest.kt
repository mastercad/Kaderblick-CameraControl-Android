package com.example.cameracontrol

import org.junit.Assert.*
import org.junit.Test

class ControlsOverlayConstantsTest {

    // ===========================================================================
    // RESOLUTION_OPTIONS
    // ===========================================================================

    @Test
    fun resolutionOptionsHasSixEntries() {
        assertEquals(6, ControlsOverlayView.RESOLUTION_OPTIONS.size)
    }

    @Test
    fun allOptionsHavePositiveWidth() {
        ControlsOverlayView.RESOLUTION_OPTIONS.forEach { opt ->
            assertTrue("width must be > 0 for ${opt.label}", opt.width > 0)
        }
    }

    @Test
    fun allOptionsHavePositiveHeight() {
        ControlsOverlayView.RESOLUTION_OPTIONS.forEach { opt ->
            assertTrue("height must be > 0 for ${opt.label}", opt.height > 0)
        }
    }

    @Test
    fun allOptionsHavePositiveFps() {
        ControlsOverlayView.RESOLUTION_OPTIONS.forEach { opt ->
            assertTrue("fps must be > 0 for ${opt.label}", opt.fps > 0)
        }
    }

    @Test
    fun allOptionsHaveNonEmptyLabel() {
        ControlsOverlayView.RESOLUTION_OPTIONS.forEach { opt ->
            assertTrue("label must not be empty", opt.label.isNotBlank())
        }
    }

    @Test
    fun contains4KOption() {
        val has4K = ControlsOverlayView.RESOLUTION_OPTIONS.any { it.width == 3840 && it.height == 2160 }
        assertTrue("RESOLUTION_OPTIONS must contain a 4K entry", has4K)
    }

    @Test
    fun contains1080pOption() {
        val has1080p = ControlsOverlayView.RESOLUTION_OPTIONS.any { it.width == 1920 && it.height == 1080 }
        assertTrue("RESOLUTION_OPTIONS must contain a 1080p entry", has1080p)
    }

    @Test
    fun contains720pOption() {
        val has720p = ControlsOverlayView.RESOLUTION_OPTIONS.any { it.width == 1280 && it.height == 720 }
        assertTrue("RESOLUTION_OPTIONS must contain a 720p entry", has720p)
    }

    @Test
    fun labelsAreUnique() {
        val labels = ControlsOverlayView.RESOLUTION_OPTIONS.map { it.label }
        assertEquals("All resolution labels must be unique", labels.size, labels.toSet().size)
    }

    @Test
    fun allCombinationsAreUnique() {
        val combos = ControlsOverlayView.RESOLUTION_OPTIONS.map { Triple(it.width, it.height, it.fps) }
        assertEquals("All width×height×fps combinations must be unique", combos.size, combos.toSet().size)
    }
}
