# 🎬 TV Video Player

A lightweight Android TV video player built with pure Android MediaPlayer API. No heavy dependencies, no internet required, no ads.

## Features

- **Storage Browser** — Browse internal storage and auto-detect USB drives
- **DPAD Navigation** — Full TV remote control support
- **Video Formats** — MP4, MKV, AVI, MOV, WEBM, FLV, 3GP, TS, M4V
- **Audio Formats** — MP3, AAC, AC3, WAV, FLAC, OGG, M4A
- **Subtitle Support** — SRT, ASS, SSA, SUB, VTT (auto-load matching filenames)
- **Audio Boost** — Toggle amplified audio via LoudnessEnhancer (+15dB)
- **Resume Playback** — Remembers last position for each video
- **Dark Theme** — Large, readable UI optimized for TV screens

## DPAD Controls

| Button | Action |
|--------|--------|
| ◀ LEFT | Rewind 10 seconds |
| ▶ RIGHT | Forward 10 seconds |
| ● CENTER | Play / Pause |
| ▲ UP | Subtitle options |
| ▼ DOWN | Audio track & boost options |
| BACK | Exit player (saves position) |
| MENU | Toggle audio boost |

## Technical Specs

- **Min SDK:** 17 (Android 4.2)
- **Target SDK:** 28 (Android 9)
- **Architecture:** armeabi-v7a (32-bit)
- **APK Size:** < 1 MB
- **Dependencies:** None (pure Android SDK)
- **Playback:** Hardware-accelerated MediaPlayer

## Build

### Local Build
```bash
chmod +x gradlew
./gradlew assembleRelease
```

### GitHub Actions
Push to `main` or `master` branch. The workflow automatically:
1. Builds a signed release APK
2. Uploads it as a downloadable artifact

Find the APK in **Actions → Latest Run → Artifacts → TVVideoPlayer-Release-APK**

## Project Structure

```
├── .github/workflows/build.yml    # CI/CD pipeline
├── app/
│   ├── build.gradle               # App config (SDK 17-28, armeabi-v7a)
│   ├── proguard-rules.pro         # ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml    # Leanback launcher, permissions
│       ├── java/com/tvplayer/lite/
│       │   ├── MainActivity.java          # Home screen
│       │   ├── VideoBrowserActivity.java  # File browser
│       │   └── VideoPlayerActivity.java   # Player with controls
│       └── res/
│           ├── layout/            # XML layouts
│           ├── drawable/          # Selectors, shapes
│           └── values/            # Colors, strings, styles
├── build.gradle                   # Root build config
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat         # Gradle wrapper scripts
└── README.md
```

## Audio Boost

The audio boost feature uses `LoudnessEnhancer` (API 19+) to amplify audio by +15dB beyond normal maximum. On older devices (API 17-18), it maximizes the system volume as a fallback.

Toggle via:
- **MENU** button on remote
- **Audio Options** popup (DPAD DOWN → select boost button)

## License

MIT License — Free to use, modify, and distribute.
