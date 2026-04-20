# ReVanced Patches: GMS Dependency Removal

Universal ReVanced patch set that strips Google Play Services, Play Billing,
and Firebase Analytics dependencies from any Android APK.

**Compatible with ReVanced Manager 1.x (Flutter)** — all patches are bytecode-only, no resource editing.

## What it patches

| Sub-patch | What it does |
|---|---|
| **Remove Play Services availability check** | Stubs `GoogleApiAvailability.isGooglePlayServicesAvailable()` → returns `SUCCESS (0)`. Removes the "Check that Google Play is enabled on your device" error. |
| **Remove Play Billing dependency** | Stubs `BillingClient.isReady()` → `true`, `getConnectionState()` → `CONNECTED`, `startConnection()` → no-op, `queryPurchasesAsync/queryProductDetailsAsync` → no-op. |
| **Remove Firebase Analytics dependency** | Stubs `FirebaseAnalytics.getInstance()` → null, `logEvent/setUserProperty/setAnalyticsCollectionEnabled` → no-op, `FirebaseApp.initializeApp()` → null. Also blanks every remaining method in the FirebaseAnalytics class. |
| **Remove Firebase init provider** | Stubs `FirebaseInitProvider` methods (`onCreate`, `attachInfo`, `call`, `query`) via bytecode to prevent crash on startup without GMS. |
| **Remove all GMS dependencies** | Master patch — applies all of the above, then does a final sweep to neutralise any method containing GMS error strings. |

## Extension (runtime stubs)

The `extensions/extension/` module compiles to a `.rve` DEX file (`gms-stubs.rve`)
that gets merged into the target APK. It provides `GmsStubs.java` with static
methods that the bytecode patches redirect calls to.

## Project structure

```
revanced-patches-gms-removal/
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── patches/
│   ├── build.gradle.kts
│   └── src/main/kotlin/app/revanced/patches/gms/
│       ├── RemoveGmsDependencyPatch.kt      ← 4 patches + master
│       └── RemoveFirebaseInitProvider.kt    ← Firebase provider stub
└── extensions/
    └── extension/
        ├── build.gradle.kts
        └── src/main/java/app/revanced/extension/gms/
            └── GmsStubs.java                ← runtime stubs
```

## Usage with ReVanced Manager (1.x Flutter)

1. Go to **Releases** and download `gms-removal-patches-1.0.0.jar`
2. Open ReVanced Manager → **Settings** → **Sources**
3. Set patches source to `hulahupjingga/revanced-patches-gms-removal`
4. Select your target APK
5. Enable **"Remove all GMS dependencies"** (or pick individual patches)
6. Make sure **"Show universal patches"** is enabled in settings
7. Patch and install

## Usage with ReVanced CLI

```bash
java -jar revanced-cli.jar patch \
  --patches gms-removal-patches-1.0.0.jar \
  --patch-name "Remove all GMS dependencies" \
  input.apk
```

Or apply individual sub-patches by name if you only need partial de-GMSing.

## Building from source

Prerequisites:
- JDK 17+
- A GitHub PAT with `read:packages` scope (for the ReVanced Gradle plugin)

```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=ghp_your_token_here

./gradlew build
```

This produces a patches JAR in `patches/build/libs/`.

## Notes

- **Universal (no package restriction)**: These patches are not tied to a specific
  app package. They will work on any APK that uses the standard GMS/Firebase/Billing
  class names.
- **No resource editing**: All patches operate at the bytecode level only. This
  avoids `aapt2 link` errors that occur on some devices/Manager versions when
  modifying `AndroidManifest.xml`.
- **Obfuscation**: If the target app obfuscates GMS library class names (rare
  but possible with R8 full mode), you will need to update the class name
  strings in the patch. Use `apktool d` or `jadx` to inspect the smali first.
- **Billing stubbing != free purchases**: The billing patch makes the app
  *not crash* without Play Store. It does NOT grant entitlements or bypass
  purchase verification. Server-side validated purchases will still fail.
- **Firebase removal is analytics-only**: This does not patch FCM (push
  notifications), Crashlytics, or other Firebase products. Extend the patch
  if your target app uses those.
