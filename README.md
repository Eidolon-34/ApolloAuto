# Apollo Auto / Apollo II

Android local music player with device and USB/SD folder libraries, playlists,
Media3 playback, Android Auto browsing and a Compose visualizer gallery.

This public source snapshot contains the v108 radio improvements with documented
public-export differences. See [PUBLIC_EXPORT_NOTES.md](PUBLIC_EXPORT_NOTES.md).
It is a development candidate, not a hardware-certified production release.

## Music library

Cached catalogs remain available on startup. New device/USB songs appear during
background scans. Automatic checks use a 15-minute per-source cadence while the
app process is alive. USB checks enumerate directory metadata, not audio bytes;
manual Rescan remains available. Initial Android permissions/folder selection
are required. Catalog refresh does not reset or silently extend the playing queue.

## Build

Use JDK 17+ and Android SDK 34. Set ANDROID_HOME or a local, untracked
local.properties with sdk.dir. Then run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug
```

Debug package: com.apolloauto.player.radio, Android 8.0/API 26 or newer.
Do not uninstall an existing app merely to install a development build; signing
certificates must match for an in-place update. No signing keys are distributed.

## Verification limits

The private v108 baseline passed 87 JVM tests and APK assembly. The public export also passed 87 JVM tests and APK assembly after its
documented font/renderer changes.
Hardware startup time, USB behavior and uninterrupted playback require testing
on the intended radio while parked. Lint has an existing missing Android Auto
voice-search intent-filter error; it is not suppressed.

## License and attribution

Original Apollo copyright/license: [LICENSE](LICENSE).
Retained third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Read the export notes for excluded font and MusicDNA adaptation.
