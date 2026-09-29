# TV-Casting — Known Issues

Full review of the codebase. Fix one at a time, check it off when done.

## 1. Self-signed HTTPS certificate warning on TV browsers
**Severity:** High
**Status:** Open

CertUtils generates a self-signed X.509 cert with the LAN IP as a SAN. The comment claims browsers accept it without warnings, but that's false — a self-signed cert is untrusted by every browser regardless of SAN. Smart-TV browsers show a security interstitial, and many don't let the user accept it at all.

**Fix direction:** Pin the cert in a custom WebView receiver app, or use a local CA the TV trusts, or fall back to HTTP on a LAN-only port with a clear warning.

---

## 2. Thread.sleep(400) on the service thread
**Severity:** High
**Status:** Open

In StreamingService.startStreaming(), a blocking Thread.sleep(400) runs on the service's main thread right after starting the server, before broadcasting the play command. This can trigger an ANR.

**Fix direction:** Replace with a short Handler/postDelayed or a coroutine delay on a background dispatcher.

---

## 3. Transport controls incomplete in UI
**Severity:** Medium
**Status:** Open

StreamingService implements seek() and setVolume(), and the ViewModel exposes them, but CastScreen only renders a play/pause toggle. Seek slider and volume control are missing from the UI.

**Fix direction:** Add a seek slider and volume slider wired to vm.seek() / vm.setVolume().

---

## 4. No format fallback or transcoding
**Severity:** Medium
**Status:** Open

receiver.html just sets video.src = url. If the TV browser can't decode the codec (e.g. HEVC, certain containers), playback fails silently with only a generic error toast.

**Fix direction:** Probe supported MIME types, show a friendly message, or proxy through a lightweight transcoder.

---

## 5. No tests
**Severity:** Medium
**Status:** Open

No unit or instrumented tests exist. Cert generation, command JSON, and state transitions are untested.

**Fix direction:** Add JVM unit tests for CertUtils, command builders, and StreamingState transitions.

---

## 6. minSdk 26 drops older devices
**Severity:** Low
**Status:** Open

minSdk = 26 excludes a chunk of older Android phones still in uses.

**Fix direction:** Lower to 24 if feasible, or document the cutoff clearly.

---

## 7. No reconnection / discovery UX
**Severity:** Low
**Status:** Open

If the TV browser drops, the user has no in-app way to see the receiver URL or re-pair. receiverUrl() exists but isn't surfaced in the UI.

**Fix direction:** Show the receiver URL / QR code on the CastScreen when connected.

---

## 8. README claims features not yet wired
**Severity:** Low
**Status:** Open

README mentions AirPlay 2 and Matter Casting hooks and auto quality adaptation, but none are implemented in code.

**Fix direction:** Either implement stubs or trim the README claims.

---

## 9. State machine never reaches Playing
**Severity:** Medium
**Status:** Open

StreamingViewModel sets Connecting on start, then Connected when the service binds — but nothing ever transitions to Playing. togglePause flips between Playing and Connected, so the UI state is wrong from the start and the play/pause icon is inverted.

**Fix direction:** Drive state from actual WebSocket open/message events instead of the bind callback.

---

## 10. MainActivity.onDestroy stops the service
**Severity:** High
**Status:** Open

MainActivity calls stopService() in onDestroy. Rotating the screen or any config change destroys the activity and kills the foreground stream — defeating the whole point of a foreground service.

**Fix direction:** Remove the stopService call; let the service live independently of the activity.

---

## 11. No network-change handling
**Severity:** Medium
**Status:** Open

The cert and server bind to the IP captured at start. If Wi-Fi drops or the phone switches networks, the server keeps serving a stale IP with no recovery.

**Fix direction:** Listen for ConnectivityManager callbacks and restart the server with the new IP.

---

## 12. Release signing is a no-op
**Severity:** Low
**Status:** Open

The release signingConfig falls back to an empty password and a missing keystore file. CI only builds debug, so the "signed-ready APK" claim in the README is false.

**Fix direction:** Supply real signing secrets via GitHub Actions secrets, or drop the release config until ready.

---

## 13. No input validation on the video URL
**Severity:** Low
**Status:** Open

Any string the user types is passed straight to the TV as video.src. A non-media URL or a page that redirects produces a silent failure on the TV.

**Fix direction:** Validate the URL scheme/host client-side and surface errors before starting the stream.
