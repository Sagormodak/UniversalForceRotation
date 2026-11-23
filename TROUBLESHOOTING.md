# Troubleshooting Guide

## 1. App Still Refuses to Rotate

Check the following in order:
1. **Is the app in Vector's Scope?**
   - Under modern `libxposed` API 102, modules are **not** injected into processes outside the module's scope.
   - Open **Universal Force Rotation -> Apps -> Filter: In Vector Scope** (or check **Vector Manager -> Modules -> Universal Force Rotation -> Scope**) and confirm the app is checked.
   - Restart (force-stop) the target app after adding it to Vector's scope.
2. **Is Android Auto Rotate turned ON?**
   - The default mode **Follow System Auto Rotate** uses `SCREEN_ORIENTATION_FULL_USER`, which intentionally respects Android's Auto Rotate toggle.
   - Check **Universal Force Rotation -> Diagnostics -> System Auto Rotate**. If you want rotation even while Auto Rotate is OFF, select **Full Sensor** (`SCREEN_ORIENTATION_FULL_SENSOR`) for that app or globally.
3. **Did the app just update or get newly installed?**
   - Restart the target app or verify Vector's scope still includes it.

---

## 2. Top Status Card Says "Vector Service Not Connected"

- Confirm **Universal Force Rotation** is **enabled** inside **Vector Manager -> Modules**.
- Confirm **Vector** itself is active (check Vector Manager's home screen).
- Force-close and reopen **Universal Force Rotation** after enabling it in Vector Manager.
- Note: Do **not** switch away from Vector 2.2 or install legacy Xposed bridges; this module uses `io.github.libxposed:service:102.0.0` (`XposedProvider`) directly.

---

## 3. Video Players or Games Behave Strangely

- Games and full-screen video players sometimes use sensor listeners or custom OpenGL/Vulkan surface transforms independently of `Activity.setRequestedOrientation()`.
- If a game/app has broken aspect ratio or distorted UI when rotated, exclude that specific app:
  - **Universal Force Rotation -> Search app -> Switch OFF**, or assign **Force Landscape** / **Force Portrait** specifically for it.

---

## 4. Collecting Logs for Debugging

1. Open **Universal Force Rotation -> Exclusions** and enable **Verbose Hook Logging**.
2. Reproduce the issue in the target app.
3. Inspect logs via **Vector Manager -> Logs -> Modules** or via `adb`:
   ```bash
   adb logcat -s UniversalForceRotation:* VectorContext:* VectorModuleManager:*
   ```
4. Also open **Universal Force Rotation -> Diagnostics -> Copy Diagnostics Report** to capture Vector version, API version, `smallestWidthDp`, Auto Rotate state, and active hooked targets.

---

## 5. Module Refuses to Load in Vector

Verify the APK was built with `compileOnly` for `libxposed:api` (already enforced by `app/build.gradle.kts`) and includes:
- `META-INF/xposed/module.prop` (`minApiVersion=101`, `targetApiVersion=102`)
- `META-INF/xposed/java_init.list` (`com.sagor.universalforcerotation.xposed.RotationModuleEntry`)
- No bundled `io.github.libxposed.api.XposedModule` inside `classes*.dex`.
