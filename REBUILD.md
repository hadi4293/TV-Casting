# TV-Casting v2 — Rebuild Plan

Rebuilding the app from scratch on branch `rebuild/v2`. Every step below is a commit. Check it off as we go.

## Goals
- Phone streams a video URL to a TV over the same Wi-Fi.
- Transport: play, pause, resume, seek, volume, stop.
- No ANR, no self-signed cert warnings, no state bugs, no leaked services.
- Clean architecture, real tests, honest README.

## Skeleton (package layout)
```
com.hadii.tvcasing/
  MainActivity.kt
  streaming/
    StreamingService.kt      # foreground service + NanoWSD server
    CertUtils.kt             # self-signed cert (pinned via receiver app later)
    StreamingState.kt        # sealed UI state
    StreamingViewModel.kt    # binds service, owns state
    CommandCodec.kt          # JSON command builders (testable)
    NetworkMonitor.kt        # restarts server on Wi-Fi change
  ui/
    CastScreen.kt            # full transport controls + receiver URL
    Theme.kt / Glass.kt
  assets/receiver.html       # robust receiver with codec fallback
```

## Steps

### Phase 1 — Foundation
- [x] 1. Branch `rebuild/v2` + this plan
- [ ] 2. Root Gradle files (settings, build, wrapper, properties)
- [ ] 3. `app/build.gradle.kts` — minSdk 24, Compose, deps, no broken signing
- [ ] 4. `AndroidManifest.xml` — permissions, FGS type, no `usesCleartextTraffic` footgun
- [ ] 5. Resources (strings, colors, themes, icons)

### Phase 2 — Core streaming
- [ ] 6. `CertUtils.kt` — generate cert with LAN IP SAN
- [ ] 7. `CommandCodec.kt` — play/pause/resume/seek/volume/stop JSON (pure, tested)
- [ ] 8. `StreamingState.kt` — Idle / Connecting / Connected / Playing / Error
- [ ] 9. `StreamingService.kt` — server on background thread, no Thread.sleep, network monitor hook
- [ ] 10. `NetworkMonitor.kt` — restart on connectivity change
- [ ] 11. `StreamingViewModel.kt` — bind/unbind, state driven by real WS events
- [ ] 12. `receiver.html` — codec probe, friendly errors, reconnect

### Phase 3 — UI
- [ ] 13. `Theme.kt` / `Glass.kt`
- [ ] 14. `CastScreen.kt` — URL input, play/stop, seek slider, volume, receiver URL display
- [ ] 15. `MainActivity.kt` — permissions only, NEVER stops the service

### Phase 4 — Quality
- [ ] 16. Unit tests — CommandCodec, CertUtils, StreamingState
- [ ] 17. CI workflow — build + test on every push
- [ ] 18. README — only claims what's real
- [ ] 19. Final review against ISSUES.md (all 13 closed)

## Rules
- One logical change per commit.
- No `Thread.sleep` on main/service threads.
- No `stopService` from Activity.
- State transitions come from real events, not guesses.
- Every public function has a reason to exist.
