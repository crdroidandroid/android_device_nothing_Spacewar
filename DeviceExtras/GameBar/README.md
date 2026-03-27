# GameBar - Real-time Performance Overlay for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)](https://android-arsenal.com/api?level=33)

GameBar is a comprehensive real-time performance monitoring overlay for Android devices. It provides detailed system metrics including FPS, CPU/GPU usage, temperatures, and memory statistics with a customizable floating overlay.

## Features

- **Real-time FPS Monitoring** - Track frame rates with multiple measurement methods
- **CPU Metrics** - Usage percentage, per-core frequencies, and temperature
- **GPU Metrics** - Usage, clock speed, and temperature
- **Memory Stats** - RAM usage, speed, and temperature
- **Battery Temperature** - Monitor device thermal status
- **Customizable Overlay** - Adjustable position, size, colors, and transparency
- **Per-App Configuration** - Auto-enable GameBar for specific applications
- **Logging & Analytics** - Record and analyze performance data
- **Gesture Controls** - Double-tap screenshot, long-press actions
- **Device-Specific Overlays** - Easy hardware path configuration per device

## Screenshots

### GameBar Settings & Customization
<p align="center">
  <img src="readme_resources/settings_01.png" width="200" />
  <img src="readme_resources/settings_02.png" width="200" />
  <img src="readme_resources/settings_03.png" width="200" />
  <img src="readme_resources/settings_04.png" width="200" />
</p>
<p align="center">
  <img src="readme_resources/settings_05.png" width="200" />
  <img src="readme_resources/settings_06.png" width="200" />
  <img src="readme_resources/settings_07.png" width="200" />
  <img src="readme_resources/settings_08.png" width="200" />
</p>
<p align="center">
  <img src="readme_resources/settings_09.png" width="200" />
  <img src="readme_resources/settings_10.png" width="200" />
  <img src="readme_resources/settings_11.png" width="200" />
  <img src="readme_resources/settings_12.png" width="200" />
</p>

### In-Game Overlay
<p align="center">
  <img src="readme_resources/ingame_01.png" width="250" />
  <img src="readme_resources/ingame_02.png" width="250" />
  <img src="readme_resources/ingame_03.png" width="250" />
</p>
<p align="center">
  <img src="readme_resources/ingame_04.png" width="250" />
  <img src="readme_resources/ingame_05.png" width="250" />
  <img src="readme_resources/ingame_06.png" width="250" />
</p>

### Logging & Analytics
<p align="center">
  <img src="readme_resources/logs_01.png" width="200" />
  <img src="readme_resources/logs_02.png" width="200" />
  <img src="readme_resources/logs_03.png" width="200" />
</p>
<p align="center">
  <img src="readme_resources/logs_04.png" width="200" />
  <img src="readme_resources/logs_05.png" width="200" />
</p>

## Requirements

- Android 13 (API 33) or higher
- System-level permissions (privileged app)
- LineageOS or AOSP-based ROM

## Building

### Prerequisites

- AOSP/LineageOS build environment
- Android SDK Platform 33+
- Soong build system

### Integration into Device Tree

1. **Clone the repository** into your ROM source:
   ```bash
   git clone https://github.com/kenway214/packages_apps_GameBar.git /packages/apps/GameBar -b main
   ```

2. **Include in device makefile**:
   
   Add to your `device.mk`:
   ```makefile
   # GameBar Performance Overlay
   $(call inherit-product, packages/apps/GameBar/gamebar.mk)
   ```

3. **Configure hardware paths**:
   
   Edit your device overlay `config.xml`:
   
   ```xml
   <resources>
       <!-- Find your device's thermal zones -->
       <!-- Run: adb shell "ls /sys/class/thermal/thermal_zone*/type" -->
       
       <!-- GPU temperature path -->
       <string name="config_gpu_temp_path">/sys/class/kgsl/kgsl-3d0/temp</string>
       
       <!-- RAM temperature path -->
       <string name="config_ram_temp_path">/sys/class/thermal/thermal_zone78/temp</string>
       
       <!-- Adjust dividers if needed (usually 1000 for millidegrees, 10 for decidegrees) -->
       <integer name="config_cpu_temp_divider">1000</integer>
   </resources>
   ```

4. **Customize init.rc** (if needed):
   
   Edit `packages/apps/GameBar/init/init.gamebar.rc` to match your device's hardware paths.
   Ensure permissions are set for all sysfs nodes used by GameBar.

5. **Build**:
   ```bash
   # Clean build (recommended for first build)
   m clean
   m GameBar
   
   # Or build entire ROM
   brunch <device>
   ```

## Finding Device-Specific Paths

Use these ADB commands to find the correct paths for your device:

```bash
# Find CPU thermal zones
adb shell "for i in /sys/class/thermal/thermal_zone*/type; do echo \$i: \$(cat \$i); done" | grep -i cpu

# Find GPU thermal zones  
adb shell "for i in /sys/class/thermal/thermal_zone*/type; do echo \$i: \$(cat \$i); done" | grep -i gpu

# Find RAM/DDR thermal zones
adb shell "for i in /sys/class/thermal/thermal_zone*/type; do echo \$i: \$(cat \$i); done" | grep -i ddr

# Check GPU paths
adb shell "ls -la /sys/class/kgsl/kgsl-3d0/"

# Check FPS path
adb shell "cat /sys/class/drm/sde-crtc-0/measured_fps"

# Check RAM frequency path
adb shell "cat /sys/devices/system/cpu/bus_dcvs/DDR/cur_freq"
```

## Configuration

### Overlay Customization

GameBar supports runtime customization through Settings:

- **Display Options**: Toggle individual metrics (FPS, CPU, GPU, RAM, temperatures)
- **Visual Style**: Adjust text size, colors, background transparency, corner radius
- **Position**: Choose from 9 predefined positions or enable draggable mode
- **Update Interval**: Set refresh rate (500ms - 5000ms)
- **FPS Method**: Choose between new (SurfaceFlinger) or legacy (sysfs) measurement

### Per-App Auto-Enable

Configure GameBar to automatically activate for specific apps:

1. Open GameBar Settings
2. Navigate to "Per-App GameBar" → "Configure Apps"
3. Select apps from the list
4. GameBar will auto-enable when those apps are in foreground

## Usage

### Quick Settings Tile
- Add GameBar tile to Quick Settings.
- **Tap** to toggle the overlay on/off.
- **Long-press** to open settings.

### Gesture Controls
- **Double-tap overlay:** Start or stop recording performance data (like FPS). This is the primary logging method.
- **Single-tap:** Toggle the overlay's visibility.
- **Long-press:** Configurable action (hide overlay, take a screenshot, or open settings).
- **Drag:** Move the overlay (when draggable mode is enabled).

### How to Record a Performance Log
The logging method has been simplified to a single, manual action.

1.  **Enable the Overlay:** Open **Settings > System > GameBar** and turn on the main **"Enable GameBar Overlay"** switch. The overlay must be visible to start a recording.
2.  **Run a Game:** Open the game or application you want to test.
3.  **Start Recording:** When you are ready, **double-tap** the GameBar overlay. You will see an indicator that recording has started.
4.  **Stop Recording:** Once you are finished, **double-tap** the overlay again. The log file will be saved automatically.

### Viewing Your Recordings
- All recordings are saved in one place:
- Go to **Settings > System > GameBar > GameBar FPS Records** to view your list of saved logs.
- From there, you can view detailed analytics for each session.

## SELinux Policy

GameBar includes SELinux policies for:
- Access to sysfs nodes (thermal, kgsl, drm)
- Overlay window permissions
- System service interactions
- File provider for log sharing

Policies are automatically included via `sepolicy/SEPolicy.mk`.

## Permissions

Required permissions (granted automatically as system app):
- `SYSTEM_ALERT_WINDOW` - Overlay display
- `PACKAGE_USAGE_STATS` - Foreground app detection
- `WRITE_EXTERNAL_STORAGE` - Log file storage
- `ACCESS_SURFACE_FLINGER` - FPS measurement
- `WRITE_SECURE_SETTINGS` - Configuration persistence

## Troubleshooting

### Overlay not showing
- Check overlay permission in Settings → Apps → GameBar
- Verify SELinux is not blocking (check `adb logcat | grep avc`)
- Ensure init.rc permissions are applied (`adb shell ls -l /sys/class/...`)

### Metrics showing "N/A"
- Verify sysfs paths in overlay config.xml match your device
- Check file permissions: `adb shell cat /sys/class/thermal/thermal_zone*/temp`
- Review logcat for file access errors

### Temperature values incorrect
- Adjust divider values in config.xml
- Most devices use 1000 (millidegrees) or 10 (decidegrees)
- Test: `adb shell cat <temp_path>` and divide manually

### Build errors
- Ensure `org.lineageos.settings.resources` is available in your ROM
- Check SettingsLib is included in build
- Verify all resource files are present in res/ directory

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test on your device
5. Submit a pull request

## License

```
Copyright (C) 2025 kenway214

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Credits

- Original concept inspired by various gaming overlays
- Built for LineageOS and AOSP-based ROMs
- Community contributions welcome
- [ColorPicker](https://github.com/jaredrummler/ColorPicker)

## Support

- **Issues**: [GitHub Issues](https://github.com/kenway214/packages_apps_GameBar/issues)
- **XDA Thread**: *Coming soon*
- **Telegram**: [Pandemonium](https://t.me/pandemonium_haydn)

---

**Note**: This is a system application that requires privileged access. It must be built as part of your ROM and cannot be installed as a regular APK.