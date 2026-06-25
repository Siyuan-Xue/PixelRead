# PixelRead

PixelRead is a small Android ebook reader built as a quiet, pixel-style utility for phone and tablet reading.

Developer identity: CODEX & XUE.

## Current Features

- Open a local PDF or EPUB through the Android system file picker
- Read PDF files in continuous scroll mode with the AndroidX PDF Viewer for high-fidelity layout, images, and pinch zoom
- Read reflowable EPUB files with Readium in single-page mode
- Adjust EPUB text with 14sp-34sp font-size steps at 2sp intervals
- Switch between dark and light reading themes based on the project color tokens
- Keep reader controls in a collapsible top drawer
- Resume recent PDF and EPUB books from the empty home screen
- View short empty, loading, and error states

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- AndroidX `pdf-viewer-fragment` for PDF rendering
- Readium Kotlin Toolkit for EPUB parsing and navigation
- Android Gradle Plugin 9.2.1
- Compile SDK: Android API 36.1
- Minimum SDK: Android API 28

## Build

From the project root:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is copied to:

```text
app/build/outputs/apk/debug/PixelRead-0.3.0-debug.apk
```

## Install With ADB

```powershell
adb install -r app/build/outputs/apk/debug/PixelRead-0.3.0-debug.apk
```

## Status

MVP prototype.
