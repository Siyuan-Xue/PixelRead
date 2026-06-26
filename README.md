# PixelRead

PixelRead is a private Android ebook reader built as a quiet, pixel-style utility for phone and tablet reading.

Developer identity: CODEX & XUE.

## Current Features

- Open a local PDF or EPUB through the Android system file picker
- Read PDF files in continuous scroll mode with the AndroidX PDF Viewer for high-fidelity layout, images, and pinch zoom
- Read reflowable EPUB files with Readium in single-page mode
- Adjust EPUB text with 13sp-29sp font-size steps at 2sp intervals
- Switch between dark and light reading themes based on the project color tokens
- Keep reader controls in a collapsible top drawer
- Resume recent PDF and EPUB books from the empty home screen
- Check GitHub Releases manually from the small update icon beside the product identity
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
.\gradlew.bat :app:assembleRelease
```

Release signing is configured through the local, untracked `signing/release-signing.properties` file.

The signed private release APK is copied to:

```text
app/build/outputs/apk/release/PixelRead-1.1.1-release.apk
```

## Install With ADB

If the old prototype package is still installed, uninstall it before installing:

```powershell
adb uninstall com.codexue.pixelread
adb install -r app/build/outputs/apk/release/PixelRead-1.1.1-release.apk
```

The formal package name is:

```text
com.milesxue.pixelread
```

## Status

Private 1.1.1 release.
