# TV Casting

Private Android app. Streams video links from your phone to a TV over the same Wi-Fi using HTTPS.

## How it works

1. Phone runs a local HTTPS server (self-signed cert) and serves a receiver page.
2. TV opens that page in its browser (or a WebView receiver app).
3. Phone sends the video URL over the encrypted channel; the TV plays it directly.
4. Phone acts as the remote: play, pause, seek, volume, quality.

## Features

- HTTPS streaming over local Wi-Fi (self-signed, pinned)
- Liquid Glass-style modern UI (blur, glassmorphism, spring animations)
- Background playback + foreground service (stream continues when phone is locked)
- Auto quality adaptation based on measured latency
- AirPlay 2 and Matter Casting hooks (extensible)
- GitHub Actions builds a signed-ready APK on every push to main

## Build

```bash
./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`. Or grab the `app-release` artifact from the Actions tab.
