# Universal Force Rotation

[![Android 17](https://img.shields.io/badge/Android-17%20(API%2037)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![libxposed API](https://img.shields.io/badge/libxposed%20API-102.0.0-blue)](https://github.com/libxposed/api)
[![Vector](https://img.shields.io/badge/Vector-2.2%20Supported-purple)](https://github.com/JingMatrix/Vector)
[![Kotlin](https://img.shields.io/badge/Kotlin-AGP%209.3.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Release](https://img.shields.io/badge/Release-v1.1.0-orange)](https://github.com/Sagormodak/UniversalForceRotation/releases/tag/v1.1.0)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey)](LICENSE)

A modern **Xposed module** written in Kotlin for **Android 17**, **Vector 2.2**, and **Modern libxposed API 102** (`io.github.libxposed:api:102.0.0` + `io.github.libxposed:service:102.0.0`) that lets portrait-locked and landscape-locked apps (across all activities, dynamic sub-features, dialogs, and custom views) follow your device's Auto Rotate preference or per-app orientation overrides.

Download prebuilt APKs from **[GitHub Releases (`v1.1.0`)](https://github.com/Sagormodak/UniversalForceRotation/releases/tag/v1.1.0)** or the [`releases/`](releases/) directory.

---

## Target Environment & Why Modern libxposed API 102

Legacy `de.robv.android.xposed.*` modules fail in modern-only Vector configurations with `Cannot support "VectorLegacy" Hook Framework`. Universal Force Rotation uses **strictly Modern libxposed API 102** (`compileOnly("io.github.libxposed:api:102.0.0")`) and `XposedProvider` (`implementation("io.github.libxposed:service:102.0.0")`), with zero legacy Xposed dependencies and zero APK or system-partition patching.

| Component | Target Specification |
| :--- | :--- |
| **Device** | Samsung Galaxy A52s 5G (`a52sxq`) |
| **ROM / OS** | Infinity X — Android 17 (API 37) |
| **Root** | Magisk with Zygisk enabled |
| **Framework** | Vector 2.2 (3119) |
| **Module API** | Modern libxposed API `102.0.0` (`minApiVersion=101`, `targetApiVersion=102`, `autoHotReload=true`, `staticScope=false`) |

---

## Orientation Override Engine Architecture (`v1.1.0`)

```mermaid
flowchart TD
    UI["Companion App UI (MainActivity / ConfigRepository)"] -->|"RemotePreferences IPC (XposedService)"| Entry["RotationModuleEntry (Split-APK Single-Init Guard)"]
    Entry -->|"Detach if system_server / SystemUI / protected"| Guard["SafetyGuard"]
    Entry -->|"Live config + API 102 replaceHook"| Coord["OrientationHookCoordinator"]
    Coord -->|"Policy lookup"| Policy["OrientationPolicyEngine"]
    Coord -->|"Server drift check & loop suppression"| Tracker["OrientationStateTracker"]
    Coord -->|"Translucent/floating safety guard"| Safe["ActivitySafetyChecker"]
    Coord -->|"Hook 1: Runtime & Binder calls"| H1["Activity.setRequestedOrientation & ActivityClient.setRequestedOrientation"]
    Coord -->|"Hook 2: Direct Activity + Instrumentation lifecycle"| H2["Activity.onCreate/onStart/onResume/onWindowFocus + Instrumentation"]
    Coord -->|"Hook 3: In-place Window & Dialog LayoutParams"| H3["Window.setAttributes + WindowManagerImpl + WindowManagerGlobal"]
```

### Multi-Layer Interception Details

1. **Runtime & Binder IPC Interception (`Activity.setRequestedOrientation` + `ActivityClient.setRequestedOrientation`)**
   - Intercepts programmatic orientation locks AND hooks the underlying `android.app.ActivityClient.setRequestedOrientation(IBinder, int)` Binder boundary so no internal framework shortcut can push a portrait/landscape lock to `system_server`.
   - `OrientationStateTracker` checks true `ActivityClient.getRequestedOrientation(mToken)` server state to detect drift across tab/fragment transitions while suppressing duplicate loops (`> 2 requests/sec`) on Android 16/17.
2. **Direct Activity & Instrumentation Lifecycle Hooks (`Activity.onCreate` / `onStart` / `onResume` / `onAttachedToWindow` / `onWindowFocusChanged` + `Instrumentation`)**
   - Hooking `android.app.Activity` lifecycle methods directly ensures custom `Instrumentation` wrappers (common in large apps like ByteDance/TikTok) cannot bypass manifest-lock overrides.
   - Bypasses stale `Activity.mLastRequestedOrientation` caching when enforcing `targetOrientation` across sub-activities, plugin activities, and split-APK feature modules.
   - `RotationModuleEntry` uses a process-wide single-initialization guard (`hooksInitialized`) so dynamic feature / Split APK `onPackageReady` dispatches never duplicate listeners or reset active state.
3. **In-Place Window, Dialog & Overlay Hooks (`Window.setAttributes`, `WindowManagerImpl`, `WindowManagerGlobal`)**
   - Mutates explicit `WindowManager.LayoutParams.screenOrientation` locks in-place across `Window`, `WindowManagerImpl`, and `WindowManagerGlobal` (`Dialog`, `BottomSheetDialog`, `PopupWindow`, overlays) while preserving `SCREEN_ORIENTATION_UNSPECIFIED (-1)`.
4. **Safety, Android 17 Policy & API 102 Hot Reload**
   - **`SafetyGuard`:** Never hooks `system_server`, `com.android.systemui`, `com.android.phone`, `com.android.bluetooth`, `com.android.permissioncontroller`, Vector Manager, or itself, calling `detach()` immediately if scoped.
   - **`ActivitySafetyChecker`:** Guards translucent/floating activities against `IllegalStateException` (`Only fullscreen opaque activities can request orientation`) on fixed modes and catches any framework rejection safely.
   - **Android 16/17 Large-Screen Policy:** Android 16/17 ignores fixed orientation requests on displays with `smallestScreenWidthDp >= 600dp`. Because the Galaxy A52s 5G is a smartphone (`sw < 600dp`), apps still enforce portrait/landscape locks normally — handled by this module inside each scoped process.
   - **API 102 Hot Reload:** Uses `HookBuilder.setId(...)`, cleanly releases preference listeners in `onHotReloading`, and atomically swaps hooks via `HookHandle.replaceHook(...)` in `onHotReloaded`.

---

## Supported Orientation Modes

| Mode | Android Constant | Value | Behavior |
| :--- | :--- | :---: | :--- |
| **Follow System Auto Rotate** *(Default)* | `SCREEN_ORIENTATION_FULL_USER` | `13` | Allows all 4 orientations when Auto Rotate is ON; respects rotation lock when OFF |
| **Force Portrait** | `SCREEN_ORIENTATION_PORTRAIT` | `1` | Forces standard portrait |
| **Force Landscape** | `SCREEN_ORIENTATION_LANDSCAPE` | `0` | Forces standard landscape |
| **Reverse Portrait** | `SCREEN_ORIENTATION_REVERSE_PORTRAIT` | `9` | Forces upside-down portrait |
| **Reverse Landscape** | `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` | `8` | Forces reverse landscape |
| **Full Sensor** | `SCREEN_ORIENTATION_FULL_SENSOR` | `10` | Rotates across all 4 orientations ignoring Auto Rotate lock |

---

## Companion Configuration App UI

- **Material 3 + Dynamic Color (Light & Dark):** Status banner showing live Vector version, libxposed API version, scope count, and active hooked targets.
- **Global Controls:** Master Enable/Disable switch, Global Force Rotation switch, and Default Orientation Mode picker.
- **App List & Search:** Real-time search by label/package, filter chips (`Third-Party`, `Excluded`, `Custom Mode`, `In Vector Scope`, `System`, `All`), per-app enable switch, and bottom sheet for custom per-app modes and single-app Vector scope requests.
- **Exclusions & Policy Tab:** System app default exclusion toggle (ON by default), verbose hook logging toggle, one-tap override reset, and read-only view of protected critical system packages.
- **Diagnostics Tab:** Device/Android 17 `smallestWidthDp` policy telemetry, live Auto Rotate & display rotation readout, API 102 running hooked targets + manual Hot Reload triggers, and clipboard diagnostics export.

---

## Project Structure

```text
UniversalForceRotation/
├── app/                          # Xposed module + Material 3 companion UI (com.sagor.universalforcerotation)
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/sagor/universalforcerotation/
│       │   │   ├── UniversalForceRotationApp.kt
│       │   │   ├── model/        (OrientationMode, RotationConfig, AppEntry)
│       │   │   ├── core/         (SafetyGuard, OrientationPolicyEngine, OrientationStateTracker, ConfigSerializer, HookLogger)
│       │   │   ├── data/         (ConfigRepository, InstalledAppsRepository, XposedServiceManager)
│       │   │   ├── xposed/       (RotationModuleEntry, OrientationHookCoordinator, ActivitySafetyChecker)
│       │   │   └── ui/           (MainActivity, AppsFragment, AppListAdapter, AppConfigBottomSheet, ExclusionsFragment, DiagnosticsFragment, AppIconCache)
│       │   ├── res/
│       │   └── resources/META-INF/xposed/
│       │       ├── java_init.list
│       │       └── module.prop
│       └── test/java/com/sagor/universalforcerotation/
├── testapp/                      # Rotation Test Lab companion test harness (com.sagor.universalforcerotation.testapp)
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── releases/                     # Prebuilt verified APKs + SHA256SUMS.txt
├── INSTALLATION.md
├── TESTING.md
├── TROUBLESHOOTING.md
├── LICENSE
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Building from Source

Requirements: **JDK 21** and **Android SDK Platform 37 (Android 17)**.

```bash
./gradlew clean
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease :testapp:assembleDebug
```

> **Release Signing Note:** `app/build.gradle.kts` reads `keystore.properties` (or `UFR_KEYSTORE_*` environment variables) for a custom release signing key and falls back to the Android debug key when absent (matching `libxposed/example`). Provide your own keystore before public redistribution.

---

## Prebuilt Release Artifacts & Checksums (`v1.1.0`)

| Artifact | Size (bytes) | SHA-256 |
| :--- | :---: | :--- |
| `UniversalForceRotation-v1.1.0-release.apk` | `1,854,964` | `8dacd40a2a2f7138a10230f27614a9a3a1b35e912abc9d240d6bb37682af2c57` |
| `UniversalForceRotation-v1.1.0-debug.apk` | `7,331,996` | `e01da9c146f74f05b2666daec44bc2598b65f05220f8b2168c4b58b80c1b224b` |
| `UniversalForceRotation-TestLab-v1.1.0-debug.apk` | `6,843,656` | `d03f5e26ad145aa214fee6db53a14450acc2018a7f3e386da00a16e28447c4e8` |

---

## Verification Summary

- **Build Verification:** **Passed** (`clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :testapp:assembleDebug` -> `BUILD SUCCESSFUL`, 0 compiler warnings; `aapt2`, `apksigner`, and `dexdump` verified `RotationModuleEntry` + `XposedProvider` present and `io.github.libxposed.api.XposedModule` absent from DEX in both Debug and R8 Release builds).
- **Unit Testing:** **Passed (25 / 25)** across 5 JVM suites (`ConfigSerializerTest`, `HookCoordinatorSimulationTest`, `OrientationPolicyEngineTest`, `OrientationStateTrackerTest`, `SafetyGuardTest`).
- **Emulator Testing:** Not executed (no emulator/AVD installed in build environment).
- **Real-Device Testing:** Pending verification of `v1.1.0` on **Samsung Galaxy A52s 5G** — see [TESTING.md](TESTING.md) for the test matrix with `Rotation Test Lab` and real-world multi-section app verification.

---

## Documentation

- [Installation & Usage Guide](INSTALLATION.md)
- [Testing Plan & Verification Report](TESTING.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
