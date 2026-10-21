# PulsePlay — Original Offline Audio & Video Studio Player

**PulsePlay** (`com.aistudio.pulseplay.mpxvqr`) is an original, offline-first Android audio and video player, equalizer DSP controller, SRT/WebVTT subtitle manager, and local media processing studio written in Kotlin using Jetpack Compose and AndroidX Media3.

> **Trademark & Naming Notice:** `PulsePlay` is used as a development identifier. Prior to commercial publication, perform an independent trademark clearance search in your target distribution jurisdictions.

---

## Core Architecture & Features

1. **Original Visual System ("Obsidian Acoustic Studio" & "Warm Ivory Studio")**
   - Custom dark (`#0D1117` / `#FF9F1C` / `#00E5FF`) and light palettes with optional Android 12+ Material You dynamic colors.
   - Bundled offline typography (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`) under the SIL Open Font License 1.1.
   - Responsive adaptive layout switching automatically between a bottom `NavigationBar` (`< 600dp`) and a side `NavigationRail` (`>= 600dp`), paired with a non-overlapping `PersistentMiniPlayerBar`.

2. **Media Library & Zero-Permission File Access**
   - Shared device `MediaStore` discovery for Audio and Video with least-privilege runtime permissions (`READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` on API <= 32).
   - Zero-permission single-file import via Android Storage Access Framework (`OpenDocument`).
   - Built-in legal test fixture generator (`SampleMediaGenerator`) that synthesizes playable stereo PCM `.wav`, AAC-LC `.m4a`, H.264 `.mp4`, and `.srt`/`.vtt` files locally on device.
   - Local Room SQLite persistence (`PulseDatabase`) for folders, custom ordered playlists, favorites, and non-destructive playback history.

3. **Audio & Video Playback + Background `MediaSessionService`**
   - Single managed playback session backed by AndroidX Media3 `ExoPlayer` and `PulsePlaybackService` (`MediaSessionService`).
   - Audio focus handling, lock-screen/notification transport controls, and automatic pause on headphone disconnection (`setHandleAudioBecomingNoisy(true)`).
   - Video player with aspect-ratio framing (`Fit`, `Fill`, `16:9`, `4:3`), 90° rotation, fullscreen toggle, and vertical drag gestures for brightness and volume.

4. **Verified SRT & WebVTT Subtitle Engine**
   - Parses and validates actual timestamped cues in `.srt` and `.vtt` files, strips inline HTML tags, and supports real-time font scaling (`70%–180%`) and sync offset adjustment (`-10s` to `+10s`).

5. **Hardware Equalizer & Bass Boost DSP**
   - Binds Android `Equalizer` and `BassBoost` (`android.media.audiofx`) to the active audio session with hardware capability detection and graceful fallback.

6. **Real Media Processing Studio & Format Converter**
   - Sample-exact 16-bit PCM RIFF WAVE audio trimming.
   - Lossless MP4/M4A stream remuxing (`MediaExtractor` + `MediaMuxer`) with keyframe-aligned trimming and orientation matrix hints (`0°`, `90°`, `180°`, `270°`).
   - Hardware/platform `MediaCodec` AAC-LC audio transcoding and video-to-audio extraction with 14-step validation, storage checks, cancellation, and startup recovery of interrupted jobs.

7. **100% Offline Privacy Posture**
   - No `INTERNET` or `ACCESS_NETWORK_STATE` permission is requested in `AndroidManifest.xml`.
   - Zero cloud SDKs, zero analytics, and zero network dependencies in the build graph.

---

## Building & Running Tests

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Assemble Installable Debug APK
```bash
gradle :app:assembleDebug
```
The compiled debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

---

## License & Third-Party Notices

All third-party libraries and bundled fonts are permissive open-source components (Apache License 2.0 and SIL Open Font License 1.1). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the complete dependency inventory and full license texts.
