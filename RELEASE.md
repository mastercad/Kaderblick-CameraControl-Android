# Automatische Veröffentlichung

Jeder Push auf `main` führt Unit-Tests und Lint aus, prüft das Zertifikat des
Upload-Keystores, baut ein signiertes Android App Bundle und veröffentlicht es
anschließend vollständig im Google-Play-Track `production`. Der Workflow erzeugt
pro Lauf automatisch einen höheren `versionCode`.

Erforderliche GitHub-Actions-Secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64-kodierter Upload-Keystore
- `ANDROID_KEYSTORE_PASSWORD`: Passwort des Keystores
- `ANDROID_KEY_ALIAS`: Alias des Upload-Schlüssels
- `ANDROID_KEY_PASSWORD`: Passwort des Upload-Schlüssels
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`: vollständiges JSON des
  Play-Console-Dienstkontos

Das Dienstkonto muss in der Google Play Console Zugriff auf
`com.example.cameracontrol` und die Berechtigung zur Veröffentlichung in
Production besitzen. Die App muss vor dem ersten API-Upload einmal in der Play
Console angelegt worden sein.
