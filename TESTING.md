# Testing Plan & Verification Report (`v1.1.0`)

This report clearly separates checks that were **actually executed** in the development environment from tests that require an **emulator** or the **physical Samsung Galaxy A52s 5G**.

---

## 1. Build Verification (Executed — Passed)

Command executed:
```powershell
.\gradlew.bat --console=plain clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :testapp:assembleDebug
```

Observed result:
- **Status:** `BUILD SUCCESSFUL` (125 tasks executed, 3 up-to-date, 0 compiler warnings).
- **APKs verified via `aapt2`, `apksigner`, `ZipFile`, and `dexdump` (`build-tools;36.0.0`):**
  - `UniversalForceRotation-v1.1.0-debug.apk` (`7,331,996` bytes, `versionCode=110`, signed v1/v2/v3, `targetSdkVersion=37`, `compileSdkVersion=37`)
    - `META-INF/xposed/module.prop`: `minApiVersion=101`, `targetApiVersion=102`, `staticScope=false`, `autoHotReload=true`, `exceptionMode=protective`
    - `META-INF/xposed/java_init.list`: `com.sagor.universalforcerotation.xposed.RotationModuleEntry`
    - DEX verification (`8,545` classes): `RotationModuleEntry` present; `io.github.libxposed.service.XposedProvider` present; `io.github.libxposed.api.XposedModule` **not bundled** (proper `compileOnly`); no legacy `de.robv.android.xposed` classes.
  - `UniversalForceRotation-v1.1.0-release.apk` (`1,854,964` bytes, `versionCode=110`, R8 minified + shrunk, signed)
    - `META-INF/xposed/module.prop` and `META-INF/xposed/java_init.list` intact.
    - DEX verification (`1,355` classes): `RotationModuleEntry` preserved with `<init>()V`, `onModuleLoaded`, `onPackageReady`, `onHotReloading`, `onHotReloaded`; `XposedProvider` present; `XposedModule` **not bundled**.
  - `UniversalForceRotation-TestLab-v1.1.0-debug.apk` (`6,843,656` bytes, `versionCode=110`, signed)

---

## 2. Unit Testing (Executed — Passed: 25 / 25)

Command executed: `.\gradlew.bat :app:testDebugUnitTest`

| Test Suite | Tests | Failures | Errors | Skipped | Time |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `ConfigSerializerTest` | 4 | 0 | 0 | 0 | 0.027s |
| `HookCoordinatorSimulationTest` | 6 | 0 | 0 | 0 | 0.028s |
| `OrientationPolicyEngineTest` | 7 | 0 | 0 | 0 | 0.006s |
| `OrientationStateTrackerTest` | 5 | 0 | 0 | 0 | 0.001s |
| `SafetyGuardTest` | 3 | 0 | 0 | 0 | 0.001s |
| **Total** | **25** | **0** | **0** | **0** | **0.063s** |

Coverage highlights:
- Exact mapping of all 6 `OrientationMode` values to Android `ActivityInfo.SCREEN_ORIENTATION_*` codes.
- Third-party default ON, system-app default OFF, hard protection of `system_server` / `com.android.systemui` / `com.sagor.universalforcerotation` / Vector Manager, and per-app exclusions/custom overrides.
- `RemotePreferences` flat serialization/deserialization round-trip and corrupt-value fallback.
- Duplicate `setRequestedOrientation` suppression (100 consecutive calls -> 1 apply + 99 suppressed), server-side `ActivityRecord` orientation drift re-sync after cooldown, and multi-section sub-activity + Dialog `LayoutParams` simulation.

---

## 3. Emulator Testing (Not Executed)

- **Status:** Not executed in this environment (`emulator` binary and Android AVDs are not installed, and target validation requires real Vector 2.2 + Magisk Zygisk on Android 17).

---

## 4. Real-Device Testing (Pending `v1.1.0` Validation on Samsung Galaxy A52s 5G)

- **Status:** Not yet executed here (`adb devices` reported 0 attached devices during build). User feedback from `v1.0.0` identified a sub-screen portrait lock in TikTok's Messages section, addressed by `v1.1.0`'s expanded Activity lifecycle, `ActivityClient` Binder, `WindowManagerGlobal`, and Split-APK single-init hooks.
- Install `UniversalForceRotation-v1.1.0-release.apk` (or debug) and `UniversalForceRotation-TestLab-v1.1.0-debug.apk`, force-stop target apps after updating, then verify the matrix below:

| # | Scenario | How to Test on A52s 5G | Expected Result |
| :-: | :--- | :--- | :--- |
| 1 | **Portrait-locked app (runtime)** | Open `Rotation Test Lab` -> **3. Runtime Portrait**, rotate phone 90° | Activity rotates cleanly to landscape; `getRequestedOrientation()` reports `FULL_USER (13)` |
| 2 | **Landscape-locked app (runtime)** | Open **4. Runtime Landscape**, hold phone upright | Activity stays/rotates to portrait matching device orientation |
| 3 | **Unspecified orientation app** | `TestLabLauncherActivity` main screen, rotate phone 90°/180°/270° | Rotates smoothly across all 4 orientations when Auto Rotate is ON |
| 4 | **Repeated portrait requests** | Open **5. Aggressive Portrait Loop**, watch attempt counter climb past 20, rotate phone | App remains freely rotatable, no screen flicker/re-lock, no ANR or restart loop |
| 5 | **Manifest-declared portrait** | Open **1. Manifest Portrait** (never calls `setRequestedOrientation()`), rotate phone | Overrides manifest portrait lock via direct `Activity` + `Instrumentation` hooks and rotates freely |
| 6 | **Activity switching & multi-section apps** | Switch across TestLab probe chain, AND open TikTok -> **Messages / Inbox / Chat** | Every sub-activity and sub-section adopts `FULL_USER` and rotates freely |
| 7 | **Dialog window portrait lock** | Open **8. Dialog Window Portrait Lock** in TestLab, rotate phone | Dialog rotates freely via `Window.setAttributes` + `WindowManagerGlobal` hooks |
| 8 | **App restart & device reboot** | Force-stop `Rotation Test Lab`, toggle custom mode, reboot Galaxy A52s 5G, reopen | Configuration persists in Vector `RemotePreferences` and hooks re-engage automatically |
| 9 | **Auto Rotate disabled** | Turn Android **Auto Rotate OFF** while **Follow System Auto Rotate** is selected | Apps stop rotating with sensor and respect system rotation lock |
| 10 | **Auto Rotate enabled** | Turn Android **Auto Rotate ON** | All scoped apps immediately rotate again; translucent check (**7. Translucent Dialog**) opens without crashing |

Live log verification command during physical device testing (with **Verbose Hook Logging** enabled in Exclusions):
```bash
adb logcat -s UniversalForceRotation:* VectorContext:* VectorModuleManager:*
```
