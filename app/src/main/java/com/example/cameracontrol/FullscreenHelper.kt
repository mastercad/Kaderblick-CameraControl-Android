package com.example.cameracontrol

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class FullscreenHelper(
    private val rootLayout: ViewGroup,
    private val onFullscreenChanged: (Boolean) -> Unit,
    private val onViewRestored: (View) -> Unit = {},
    private val additionalViews: List<View> = emptyList() // Zusätzliche Views die im Fullscreen sichtbar sein sollen
) {
    private var isFullscreen = false
    private var fullscreenView: View? = null
    private var originalParent: ViewGroup? = null
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var originalIndex: Int = -1
    
    fun toggleFullscreen(view: View) {
        if (isFullscreen && fullscreenView == view) {
            exitFullscreen()
        } else if (!isFullscreen) {
            enterFullscreen(view)
        } else {
            exitFullscreen()
            enterFullscreen(view)
        }
    }
    
    private fun enterFullscreen(view: View) {
        // Speichere Original-Daten
        originalParent = view.parent as? ViewGroup
        originalLayoutParams = view.layoutParams
        originalIndex = originalParent?.indexOfChild(view) ?: -1
        
        // Entferne View aus aktuellem Parent
        originalParent?.removeView(view)
        
        // Füge zur Root hinzu (Vollbild)
        val fullscreenParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(view, fullscreenParams)
        
        // Bringe zusätzliche Views nach vorne, behalte aber ihre Sichtbarkeit bei
        additionalViews.forEach { additionalView ->
            additionalView.bringToFront()
        }
        
        fullscreenView = view
        isFullscreen = true
        onFullscreenChanged(true)
    }
    
    private fun exitFullscreen() {
        fullscreenView?.let { view ->
            // Entferne von Root
            rootLayout.removeView(view)
            
            // Füge zurück zum Original-Parent
            if (originalIndex >= 0 && originalParent != null) {
                originalParent?.addView(view, originalIndex, originalLayoutParams)
            }
            
            val restoredView = view
            fullscreenView = null
            isFullscreen = false
            onFullscreenChanged(false)
            
            // Cleanup
            originalParent = null
            originalLayoutParams = null
            originalIndex = -1
            
            onViewRestored(restoredView)
        }
    }
    
    fun isInFullscreen() = isFullscreen
    
    fun exitFullscreenIfActive() {
        if (isFullscreen) {
            exitFullscreen()
        }
    }
}
