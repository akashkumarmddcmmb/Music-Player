# PulsePlay — Original Offline Audio & Video Studio Player

**PulsePlay** (`com.aistudio.pulseplay.mpxvqr`) is an original, offline-first Android audio and video player, equalizer DSP controller, SRT/WebVTT subtitle manager, local media processing studio, and GitHub Releases self-updating application written in Kotlin using Jetpack Compose and AndroidX Media3.

> **Trademark & Naming Notice:** `PulsePlay` is used as a development identifier. Prior to commercial publication, perform an independent trademark clearance search in your target distribution jurisdictions.

---

## Core Architecture & Features

1. **Original Visual System ("Obsidian Acoustic Studio" & "Warm Ivory Studio")**
   - Custom dark (`#0D1117` / `#FF9F1C` / `#00E5FF`) and light palettes with optional Android 12+ Material You dynamic colors.
   - Bundled offline typography (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`) under the SIL Open Font License 1.1.
   - Responsive adaptive layout switching automatically between a bottom `NavigationBar` (`< 600dp`) and a side `NavigationRail` (`>= 600dp`), paired with a non-overlapping `PersistentMiniPlayerBar` and landscape `NowPlayingSidePanel`.

2. **Media Library & Zero-Permission File Access**
   - Shared device `MediaStore` discovery for Audio and Video with least-privilege runtime permissions (`READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` on API <= 32).
   - Zero-permission single-file import via Android Storage Access Framework (`OpenDocument`).
   - Built-in legal test fixture generator (`SampleMediaGenerator`) that synthesizes playable stereo PCM `.wav`, AAC-LC `.m4a`, H.264 `.mp4`, and `.srt`/`.vtt` files locally on device.
   - Local Room SQLite persistence (`PulseDatabase`) for folders, custom ordered playlists, favorites, and non-destructive playback history.

3. **Audio & Video Playback + Background `MediaSessionService`**
   - Single authoritative playback session backed by AndroidX Media3 `ExoPlayer` and `PulsePlaybackService` (`MediaSessionService`).
   - Audio focus handling, lock-screen/notification transport controls, sleep timer, and automatic pause on headphone disconnection (`setHandleAudioBecomingNoisy(true)`).
   - Video player with aspect-ratio framing (`Fit`, `Fill`, `Original`, `16:9`, `4:3`), 90° rotation, fullscreen toggle, Picture-in-Picture (`PiP`) mode, audio track selection, and vertical drag gestures for brightness and volume.

4. **Verified SRT & WebVTT Subtitle Engine**
   - Parses and validates actual timestamped cues in `.srt` and `.vtt` files, strips inline HTML tags, and supports real-time font scaling (`70%–180%`) and sync offset adjustment (`-10s` to `+10s`).

5. **Real-Time 5-Band Biquad Equalizer, Bass Boost & Preamp DSP**
   - Custom `PulseEqualizerAudioProcessor` + Android `Equalizer` / `BassBoost` (`android.media.audiofx`) bound to the active audio session with persisted stereo L/R balance and preamp gain.

6. **Real Media Processing Studio & Format Converter (with Undo/Redo)**
   - Reversible Undo/Redo parameter history stack (`EditorUndoRedoHistory`) for trim ranges, rotation, crop presets, conversion profiles, and bitrates.
   - Sample-exact 16-bit PCM RIFF WAVE audio trimming and full `MediaCodec` decode/encode.
   - Lossless keyframe-aligned MP4/M4A stream remuxing (`MediaExtractor` + `IsoMp4Muxer`) with `tkhd` orientation matrix hints (`0°`, `90°`, `180°`, `270°`).
   - Post-export Play, Share (`FileProvider`), and File Info inspection, plus automatic startup cleanup of orphaned temporary files.

7. **GitHub Releases In-App Auto-Updater (`AppUpdateManager`)**
   - Checks `https://api.github.com/repos/{owner}/{repo}/releases/latest` (or `/releases`) on user request in **Settings → App Updates (GitHub Releases)**.
   - Compares semantic version (`versionName`) and `versionCode`, downloads the release APK over HTTPS with progress and cancellation, verifies the SHA-256 digest against `SHA256SUMS.txt` and validates the APK archive's `packageName`, `versionCode`, and signing certificate continuity before launching the Android package installer via `FileProvider`.
   - All media playback, library indexing, equalizer DSP, and studio editing remain 100% offline.

---

## Building, Testing & GitHub Release Signing

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Assemble Installable Debug APK
```bash
gradle :app:assembleDebug
```
The compiled debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Configure GitHub Repository & Release Signing in GitHub Actions
1. **Repository Slug (`owner/repo`)**:
   - In GitHub Actions, `GITHUB_REPOSITORY` is automatically injected into `BuildConfig.GITHUB_REPOSITORY` during build.
   - Locally or inside the app, you can also set your exact `owner/repo` in **Settings → App Updates (GitHub Releases)** or pass `-PGITHUB_REPOSITORY=owner/repo` to Gradle.
2. **Release Keystore Secrets (for Signed Production Releases)**:
   - `RELEASE_KEYSTORE_BASE64`: Base64-encoded JKS/PKCS12 keystore (`base64 -w 0 my-upload-key.jks`).
   - `STORE_PASSWORD`: Keystore password.
   - `KEY_PASSWORD`: Key password for the `upload` key alias.
   - Push a tag matching `v*` (e.g., `git tag v1.0.1 && git push origin v1.0.1`) or trigger the workflow manually via `workflow_dispatch` to publish a GitHub Release with `PulsePlay-vX.Y.Z-release.apk`, `PulsePlay-vX.Y.Z-debug.apk`, and `SHA256SUMS.txt`.

---

## License & Third-Party Notices

All third-party libraries and bundled fonts are permissive open-source components (Apache License 2.0 and SIL Open Font License 1.1). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the complete dependency inventory and full license texts.
