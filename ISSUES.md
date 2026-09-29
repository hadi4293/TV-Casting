# TV-Casting — Known Issues

Tracking list. Fix one at a time, check it off when done.

## 1. Self-signed HTTPS certificate warning on TV browsers
**Severity:** High
**Status:** Open

The server generates a self-signed cert (CertUtils). Most smart-TV browsers show a security warning before loading the receiver page, and many don't let the user accept it at all.

**Fix direction:** Pin the cert in a custom WebView receiver app, or use a local CA the TV trusts, or fall back to HTTP on a LAN-only port with a clear warning.

---

## 2. Thread.sleep(400) on the main/service thread
**Severity:** High
**Status:** Open

In StreamingService.startStreaming(), a blocking Thread.sleep(400) runs on the service's main thread right after starting the server, before broadcasting the play command. This can trigger an ANR.

**Fix direction:** Replace with a short Handler/postDelayed or a coroutine delay on a background dispatcher.

---

## 3. Transport controls incomplete in UI
**Severity:** Medium
**Status:** Open

StreamingService implements seek() and setVolume(), but CastScreen only exposes a play/pause toggle. Seek slider and volume control are missing from the UI.

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

minSdk = 26 excludes a chunk of older Android phones still in use.

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
