# CameraControl 📷🎮

Eine professionelle Android-App zur Fernsteuerung von motorisierten Kamera-Rigs mit Echtzeit-Video-Streaming und präziser Hardware-Kontrolle.

![version](https://img.shields.io/github/v/tag/mastercad/Kaderblick-CameraControl-Android) ![platform](https://img.shields.io/badge/platform-Android_24+-green) ![kotlin](https://img.shields.io/badge/kotlin-v1.9-purple) ![license](https://img.shields.io/badge/license-MIT-blue)

## 🌟 Hauptfunktionen

- **Dual-Kamera-Unterstützung**: Steuerung von bis zu zwei Kameras gleichzeitig
- **Echtzeit-MJPEG-Streaming**: Verzögerungsfreie Videovorschau von beiden Kameras
- **Präzise Motorsteuerung**:
  - Stepper-Motor für horizontale Bewegungen (Pan)
  - Servo-Motor für vertikale Bewegungen (Tilt)
- **Intuitive Touch-Bedienung**: Joystick und Winkel-Slider für präzise Kontrolle
- **System-Monitoring**: Echtzeit-Überwachung von CPU, Temperatur und Netzwerkstatus
- **Video-Aufzeichnung**: Remote-Aufzeichnung direkt auf dem Raspberry Pi
- **Fullscreen-Modus**: Maximale Sicht auf die Kamerastreams
- **Automatische Fehlerbehandlung**: Reconnect bei Verbindungsverlust

## 📋 Voraussetzungen

### Hardware
- Android-Gerät mit Android 7.0 (API 24) oder höher
- Raspberry Pi(s) mit Kamera-Server (siehe Backend-Setup unten)
- Motorisiertes Kamera-Rig mit Stepper- und Servo-Motoren
- WLAN-Netzwerk für die Kommunikation

### Software
- Android Studio Hedgehog (2023.1.1) oder neuer
- JDK 11 oder höher
- Gradle 8.0+

## 🚀 Installation

### Option 1: APK herunterladen (Einfachste Methode)
1. Gehe zu [Releases](../../releases)
2. Lade die neueste `CameraControl-vX.X.X-release.apk` herunter
3. Installiere die APK auf deinem Android-Gerät
4. Erlaube "Installation aus unbekannten Quellen" falls nötig

### Option 2: Aus Quellcode bauen
```bash
# Repository klonen
git clone https://github.com/USERNAME/CameraControl.git
cd CameraControl

# Projekt in Android Studio öffnen
# Dann: Build → Build Bundle(s) / APK(s) → Build APK(s)
```

### Option 3: Mit Gradle CLI
```bash
./gradlew assembleRelease
# APK findet sich in: app/build/outputs/apk/release/
```

## ⚙️ Konfiguration

### Kamera-URLs anpassen
Bearbeite die IP-Adressen in [MainActivity.kt](app/src/main/java/com/example/cameracontrol/MainActivity.kt):

```kotlin
private val camera1BaseUrl = "http://192.168.178.47:8000"
private val camera2BaseUrl = "http://192.168.178.48:8000"
```

### Hardware-Parameter anpassen
Die Steuerungsparameter können in [HardwareConfig.kt](app/src/main/java/com/example/cameracontrol/HardwareConfig.kt) angepasst werden:

```kotlin
// Servo-Einstellungen
const val SERVO_MIN_ANGLE = 0f
const val SERVO_MAX_ANGLE = 180f
const val SERVO_THRESHOLD = 2f

// Stepper-Einstellungen
const val STEPPER_MIN_SPEED = -30
const val STEPPER_MAX_SPEED = 30
const val STEPPER_DEAD_ZONE = 0.15f

// Timing
const val STEPPER_UPDATE_INTERVAL = 500L
const val SERVO_UPDATE_INTERVAL = 500L
```

## 🎮 Bedienung

### Hauptbildschirm

```
┌─────────────────────────────────────────┐
│  🔄 Kamera 1      📊 Monitor    🔄 Kamera 2 │
│  ┌───────────┐                ┌───────────┐ │
│  │  Stream   │                │  Stream   │ │
│  │  Video 1  │                │  Video 2  │ │
│  └───────────┘                └───────────┘ │
│                                             │
│  🎮 Steuerung    🔴 Aufnahme   🔧 Optionen  │
└─────────────────────────────────────────────┘
```

### Steuerungselemente

#### 1. Video-Streams
- **Einzelklick auf Stream**: Wechsel in Fullscreen-Modus
- **Einzelklick im Fullscreen**: Zurück zur Dual-Ansicht
- Im Fullscreen wird automatisch nur die angezeigte Kamera gesteuert

#### 2. Kamera-Modi
- **Kamera 1 🎥**: Steuert nur die erste Kamera
- **Kamera 2 🎥**: Steuert nur die zweite Kamera  
- **Beide 🎥🎥**: Synchronisierte Steuerung beider Kameras

#### 3. Joystick (Kombinierte Steuerung)
```
         ↑ Servo hoch
         │
    ← ───┼─── → Stepper links/rechts
         │
         ↓ Servo runter
```

**Bedienung**:
- **X-Achse (horizontal)**: Steuert Stepper-Motor (Pan)
  - Je weiter vom Zentrum, desto schneller die Drehung
  - Dead-Zone in der Mitte verhindert ungewollte Bewegungen
  
- **Y-Achse (vertikal)**: Steuert Servo-Motor (Tilt)
  - Proportionale Geschwindigkeit zur Auslenkung
  - Schwellenwert verhindert Zittern

#### 4. Winkel-Slider
- **Absoluter Servo-Winkel**: 0° - 180°
- Präzise Positionierung durch direkten Winkel
- Debouncing verhindert Überlastung

#### 5. Stepper-Rad (Detaillierte Kontrolle)
- **Links drehen**: Motor dreht gegen den Uhrzeigersinn
- **Rechts drehen**: Motor dreht im Uhrzeigersinn
- **Geschwindigkeit**: Wird durch Drehrate bestimmt
- **Anzeige**: Zentrale Anzeige der aktuellen Steps

#### 6. System-Monitor
Zeigt in Echtzeit:
- **CPU-Auslastung** (%)
- **CPU-Temperatur** (°C)
- **Netzwerk-Latenz** (ms)
- **Verbindungsstatus**

#### 7. Aufnahme-Steuerung
- **🔴 Record**: Startet Videoaufzeichnung auf dem Pi
- **⏹️ Stop**: Beendet die Aufzeichnung
- Status-Anzeige: Zeigt laufende Aufnahmen

#### 8. Erweiterte Einstellungen (⚙️-Button)
- **Servo Speed**: Geschwindigkeit der Servo-Bewegungen
- **Stepper Delay**: Verzögerung zwischen Stepper-Schritten
- **Auto-Home**: Automatische Zentrierung beim Start
- **Shutdown**: Remote-Shutdown des Raspberry Pi

### Gesten und Shortcuts

| Geste/Aktion | Funktion |
|--------------|----------|
| Tap auf Video | Fullscreen Toggle |
| Joystick ziehen | Motor-Steuerung |
| Slider bewegen | Präziser Servo-Winkel |
| Rad drehen | Feinsteuerung Stepper |
| 🎮 Button | Steuerung ein/ausblenden |

## 🏗️ Backend-Setup (Raspberry Pi)

Die App benötigt einen HTTP-Server auf dem Raspberry Pi, der folgende Endpunkte bereitstellt:

### Erforderliche API-Endpunkte

```python
# Beispiel-Struktur der API

# Video-Streaming
GET /preview                    # MJPEG-Stream

# Motor-Steuerung
POST /stepper/move             # Body: {"steps": int, "delay": float}
POST /servo/angle              # Body: {"angle": float}

# System-Status
GET /system/info               # Returns: {cpu_percent, cpu_temp, ping}

# Aufnahme
POST /recording/start          # Startet Aufzeichnung
POST /recording/stop           # Stoppt Aufzeichnung
GET /recording/status          # Returns: {"is_recording": bool}

# Shutdown
POST /system/shutdown          # Fährt Pi herunter
```

### Beispiel-Server mit Flask

```python
from flask import Flask, Response, jsonify, request
import cv2
import time

app = Flask(__name__)

# MJPEG-Streaming
def generate_frames():
    camera = cv2.VideoCapture(0)
    while True:
        success, frame = camera.read()
        if not success:
            break
        ret, buffer = cv2.imencode('.jpg', frame)
        frame_bytes = buffer.tobytes()
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n' + frame_bytes + b'\r\n')

@app.route('/preview')
def preview():
    return Response(generate_frames(),
                   mimetype='multipart/x-mixed-replace; boundary=frame')

# System-Info
@app.route('/system/info')
def system_info():
    return jsonify({
        "cpu_percent": psutil.cpu_percent(),
        "cpu_temp": get_cpu_temperature(),
        "ping": 10  # ms
    })

# Motor-Steuerung
@app.route('/stepper/move', methods=['POST'])
def stepper_move():
    data = request.json
    steps = data.get('steps', 0)
    delay = data.get('delay', 0.002)
    # Implementierung der Stepper-Ansteuerung
    return jsonify({"status": "ok"})

@app.route('/servo/angle', methods=['POST'])
def servo_angle():
    data = request.json
    angle = data.get('angle', 90)
    # Implementierung der Servo-Ansteuerung
    return jsonify({"status": "ok"})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000)
```

### Hardware-Verbindungen (Raspberry Pi)

```
Raspberry Pi GPIO
├── Stepper-Motor
│   ├── Step Pin → GPIO 17
│   ├── Dir Pin → GPIO 27
│   └── Enable Pin → GPIO 22
│
└── Servo-Motor
    ├── PWM Pin → GPIO 18
    └── Power → 5V (ext. Netzteil empfohlen)
```

## 📱 Features im Detail

### MJPEG-Streaming
- Automatische Reconnect-Logik bei Verbindungsabbruch
- Frame-Rate-Limitierung (30 FPS max) für Performance
- Puffer-Management für flüssige Wiedergabe
- Fehler-Overlays bei Verbindungsproblemen

### Motor-Steuerung
- **Throttling**: Verhindert Überlastung durch zu viele Requests
- **Dead-Zone**: Minimiert unbeabsichtigte Mikrobewegungen
- **Non-Blocking**: Alle Requests asynchron via Coroutines
- **Servo-Threshold**: Schwellenwert gegen Servo-Jittering

### Fullscreen-Modus
- Immersive Ansicht mit ausgeblendeten System-Bars
- Automatische Kamera-Selektion (steuert nur angezeigte Kamera)
- Tap zum Verlassen
- Smooth Transitions

### Fehlerbehandlung
- Automatische Server-Erkennung (online/offline)
- Retry-Mechanismus bei fehlgeschlagenen Requests
- Visuelles Feedback bei Verbindungsproblemen
- Graceful Degradation (App läuft weiter bei Server-Ausfall)

## 🛠️ Entwicklung

### Projektstruktur

```
app/src/main/java/com/example/cameracontrol/
├── MainActivity.kt              # Hauptaktivität
├── CameraController.kt          # Motor-Steuerungs-Logik
├── MjpegTextureView.kt         # Video-Streaming
├── JoystickView.kt             # Joystick-UI-Element
├── StepperWheelView.kt         # Stepper-Rad-UI
├── VerticalAngleSlider.kt      # Servo-Slider-UI
├── CombinedControlView.kt      # Kombinierte Steuerung
├── ControlsOverlayView.kt      # Einstellungen-Overlay
├── SystemMonitorView.kt        # System-Monitoring-UI
├── SystemMonitorService.kt     # Monitoring-Service
├── HardwareConfig.kt           # Hardware-Konfiguration
└── FullscreenHelper.kt         # Fullscreen-Verwaltung
```

### Technologien

- **Sprache**: Kotlin 1.9
- **UI**: View-Based (XML Layouts + ViewBinding)
- **Async**: Kotlin Coroutines
- **Networking**: OkHttp 4.12
- **Video**: Custom MJPEG-Decoder mit TextureView
- **Build**: Gradle 8.0 mit Kotlin DSL

### Dependencies

```kotlin
// Core Android
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
androidx.constraintlayout:constraintlayout:2.1.4

// Lifecycle & ViewModel
androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0
androidx.lifecycle:lifecycle-livedata-ktx:2.7.0

// Navigation
androidx.navigation:navigation-fragment-ktx:2.7.6
androidx.navigation:navigation-ui-ktx:2.7.6

// Video (Media3 ExoPlayer)
androidx.media3:media3-exoplayer:1.2.0
androidx.media3:media3-ui:1.2.0

// Networking
com.squareup.okhttp3:okhttp:4.12.0

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0

// Charts (für System-Monitor)
com.github.PhilJay:MPAndroidChart:v3.1.0
```

### Build-Varianten

```bash
# Debug-Build (mit Logging)
./gradlew assembleDebug

# Release-Build (optimiert)
./gradlew assembleRelease

# Tests ausführen
./gradlew test

# Instrumented Tests
./gradlew connectedAndroidTest
```

## 🔄 CI/CD

Automatische Builds über GitHub Actions:
- Build bei jedem Push zu `main`/`master`
- Release-APKs bei Tags (`v*`)
- Automatische Version-Benennung
- Artifact-Upload für Debug und Release

Siehe [.github/workflows/android-release.yml](.github/workflows/android-release.yml)

## 🐛 Bekannte Probleme & Lösungen

### Problem: Video-Stream startet nicht
**Lösung**: 
- Prüfe IP-Adressen in `MainActivity.kt`
- Stelle sicher, dass Raspberry Pi Server läuft
- Port 8000 muss erreichbar sein (Firewall prüfen)

### Problem: Motoren reagieren nicht
**Lösung**:
- Überprüfe Backend-API-Endpunkte
- Logge HTTP-Responses im Android Studio Logcat
- Teste API manuell mit `curl` oder Postman

### Problem: App stürzt ab bei langsamer Verbindung
**Lösung**:
- Erhöhe Timeouts in `CameraController.kt`
- Reduziere Update-Intervalle in `HardwareConfig.kt`

### Problem: Servo zittert/jittert
**Lösung**:
- Erhöhe `SERVO_THRESHOLD` in `HardwareConfig.kt`
- Verlängere `SERVO_UPDATE_INTERVAL`

## 📄 Lizenz

Dieses Projekt steht unter der MIT-Lizenz. Siehe [LICENSE](LICENSE) für Details.

## 👨‍💻 Autor

**Andreas**
- GitHub: [@USERNAME](https://github.com/USERNAME)

## 🙏 Danksagungen

- [OkHttp](https://square.github.io/okhttp/) für robuste HTTP-Kommunikation
- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) für schöne Charts
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) für elegante Async-Programmierung

## 🔮 Roadmap

- [ ] WebRTC-Unterstützung für niedrigere Latenz
- [ ] Kamera-Presets und Positions-Speicherung
- [ ] Zeitraffer-Aufnahme-Modus
- [ ] Multi-Point-Tracking (automatische Kamerafahrten)
- [ ] Bluetooth-Gamepad-Unterstützung
- [ ] Dark/Light Theme-Umschaltung
- [ ] Export von Aufnahmen über App
- [ ] Cloud-Anbindung für Remote-Access

---

**Hinweis**: Diese App wurde für den Einsatz in lokalen Netzwerken entwickelt. Für den Einsatz über das Internet sollten zusätzliche Sicherheitsmaßnahmen (HTTPS, Authentifizierung) implementiert werden.
