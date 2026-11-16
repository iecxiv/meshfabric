# FIDO2/WebAuthn and validation scope — 1.3.8

[Español](../es/fido.md) | [My Account](../profile-native.md) | [README](../../README.md)

This describes fido-support with app versionName 1.3.8. It is not a tag or release announcement.

## Implemented behavior

- Native FIDO2/WebAuthn sign-in and registration, listing and removal of account security keys.
- YubiKit 3.3.0 remains pinned. The WebView stays restricted to the selected server, does not open an external browser, and cancels on TLS errors.
- Sign-in uses the attempt-bound hwtoken/hwstate exchange and an empty token form field for server-side session credential restoration. Authenticator success, HTTP success or cookies alone do not establish an authenticated account: the control connection must verify matching user identity.
- Registration requires server acknowledgement and key-list readback. Removal is not confirmed merely because the request was sent; the key must be absent from the returned list.
- YubiKit prompts and results have Spanish resources and default/English declarations. Key-operation completion remains separate from final account sign-in.
- The proposed selectable-key removal UI was cancelled and is not included. The existing key index/ID input remains unchanged.

## Physical checks reported by the user

Report date: 2026-11-16. The security key identified in this work is a YubiKey 5 NFC.

| Operation | Evidence and scope |
| --- | --- |
| Security-key registration | User reports success; registration over NFC was independently reported earlier in this work. |
| Security-key removal | User reports success. No complete per-transport removal matrix was supplied. |
| FIDO sign-in | User reports success and explicitly confirms sign-in over USB as well. |
| Copy/paste | User reports it working after the terminal/desktop clipboard work. The exact device/host and edge-case matrix was not recorded. |

These are user-reported physical checks, not an independent assistant hardware test. USB sign-in does not establish USB registration/removal. Other key models, all Android/agent/server versions, every USB/NFC combination and multiserver isolation are not certified by this report. A complete transport-by-operation matrix remains open.

## Build and runtime configuration

Source of truth: app/build.gradle.kts.

| Setting | Value |
| --- | --- |
| versionName | 1.3.8 |
| versionCode | 1, unchanged |
| minSdk | 26; Android 8.0 minimum |
| targetSdk | 35, unchanged |
| compileSdk | API 37, minor API 2; platforms;android-37.2 |
| Build Tools | 37.0.0 |
| Java source/target | 17 |
| Validation JDK | 21 |
| Validated Android Command-line Tools | 23.0 |

compileSdk is the build API selection, not the app minimum or target. SDK values and YubiKit are not changed by this documentation/versionName update.

## Automated checks and remaining limits

The Locale constructor, Compose v2 rule and clipboard API migrations have been incorporated. The user reported successful validation workflows. This is not a claim of zero remaining lint/compiler warnings; inspect the latest reports separately.

Changes are gated by compilation, all JVM unit tests, lint and the entire Android instrumented suite, without skipped tests or weakened assertions. This update also runs the standalone JavaScript test scripts. No APK upload is required: the developer builds locally and installs through ADB.

Physical checks remain distinct from automated results. Review the detailed clipboard lifecycle/layout checklists when changing those flows, and do not infer universal FIDO compatibility or multiserver validation from a successful CI run. No merge, tag or release is performed by this update.
