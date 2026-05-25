package com.example.cameracontrol

/**
 * Vollständiger Gesundheitsstatus einer Kamera, geparst aus dem /status-Endpoint.
 *
 * [isRecording]         = recording.active – ECHTER Status: nur true wenn Thread lebt UND kein Fehler
 * [captureThreadAlive]  = ob der V4L2-Capture-Thread im camera_service läuft
 * [framesWritten]       = Anzahl tatsächlich in die Datei geschriebener Frames (0 = nichts aufgenommen!)
 * [lastFrameAgeS]       = Sekunden seit dem letzten empfangenen Frame von der Kamera
 * [captureError]        = Fehlermeldung des Capture-Threads (null = kein Fehler)
 * [audioAvailable]      = läuft arecord gerade und ist aktiv
 * [audioError]          = Fehlermeldung wenn arecord abgestürzt ist (null = OK)
 * [videoFileBytes]      = aktuelle Dateigröße der Aufnahmedatei in Bytes (null = keine Datei)
 */
data class CameraHealthStatus(
    val isRecording: Boolean,
    val captureThreadAlive: Boolean,
    val framesWritten: Int,
    val lastFrameAgeS: Double?,
    val captureError: String?,
    val audioAvailable: Boolean,
    val audioError: String?,
    val videoFileBytes: Long?,
) {
    /** Aufnahme läuft und alle Subsysteme sind gesund. */
    val isFullyHealthy: Boolean
        get() = isRecording && captureError == null && captureThreadAlive

    /** Aufnahme läuft, aber Audio fehlt oder ist fehlgeschlagen. */
    val hasAudioProblem: Boolean
        get() = isRecording && (!audioAvailable || audioError != null)

    /** Zusammengefasste Fehlerbeschreibung für die UI (null = kein Fehler). */
    val errorSummary: String?
        get() {
            val errors = mutableListOf<String>()
            if (!captureThreadAlive) errors.add("Capture-Thread tot")
            captureError?.let { errors.add("Video: $it") }
            audioError?.let { errors.add("Audio: $it") }
            if (isRecording && (videoFileBytes ?: 0L) == 0L && framesWritten == 0)
                errors.add("Keine Frames geschrieben – Kamera liefert kein Bild!")
            return errors.joinToString(" | ").ifEmpty { null }
        }
}
