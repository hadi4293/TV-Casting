# TV Casting

Private Android app. Builds an APK automatically with GitHub Actions on every push to main.

## Download the APK

Go to the **Actions** tab, open the latest successful run, and download the `app-release-unsigned` artifact. Or check **Releases** for tagged builds.

## Build locally

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
