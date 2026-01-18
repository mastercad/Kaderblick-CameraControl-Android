# Kaderblick Camera System Configuration Example

## Camera System Setup

This document provides example configuration for setting up your Kaderblick Dual Camera Wireless system to work with this Android app.

### Network Configuration

**Recommended Setup:**
- Router/Access Point: 2.4GHz or 5GHz WiFi
- Android Device: Connected to same WiFi network
- Kaderblick Camera System: Connected to same WiFi network

**IP Address Configuration:**
- Static IP recommended for camera system
- Example: `192.168.1.100` (port 8080)
- Ensure port 8080 is not blocked by firewall

### Camera System Requirements

Your Kaderblick camera system should expose the following HTTP endpoints:

#### 1. Status Endpoint
```
GET /status
Response: HTTP 200 OK
```
Used to test connection availability.

#### 2. Capture Endpoint
```
POST /capture
Content-Type: application/json
Body: {"action":"capture","camera":1}

Response: HTTP 200 OK
Body: {"status":"success","camera":1,"filename":"IMG_20260118_103045.jpg"}
```

#### 3. Record Endpoint
```
POST /record
Content-Type: application/json
Body: {"action":"start_recording","camera":1}

Response: HTTP 200 OK
Body: {"status":"success","camera":1,"recording":true}
```

#### 4. Switch Endpoint
```
POST /switch
Content-Type: application/json
Body: {"action":"switch","camera":2}

Response: HTTP 200 OK
Body: {"status":"success","active_camera":2}
```

### Camera ID Mapping

- `camera: 1` - Primary/First camera
- `camera: 2` - Secondary/Second camera
- `camera: 0` - Both cameras (dual mode)

### Troubleshooting

**Connection Issues:**
1. Verify Android device and camera system are on same network
2. Check IP address is correct
3. Ensure port 8080 is accessible
4. Try pinging the camera system IP from another device
5. Check camera system is powered on and WiFi-enabled

**Permission Issues:**
1. Grant Camera permission in Android Settings > Apps > Kaderblick Camera Control
2. Grant Audio permission for video recording
3. Grant Storage permission for saving files (Android 10 and below)

**Camera Issues:**
1. Restart the app
2. Restart camera system
3. Check camera system firmware is up to date
4. Verify camera lenses are not obstructed

### Example Setup Commands

**Test Camera Connection (using curl):**
```bash
# Test status endpoint
curl http://192.168.1.100:8080/status

# Capture photo from camera 1
curl -X POST http://192.168.1.100:8080/capture \
  -H "Content-Type: application/json" \
  -d '{"action":"capture","camera":1}'

# Start recording on camera 2
curl -X POST http://192.168.1.100:8080/record \
  -H "Content-Type: application/json" \
  -d '{"action":"start_recording","camera":2}'

# Stop recording on camera 2
curl -X POST http://192.168.1.100:8080/record \
  -H "Content-Type: application/json" \
  -d '{"action":"stop_recording","camera":2}'

# Switch to dual camera mode
curl -X POST http://192.168.1.100:8080/switch \
  -H "Content-Type: application/json" \
  -d '{"action":"switch","camera":0}'
```

### Network Security

**Important Security Considerations:**

1. **WiFi Security**: Use WPA3 or WPA2 encryption on your WiFi network
2. **Private Network**: Keep camera system on private network, not exposed to internet
3. **Authentication**: Consider adding authentication to camera system endpoints
4. **HTTPS**: For production use, consider implementing HTTPS instead of HTTP
5. **Firewall**: Configure firewall rules to limit access to camera system

### Performance Tips

1. **WiFi Signal**: Ensure strong WiFi signal for both Android device and cameras
2. **Bandwidth**: 5GHz WiFi recommended for better performance with dual cameras
3. **Distance**: Keep devices within 20 meters of router for best performance
4. **Interference**: Avoid interference from other wireless devices
5. **Quality Settings**: Adjust camera quality settings based on network capacity

## Support

For issues specific to the Kaderblick camera hardware, consult the camera system documentation or contact Kaderblick support.

For Android app issues, check the GitHub repository issue tracker.
