# Installation & Usage Guide

Target environment:
- **Device:** Samsung Galaxy A52s 5G (`a52sxq`)
- **ROM / OS:** Infinity X (Android 17)
- **Root:** Magisk with Zygisk enabled
- **Hooking Framework:** Vector 2.2 (3119), Modern libxposed API 102

> **Important:** Do **not** uninstall or replace your existing Vector 2.2 framework. Universal Force Rotation targets Vector's built-in Modern libxposed API 102 directly.

---

## 1. Install the APK

Install the Universal Force Rotation APK from `releases/`:
- `UniversalForceRotation-v1.0.0-release.apk` (recommended; R8 optimized) or
- `UniversalForceRotation-v1.0.0-debug.apk` (debug symbols)

Via ADB (optional):
```bash
adb install -r UniversalForceRotation-v1.0.0-release.apk
```
Optional test harness APK:
```bash
adb install -r UniversalForceRotation-TestLab-v1.0.0-debug.apk
```

---

## 2. Enable in Vector Manager

1. Open **Vector Manager** (from its shortcut or system notification).
2. Navigate to the **Modules** tab.
3. Locate **Universal Force Rotation** (`com.sagor.universalforcerotation`).
   - Vector should display it as a modern libxposed module (`minApiVersion=101`, `targetApiVersion=102`, `staticScope=false`, `autoHotReload=true`).
4. Toggle **Enable Module** **ON**.

---

## 3. Select Target Applications (Scope)

Because modern `libxposed` API 101/102 modules only load inside applications listed in Vector's scope (never globally into Zygote):

1. In Vector Manager -> **Universal Force Rotation** -> **Scope**:
   - Select the third-party apps whose orientation locks you want to override (and optionally `Rotation Test Lab` if testing).
   - Enable **Auto-include new apps** in Vector if desired so newly installed third-party apps are scoped automatically.
2. **Do NOT add critical system packages** such as `System Framework` (`system_server`), `System UI` (`com.android.systemui`), `Phone` (`com.android.phone`), or `Bluetooth` to the scope.
   - Universal Force Rotation also contains an internal `SafetyGuard` that immediately detaches (`detach()`) from `system_server` and critical system packages even if accidentally added.
3. Force-stop or restart the target apps (or **reboot** if you scoped persistent/preloaded apps).

---

## 4. Enable Android Auto Rotate

1. Open Android Quick Settings (swipe down from status bar) or **Settings -> Display**.
2. Turn **Auto rotate** **ON**.
3. With the default **Follow System Auto Rotate** (`SCREEN_ORIENTATION_FULL_USER`) mode:
   - When Android Auto Rotate is **ON**: scoped portrait-locked and landscape-locked apps rotate freely across all 4 orientations as you turn your phone.
   - When Android Auto Rotate is **OFF**: scoped apps obey your system rotation lock instead of snapping back to an app-forced orientation.

---

## 5. Configure Universal Force Rotation

1. Open the **Universal Force Rotation** app from your launcher.
2. Confirm the top status card reads **Vector 2.2 · API 102** (connected).
3. Verify settings on the **Apps** tab:
   - **Master Enable:** ON
   - **Global Force Rotation:** ON
   - **Default Orientation Mode:** `Follow System Auto Rotate` (or choose `Force Portrait`, `Force Landscape`, `Reverse Portrait`, `Reverse Landscape`, or `Full Sensor`)
4. To customize or exclude an individual app:
   - Search for the app or scroll the **Third-Party** list.
   - Flip its switch OFF to exclude it, or tap the app row to open its bottom sheet, assign a custom orientation mode, or request/remove Vector scope.
5. Configuration changes persist in Vector's `RemotePreferences` across app restarts and device reboots.

---

## 6. How to Disable or Recover if an App Crashes

### Disable a Single App
1. Open **Universal Force Rotation**.
2. Search for the misbehaving app and turn its switch **OFF** (or tap it -> toggle OFF -> **Remove from Vector Scope**).
3. Force-stop and reopen that app.

### Pause All Interception Immediately
1. Open **Universal Force Rotation** and turn **Master Enable** **OFF**.
2. Active apps update live via `RemotePreferences` and restore their original orientation on next resume.

### Full Disable in Vector (or Recovery After Crash)
1. Open **Vector Manager** -> **Modules** -> disable **Universal Force Rotation**.
2. If you ever get stuck in a crash loop involving an app, reboot into Safe Mode / Magisk Safe Mode or remove/disable the module via Vector Manager (or `adb uninstall com.sagor.universalforcerotation`). Because the module never modifies system partitions or patches APKs, uninstalling or disabling it immediately reverts all behavior cleanly.
