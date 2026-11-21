# JARVIS HUD — Standalone Local Edition (No API Key)

Separate native Android app preserving the supplied local HUD layout. Kotlin WebView, native Android text-to-speech and speech recognition, and AccessibilityService. No OpenAI, Gemini or other AI provider keys. There is no general-purpose LLM in this edition. Android voice recognition may still use the device speech provider's network service even when offline is preferred.

## Install
GitHub Actions → Build JARVIS HUD Bridge APK (No API Key) → artifact jarvis-hud-bridge-debug-apk. Extract app-debug.apk, install, open JARVIS LOCAL HUD, tap ACCESSIBILITY and enable JARVIS Local Bridge. Return to the HUD and tap CHECK BRIDGE.

## Commands
home, back, recents, scroll down/up, tap center, click [exact visible label], type [text], open [launcher label], snapshot.
Target-app actions move the HUD to the background first; open it again to view the result. Click, tap and type require confirmation. Snapshot is an on-demand accessibility hierarchy, not a screenshot. Does not read password or editable text in snapshots. No continuous microphone capture, hidden automation, or lock-screen bypass.

## Bridge
127.0.0.1:8080 only. HTTP endpoints /bridge/command and /bridge/state; the AccessibilityService polls /bridge/poll and posts /bridge/result. An automatically generated in-process session credential protects the loopback endpoint from other apps/browser pages; no user key or configuration is required. The packaged WebView does not load external websites. Raw browser UI without the packaged native component cannot operate the phone. The service must be enabled manually.

No APK runtime or device testing is claimed until the build and installation have been verified. Gradle 8.9, JDK 17, SDK 35.
