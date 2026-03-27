# 🎮 GameBar User Guide
### Real-time Performance Monitoring & Logging

---

## 📊 What is GameBar?

GameBar is a **real-time performance overlay** that displays FPS, CPU/GPU usage, temperatures, RAM stats, and more while you use apps. It includes powerful **logging and analytics** features to record and analyze performance data.

**Perfect for:**
- 🎮 Gaming performance testing & optimization
- 📱 App benchmarking & comparison
- 🔥 Thermal analysis & throttling detection
- ⚡ Battery drain investigation
- 📈 Performance tracking over time

---

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

---

## 🎛️ Customizing What to Log

### Choosing Parameters
You can select which data to log:

1. Go to **GameBar** → **Logging**
2. Check/uncheck parameters:
   - ✅ FPS (Frames Per Second)
   - ✅ Frame Time
   - ✅ Battery Temperature
   - ✅ CPU Usage
   - ✅ CPU Clock Speed
   - ✅ CPU Temperature
   - ✅ RAM Usage
   - ✅ RAM Speed
   - ✅ RAM Temperature
   - ✅ GPU Usage
   - ✅ GPU Clock Speed
   - ✅ GPU Temperature

**Tip:** By default, all are enabled. Disable what you don't need!

---

## 📁 Understanding Your Logs

### Log File Format
Logs are saved as **CSV files** (open with Excel, Google Sheets, etc.)

**Columns:**
```
DateTime | PackageName | FPS | Frame_Time | Battery_Temp | CPU_Usage | ...
```

**Example:**
```csv
2025-01-07 14:30:52,com.tencent.ig,60,16.67,35.2,45,2016MHz,42.5,...
2025-01-07 14:30:53,com.tencent.ig,59,16.95,35.3,47,2150MHz,43.0,...
```

### File Naming

**Per-App Logs:**
```
{package_name}_GameBar_log_{timestamp}.csv

Examples:
📱 com.tencent.ig_GameBar_log_20250107_143052.csv (PUBG)
🌐 com.android.chrome_GameBar_log_20250107_144530.csv (Chrome)
```

**Global Logs:**
```
GameBar_log_{timestamp}.csv

Example:
📊 GameBar_log_20250107_150000.csv
```

---

## 📊 Built-in Analytics & Visualization

### Session Analytics (Per-App Logs Only)

GameBar includes **powerful built-in analytics** for per-app logs! No need for external tools.

#### 📈 How to View Analytics:

1. Go to **GameBar** → **"Per-App GameBar"**
2. Tap your app (e.g., BGMI)
3. Tap any log session
4. **Instant analytics popup** appears!

#### 📊 What You Get:

**Session Information:**
- 📅 Date & time
- ⏱️ Session duration
- 📊 Total samples collected
- 📁 File name

**FPS Statistics:**
- Max FPS, Min FPS, Average FPS
- Variance & Standard Deviation
- **Smoothness percentage** (consistency metric)
- **1% Low FPS** (worst 1% of frames)
- **0.1% Low FPS** (worst 0.1% of frames)
- **Real-time FPS graph** with average line

**Frame Time Analysis:**
- Frame time graph (lower is better)
- Average frame time
- Frame time spikes visualization

**CPU Metrics:**
- Max/Min/Avg CPU usage (%)
- Max/Min/Avg CPU temperature (°C)
- CPU usage over time graph
- CPU temperature over time graph
- Per-core clock speeds graph

**GPU Metrics:**
- Max/Min/Avg GPU usage (%)
- Max/Min/Avg GPU clock speed (MHz)
- Max/Min/Avg GPU temperature (°C)
- GPU usage over time graph
- GPU clock frequency graph
- GPU temperature graph

### 💾 Export Options

From the analytics popup, tap **"⋮ Actions"** to access:

#### 1. **📊 Export Data (CSV)**
- Exports the raw CSV file
- Share via any app (WhatsApp, Email, Drive, etc.)
- Open in Excel, Google Sheets, or any CSV viewer

#### 2. **📸 Save Graphics (PNG)**
- Saves all graphs as PNG images
- Perfect for sharing performance screenshots
- Includes FPS, CPU, GPU, and temperature graphs
- Saved to: `/sdcard/Pictures/GameBar/`

#### 3. **🔗 Share Log**
- Quick share via system share sheet
- Send to friends, forums, or developers
- Includes CSV data file

#### 4. **📂 Open External**
- Opens log file in external CSV viewer
- Use your preferred spreadsheet app
- Full data access for custom analysis

### 🎨 Visual Analysis Tips

**Reading the Graphs:**
- **Green line** = Average value
- **Spikes** = Performance issues or intensive moments
- **Flat lines** = Stable performance
- **Drops** = Lag, stuttering, or throttling

**What to Look For:**
- ✅ **Consistent FPS** = Smooth gameplay
- ⚠️ **FPS drops** = Stuttering/lag
- 🔥 **Rising temps** = Thermal throttling risk
- 📉 **1% Low FPS** = Worst-case performance

### 📱 External Analysis (Optional)

If you prefer external tools:

#### Using Google Sheets:
1. Export CSV from GameBar
2. Upload to Google Drive
3. Open with Google Sheets
4. Create custom charts

#### Using Excel:
1. Transfer CSV to PC
2. Open in Excel
3. Insert → Chart → Line Chart
4. Analyze with pivot tables

---

## 🎨 Visual Examples

### Example: Gaming Session Log

```
Game: PUBG Mobile
Duration: 30 minutes
Result:

📊 Average FPS: 58
⚡ Max FPS: 60
📉 Min FPS: 45 (during intense battle)
🔥 Avg CPU Temp: 42.5°C
🌡️ Max CPU Temp: 48.0°C
```

### Example: App Comparison

```
Comparing Browsers:
Chrome vs Firefox vs Edge

📊 Chrome: 59 FPS avg, 40% CPU
📊 Firefox: 57 FPS avg, 38% CPU  
📊 Edge: 60 FPS avg, 42% CPU
```

---

## ❓ Troubleshooting

### ❌ "GameBar logging enabled but overlay is OFF"
**Problem:** You enabled per-app logging, but GameBar overlay is turned off.

**Why this happens:**
- GameBar **requires the overlay to be ON** to collect performance data
- Without the overlay, there's no data source to log

**Solution:**
1. Go to GameBar settings
2. Turn ON **"Enable GameBar Overlay"** switch
3. You should see the overlay appear on screen
4. Now logging will work automatically!

### ❌ Double-tap doesn't start logging
**Possible causes:**

1. **Double-tap gesture disabled**
   - Go to GameBar → Overlay Gesture Controls
   - Enable "Double-tap to capture"

2. **App has auto-logging enabled**
   - If the app is in your per-app list, it's already logging automatically
   - You'll see: *"This app has auto-logging enabled. Logs are saved automatically."*
   - No need to double-tap!

3. **Wrong logging mode**
   - Manual double-tap only works in **Per-App Logging mode**
   - Check: GameBar → GameBar Logging → Select "Per-App Logging"

4. **GameBar overlay is OFF**
   - Logging requires overlay to be ON
   - Turn on the main GameBar switch

5. **Tapping too slow**
   - Tap faster (like double-tap to unlock phone)

### ❌ Can't find my logs
**Check these locations:**

**For Per-App Logs:**
1. **In-app:** GameBar → Per-App GameBar → Select your app
2. **File manager:** `/sdcard/Documents/GameBar/PerAppLogs/{package_name}/`
3. Search for: `*_GameBar_log_*.csv`

**For Universal Logs:**
1. **In-app:** GameBar → GameBar Logging → View Logs
2. **File manager:** `/sdcard/Documents/GameBar/`
3. Search for: `GameBar_log_*.csv`

### ❌ No data in log file (empty or only header)
**Common causes:**

1. **GameBar overlay was OFF during session**
   - Solution: Turn ON overlay before logging

2. **Session too short (< 2 seconds)**
   - Solution: Log for at least 5-10 seconds to collect meaningful data

3. **App crashed or force closed**
   - Solution: Let the app close naturally or use double-tap to stop

4. **Storage permission denied**
   - Solution: Grant storage permission to GameBar in Android settings

### ❌ Analytics won't open / Shows error
**Possible causes:**

1. **Corrupted log file**
   - Try opening a different session
   - Re-log the session

2. **Log file too large**
   - Very long sessions (>2 hours) may take time to analyze
   - Wait for "Analyzing..." dialog to complete

3. **Missing data columns**
   - Ensure you have FPS, CPU, GPU data enabled in overlay settings

### ❌ Export/Share not working
**Solutions:**

1. **No file manager app**
   - Install a file manager (Files by Google, etc.)

2. **Storage permission denied**
   - Go to Android Settings → Apps → GameBar → Permissions
   - Grant Storage permission

3. **No app to open CSV**
   - Install Google Sheets, Excel, or any CSV viewer

---

## 💡 Pro Tips

### 🎮 For Gamers
1. **Enable per-app logging** for your main games
2. Log a few sessions to find average performance
3. **After updates**, compare logs to see improvements/issues
4. Share logs with devs when reporting lag

### 📱 For Testers
1. Use **global mode** to compare apps
2. Test in identical conditions (same brightness, network, etc.)
3. Let apps run for at least 5 minutes for accurate data
4. **Close background apps** for cleaner results

### 🔥 For Thermal Testing
1. Enable **all temperature sensors** (CPU, GPU, Battery, RAM)
2. Run intensive tasks (gaming, video recording)
3. Watch for **thermal throttling** (FPS drops when temp rises)
4. Compare temperatures across different scenarios

### ⚡ For Battery Investigation
1. Log with GameBar overlay OFF initially
2. Then log with overlay ON
3. **Compare power consumption** (overlay itself uses ~1-2% battery)
4. Find which apps drain the most

---

## 🎯 Quick Reference Card

### Common Scenarios

| I Want To... | Use This Mode | Steps |
|--------------|---------------|-------|
| 🎮 Log every BGMI session automatically | Per-App Auto | Configure BGMI in Per-App GameBar → Turn ON overlay → Just play! |
| 👆 Quick one-time test of any app | Manual (Double-Tap) | Set Per-App mode → Turn ON overlay → Open app → Double-tap → Test → Double-tap |
| 📊 Compare multiple apps in one session | Universal | Set Universal mode → Turn ON overlay → Double-tap → Switch apps → Double-tap |
| 🔥 Check game temperatures & throttling | Per-App Auto | Enable temp sensors in overlay → Configure game → Play normally |
| ⚡ Find which app drains battery most | Universal | Log 1 hour usage → View analytics → Check CPU/GPU usage |
| 📈 View detailed FPS graphs & analytics | Per-App (any) | Per-App GameBar → Select app → Tap session → View analytics |
| 📤 Share performance data with friends | Per-App (any) | Open analytics → Actions → Export CSV or Save PNG |
| 🔍 Compare performance before/after update | Per-App Auto | Log sessions before update → Update app → Log again → Compare |

### Logging Mode Comparison

| Feature | Per-App Auto | Manual (Double-Tap) | Universal |
|---------|--------------|---------------------|-----------|
| **Setup Required** | ✅ Yes (one-time) | ❌ No | ❌ No |
| **Auto Start/Stop** | ✅ Yes | ❌ Manual | ❌ Manual |
| **Works in Any App** | ❌ Only configured | ✅ Yes | ✅ Yes |
| **Separate Logs per App** | ✅ Yes | ✅ Yes | ❌ All in one |
| **Built-in Analytics** | ✅ Yes | ✅ Yes | ⚠️ Limited |
| **Best For** | Regular gaming | Quick tests | Multi-app comparison |

---

## 📞 Need Help?

### Still confused?
1. Check **GAMEBAR_FIXES_SUMMARY.md** for technical details
2. Watch in-app tutorials (if available)
3. Post in XDA/Telegram group with screenshots

### Reporting Issues?
Include:
1. Which method you're using (Per-App/Manual/Global)
2. Screenshot of settings
3. Sample log file (if generated)
4. What you expected vs what happened

---

## ✨ Summary

### The Easiest Way (For Most Users):

**For Automatic Logging:**
1. ✅ Go to **GameBar** → **Per-App GameBar** → **Configure Apps**
2. ✅ Select your favorite games/apps
3. ✅ Go to **GameBar Logging** → Select **"Per-App Logging"**
4. ✅ **Turn ON GameBar overlay** (main switch)
5. ✅ **Just play your games normally**
6. ✅ **Logs save automatically** when you close the app!

**To View Your Logs:**
1. 📊 **GameBar** → **Per-App GameBar** → Select your app
2. 📈 Tap any session to see **detailed analytics with graphs**
3. 💾 Export as CSV or save graphs as PNG images
4. 🔗 Share with friends or developers

### That's it! 🎉

**Key Points to Remember:**
- ⚠️ **GameBar overlay MUST be ON** to collect data
- 🎯 Per-App mode = Automatic logging for configured apps
- 👆 Double-tap = Manual logging for any app (in Per-App mode)
- 🌐 Universal mode = Log everything across all apps
- 📊 Built-in analytics = No need for external tools!

---

**GameBar 12**
