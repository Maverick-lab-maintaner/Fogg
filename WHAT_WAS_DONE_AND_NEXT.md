# What Was Done and What's Next — Fogg on the H19 (HryFine) watch

This file is the running chronicle for the local Fogg fork used with the owner's
H19 smartwatch. Append a new phase for every working session; never delete old
phases.

---

## Phase 1 — Porting Fogg to a HryFine/Bluetrum watch (2026-11-02)

### 1. Chronological Timeline

1. **Starting point.** The owner's Temu watch (label "H19", advertises as
   `Smartwatch`, MAC ends `03:6A`) normally pairs with the **HryFine** app
   (`com.lianhezhuli.hyfit` 3.8.25). Fogg v0.8.2 (upstream PR #10 added the
   `...dcca9f` Nordic-UART UUID variant) connected but looped
   "connecting/disconnecting".
2. **Diagnosis via adb logcat (USB).** Wireless adb pairing failed on MIUI
   ("protocol fault"); USB debugging worked. Logs showed the BLE link, service
   discovery, notify and MTU all succeed, but the watch never answers Fogg's
   handshake.
3. **Disproven hypothesis:** a real SMA `IDENTITY/CREATE` bind before
   `SESSION/CREATE`. Built and tested; the watch ignored it too.
4. **HCI snoop capture attempt failed:** MIUI's developer toggle never reaches the
   Bluetooth stack (`dumpsys bluetooth_manager` → `sSnoopLogSettingAtEnable =
   DISABLED`), and `setprop persist.bluetooth.*` is blocked by SELinux without root.
5. **nRF Connect manual probe.** The SMA handshake bytes got no reply.
6. **Owner found the model "H19"** under the watch. Decision: decompile HryFine
   (owner: *"just decompile hryfine instead"*).
7. **jadx decompile** → the watch speaks HryFine's own `0xDF` protocol, not SMA.
   An nRF Connect test with a hand-built `DF … F0` challenge returned
   `FD 00 05 04 F0 00 00 11 01` (ack/success) — first ever reply.
8. **Connect flow ported** (read 0x2A27 protocol version → 0x2A28 feature bits →
   device info `F3` → bind `03` → time sync `02 01`). Owner: *"yes it stays
   connected and time updated, do health next"*.
9. **Health sync ported** (steps/calories/distance hourly, live totals, sleep,
   heart rate, BP, SpO2 decoders). Fixes: stale "sync in progress" after a
   reconnect; sleep has no bed/wake times (anchored to 07:00, one record per
   night). First heart-rate reading arrived after enabling HR auto-measure
   (`02 16`).
10. **Notifications ported** (`02 12` message push with per-app icon type,
    `02 11` call states). Fixes: MIUI **Autostart** blocked the notification
    listener (enabled via `appops set … 10008 allow`); listener re-link needed
    after every reinstall; `getString` returned "" for WhatsApp's styled text.
    Owner: *"message worked but not call"*.
11. **WhatsApp calls.** MIUI's `checkInterceptListenerForMIUIInternal` withholds
    WhatsApp call notifications from third-party listeners; the owner also has
    a Dual-apps WhatsApp (user 999). Switched to detecting app calls through
    `AudioManager` mode changes (RINGTONE/IN_COMMUNICATION/NORMAL). The watch's
    call screen only serves its Bluetooth hands-free link, so app calls are now
    sent as a WhatsApp-icon "Incoming call" alert. Owner: *"letsmove we move
    test the rest later"* — **not yet confirmed working**.
12. **Custom watch faces.** Found HryFine's custom-face path: `0F 04/05/06`
    commands plus a transfer over the `0xFF00` service (`D5`/`D6` frames, CRC-8
    poly 0x31). Images must be Bluetrum **PAR**; the encoder is native
    (`libabpartool.so`), reused from the owner's HryFine install via a JNI
    shim. Owner: *"it worked but it does postch [isn't posh]"*.
13. **Faces, prayer times, notes.** Built drawn faces (Prayer Times, Night
    Mosque, Arabesque Gold, Marble & Gold), offline prayer-time maths +
    exact-alarm alerts, and "Share → Send to watch" for notes. Owner asked for
    open-source premium/Islamic faces (*"dont copy"* iQibla).
14. **Image quality + licensed photos.** Added HQ downscale, unsharp mask,
    Floyd–Steinberg dithering to RGB565. Replaced unverified-licence photos with
    32 Wikimedia Commons Quality/Featured images (CC0/PD/CC BY/CC BY-SA) and a
    credits screen. Owner: *"i have seen it , can we increase the quality…"*.
15. **Owner asked why not dedicated faces → dial-format reverse engineering.**
    HryFine's dial store downloads over **plain HTTP** (`hrycdn.768.plus`);
    captured 5 dials with a temporary USB-tunnelled proxy while the owner
    installed dials in HryFine (proxy removed afterwards). Decoded the **WF**
    container and **PAR** image formats; an edited dial installed and showed
    the moved digits (owner: *"it shifted"*).
16. **Live dial compiler.** First compiled dial was 129 KB → watch said "image
    too large". Wrote a palette PAR encoder → 32 KB, verified by decoding on PC.
    Watch accepts all data blocks but never confirms the final file CRC
    (`D5 05 01 83` missing) even with the watch-requested 100 s wait.
    **Test A** (real dial + our dial ID) **installed** → ID not the cause.
    **Test B** (real dial + our encoder's background) is pending.
17. Owner: *"I should have done this long time ago"* → this document.

### 2. Technical Architecture Evolution

All work is in `dial-sender/` of the local clone `C:\Users\MAVERIX\Documents\Fogg-src`
(branch `main`, **uncommitted**).

**New files**
| File | Purpose |
|---|---|
| `ble/HryFineProtocol.java` | HryFine `DF` frame codec, acks, challenge, time payload, health decoders, message/call frames, custom-face + `0xFF00` transfer frames, CRC-8, dial-file (base64 + MD5-token) decoder |
| `ble/HryFaces.java` | Drawn faces, photo/prayer-bar faces, photo catalogue from `credits.json` |
| `ble/HryImage.java` | HQ cover-crop, unsharp mask, Floyd–Steinberg RGB565 dither |
| `ble/HryDialCompiler.java` | WF dial assembler (record table + images, first-use image order) |
| `ble/HryPalettePar.java` | Palette PAR encoder (median-cut ≤255 colours, RLE, row table) |
| `ble/HryLiveFaces.java` | "Live Gold" live dial (gold digits, date, steps, HR, battery) |
| `ble/PrayerTimes.java` | Offline prayer times (MWL 18°/17°, Shafi/Hanafi Asr, high-latitude fallback), Qibla |
| `ble/PrayerScheduler.java` | Exact alarms per prayer, alerts, auto-refreshed prayer face |
| `ble/ParProbe.java` | **Temporary** — dumps PAR outputs for all encoder flag combinations |
| `HryFacesActivity.java` | Faces screen (photo groups, designs, live faces, dial loader, prayer settings, credits) |
| `ShareToWatchActivity.java` | `ACTION_SEND text/plain` → note pushed to the watch in ≤150-char parts |
| `com/bluetrum/abpartool/ParTool.java` | JNI shim for `libabpartool.so` |
| `jniLibs/arm64-v8a/libabpartool.so` | **Proprietary**, copied from the owner's HryFine APK |
| `assets/fonts/hry/` | Amiri, Amiri Bold, Cinzel (+ OFL licence texts) |
| `assets/hryfaces/photos/` | 32 photos (480×572 JPEG) + `credits.json` |

**Modified files**: `BleManager.java` (HryFine mode, reads, health, notifications,
calls, custom-face/dial transfer state machine), `WatchCallController.java`
(audio-mode app-call detection), `WatchNotificationService.java` (call
handling, text extraction, drop-reason logging), `WeatherSync.java`
(`currentLocation` made package-visible), `BootReceiver.java` (reschedule
prayers), `DialEditorActivity.java` + `fragments/DialsFragment.java` (send
images to HryFine watches), `res/layout/fragment_dials.xml` ("Faces" button),
`AndroidManifest.xml` (`USE_EXACT_ALARM`, new activities, receiver).

**Why:** the watch is a HryFine/Bluetrum device, not SMA. Every feature needed a
second protocol path, keyed on `hryMode` (set when the `...dcca9f` UART service
is used), leaving the original SMA path untouched.

### 3. Rejected Paths and Why

- **SMA bind-then-login handshake** — watch ignores all SMA (`0xAB`) frames.
- **HCI snoop logging** — MIUI toggle not wired through; no root for `setprop`.
- **Wireless adb** — MIUI pairing bug; USB works.
- **HryFine verification challenge (`F0`) first** — only "SDK" watches answer
  it; this watch's feature bits say `sdk=false`, so it goes device-info → bind.
- **Notification listener for WhatsApp calls** — MIUI intercepts call
  notifications for non-whitelisted listeners; replaced by audio-mode detection.
- **Pushing calls with `02 11` for app calls** — the watch's call UI only
  responds to its BT hands-free link; app calls are sent as message alerts.
- **Modifying firmware** — no source/tooling for the Bluetrum chip; brick risk.
- **PrayerWatchFace (Apache-2.0) wallpapers** — low-res (~400 px) and the photos'
  own licences unstated; replaced by Wikimedia Commons images.
- **Calling HryFine's signed dial API** — would mean extracting their signing
  secret; not done. Plain-HTTP capture of the owner's own downloads used instead.
- **`adb backup`** of HryFine data — blocked for apps targeting Android 12+.
- **HryFine debug logs** — disabled in release (`LogUtils.setLog(false)`).
- **Non-palette PAR (`rawToPar`) for dial images** — 129 KB dial, "too large";
  dithering also defeats RLE. Palette PAR is used for dial images.

### 4. Current Source-of-Truth Workflow

**Build** (Windows, Git Bash):
```bash
cd /c/Users/MAVERIX/Documents/Fogg-src/dial-sender
./gradlew.bat assembleDebug -PbuildPython="C:/Users/MAVERIX/AppData/Roaming/uv/python/cpython-3.11.15-windows-x86_64-none/python.exe"
```
(Chaquopy needs Python 3.8–3.12; the hermes venv python is now blocked by Windows
Application Control — use the base uv CPython 3.11 above. `local.properties`
holds `sdk.dir=C:/Users/MAVERIX/AppData/Local/Android/Sdk`.)

**Install + re-link notification access** (needed after every reinstall on MIUI):
```bash
ADB=/c/Users/MAVERIX/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
C=com.example.dialsender/com.example.dialsender.ble.WatchNotificationService
MSYS_NO_PATHCONV=1 "$ADB" shell cmd notification disallow_listener $C
MSYS_NO_PATHCONV=1 "$ADB" shell cmd notification allow_listener $C
```
MIUI Autostart for Fogg must stay on (`appops set com.example.dialsender 10008 allow`).
Only one app may hold the watch: force-stop HryFine before using Fogg.

**Logs:** `"$ADB" logcat -d | grep BleManager` (raise buffer with `logcat -G 16M`).
Never paste contact names/numbers/message text into chat (owner rule).

**In-app:** Watch tab → watch faces → **Faces** (photo/prayer faces, designs,
Live faces, Load dial file, prayer settings, Photo credits). Notes: any notes
app → Share → **Send to watch**.

**Do not:** commit `jniLibs/libabpartool.so` or `com/bluetrum/` upstream
(proprietary); call HryFine's signed API; set the phone's global proxy without
removing it afterwards (`settings put global http_proxy :0`).

### 5. Commit/Milestone Map

- **No commits made this phase.** All changes are uncommitted on `main` of the
  local clone (base `b657f3260`, upstream PR #10 merge, v0.8.2).
- Milestones (all verified on the watch unless noted):
  - Connect + bind + time sync ✅
  - Health sync (steps/cal/dist/sleep/HR) ✅
  - Message notifications ✅ · SIM calls ✅ · WhatsApp calls ❓ (untested)
  - HR auto-measure command ✅
  - Custom-face background transfer ✅ (16.8 KB and 55.9 KB faces installed)
  - Install of a real HryFine dial through Fogg ✅ · edited dial ✅ · Test A ✅
  - Compiled "Live Gold" dial ❌ (no final-CRC confirmation)
- Suggested first commit split: (1) HryFine protocol/connect/health,
  (2) notifications/calls, (3) faces/prayer/notes, (4) dial compiler — keeping
  the proprietary `.so` and JNI shim out of any shared branch.

### 6. Next-Step Guidance for a Future Agent or Outsider

1. **Run Test B**: Faces → Load dial file → `dial_TEST_B_ourbg.bin` (in phone
   Downloads; built by `tools/hybrid.py`). Pass ⇒ encoder fine, bisect Live
   Gold's other elements (alpha digit strips with n=64, 11-frame battery,
   preview). Fail ⇒ diff our palette PAR against a real one (header bytes,
   K, padding, max palette size, row-table semantics).
2. Once a compiled dial installs: add analog hands (ParTool hand mode =
   `rawToPar(alpha=true, b=false, mode3=true)`), Islamic live faces (photo bg +
   live digits), and auto-rebuild for prayer refresh.
3. Confirm WhatsApp incoming-call alerts with a call from another phone.
4. Remove `ParProbe` and the long-press "build only" debug hook.
5. Commit in reviewed chunks (see §5); keep proprietary parts local.
6. Port remaining SMA-only settings to HryFine (SpO2/sleep monitoring toggles,
   alarms, battery read, watch-side answer/reject).
7. Deferred owner request: ad blocking on the TV.

### 7. Every Twist, Turn, and Tool Chain

- **jadx 1.5.6** (`C:\Users\MAVERIX\tools\jadx`): `jadx.bat -d src --no-res -j 8 hryfine-3.8.25.apk`
  → `C:\Users\MAVERIX\Documents\HryFine-re\src`. APK pulled with `adb pull` from `pm path`.
- **nRF Connect**: write type BYTE ARRAY, hex without spaces, "Write command".
- **HryFine frame**: `DF len(BE16) sum cmd ver key plen(BE16) payload`; sum is
  over all bytes except index 3; every `DF` must be answered with
  `FD 00 05 sum cmd key 00 00 01`. Protocol version comes from 0x2A27 ("10000" → byte 16).
- **Feature bits (0x2A28)**: byte15 0x10 SDK, 0x20 device info, 0x02 new
  message types; byte14 0x20 push calls; byte18 0x80 is565Bmp, 0x40 is8Bytes;
  byte17 codeFormat 8.
- **Custom face**: `0F 04` → watch `0F 05` (payloadLen field under-reports; read
  to end of frame) → send `0F 05` with bgUpdate=1 → watch `0F 06 [01]` →
  transfer type 2. Clock style = {x, y, RGB565, above, below} (widgets 0 none, 1 date).
- **Transfer (`0xFF00`, notify FF01, write FF02)**: MTU 185; NUS notify off,
  FF01 on; `D5 type 01 step 00 00 len payload sum`; start reply gives packet
  size, interval, group size, timeout (byte 13 × 5 s); data `D6`+chunk;
  group CRC-8 then file CRC-8 (poly 0x31, init 0).
- **WF dial**: `"WF" fc 03 code[8] w h tail[10]`, records from 0x1A:
  `type kind count x y a b c d` (16 B, LE) + u32 start of the **next** record's
  image; last record (02/11 preview) has no offset; images in first-use order.
  Coordinates are centres; `a`=1 centre-aligned; strips vertical. Kinds:
  01/05-07 hands; 02/10 image, 02/11 preview, 02/15 battery icon, 02/16 BT icon;
  04/02 month, 03 day, 04 weekday(14), 05 hour, 06 minute, 07 second, 4e AM/PM,
  40 calories, 41 steps, 42 heart rate, 45 battery%, 46/49 distance int/dec,
  4a unit; 05/01 touch zones.
- **PAR**: `"PA" pal alpha w h mode fmt K n 0000` (18 B). Row i ends at
  `dataStart + K·(i+1) + table[i]`. Palette fmt2: n RGB565 padded to even;
  RLE `c<0x80` run c+1 of index, `c≥0x80` (c−0x7F) literals; index n =
  transparent. Non-palette: `0x80|n` run, `0xC0|n` literals (one code <0x80 unknown).
  `rawToPar` flags: 1st = alpha, 2nd = alt format, 3rd = mode `1e` (hands).
- **Dial capture**: `tools/dialproxy.py` (CONNECT tunnelled, only HTTP `.bin`
  saved) + `adb reverse tcp:8888 tcp:8888` + `settings put global http_proxy
  127.0.0.1:8888`; removed afterwards. Dials are base64 with md5("ZZ22")="=",
  md5("Z22Z")="+", md5("XGHJ")="/".
- **RE scripts** (scratchpad, temporary — copy into the repo if needed):
  `parscan.py`, `render.py` (palette decoder → PNG), `overlay.py`,
  `rebuild.py` (proves byte-identical layout), `hybrid.py` (Test A/B builder),
  `strip.py`, `list.py`/`fetch.py`/`sheet.py`/`prep.py` (Commons pipeline, Pillow venv).
- **Gotchas**: MSYS path mangling (`MSYS_NO_PATHCONV=1`); MIUI install prompt;
  debug vs release signature (uninstall first); Windows Application Control
  blocks the hermes venv python; Commons API rate-limits (sleep + retry);
  bottom nav hides when Status is scrolled (tap 762,2486).

### 8. What Worked, What Did Not

**Worked**: decompiling HryFine was the turning point — every protocol detail
came from the vendor code. Connection, health, messages, SIM calls, HR
auto-measure, custom faces, prayer faces/alerts, notes sharing and installing
real HryFine dials all work. Validating layouts by byte-identical rebuild and
decoding our own output on PC caught errors before touching the watch. Owner
feedback along the way: *"yes it stays connected and time updated"*, *"it
worked but it does postch"*, *"it shifted"*.

**Did not work / open**: WhatsApp calls on the watch (unconfirmed); the
compiled live dial is not yet accepted. Owner pushed back on quality (*"looks
terrible and out of place"* for stretched previews) and asked for licensed
open-source assets rather than copies — both addressed.

**Repeat**: one-variable experiments (Test A/B), decode-and-render checks,
capturing ground truth from the vendor app. **Avoid**: guessing protocol
details without the vendor code; quoting personal data from logs.

---

## Phase 2 — Live-dial bisection and the watch stuck in bootloader (2026-11-02, afternoon)

### 1. Chronological Timeline

1. **Test B passed** (real HryFine dial + background encoded by our
   `HryPalettePar`). Owner: *"it worked great"* → our palette PAR encoder is
   accepted by the watch.
2. Built bisection dials from `live_gold.bin` with `tools/bisect.py`:
   C = full Live Gold with HryFine's real preview; D = bg + hour + minute;
   E = D + weekday; F = D + day/month; G = D + heart/steps/battery%.
3. **Test C failed**: owner — *"test c stalled after progress bar was full,
   watch has disconnected before my test of test d"*. All data blocks were
   accepted; no final-CRC confirmation; the watch then dropped the link.
4. **Watch went blank.** Owner: *"my watch has now become bland when turn it
   one it doesnt, even when i charge dont see any sign that is charging"* and
   HryFine began offering an update. All experiments stopped; Fogg force-stopped.
5. Bluetooth logs showed the watch now advertising at **`…03:3F`** (= normal
   MAC last byte `0x6A ^ 0x55`) exposing only service **`0x18A8`** — HryFine's
   "5610" bootloader / repair mode. nRF Connect could connect.
6. HryFine's update screen kept failing: it looked for `…03:6A` because its
   `supportMacXor55` flag is only learned from a working watch. Fix found:
   **HryFine → Add device → pick the watch**, whose `onConnectSuccess` sees the
   0x18A8 service, saves that device and auto-opens the repair screen. Worked —
   but the screen shows **"Currently the latest version"** with **"Re-upgrade"
   disabled**: HryFine's server has no newer firmware, so it will not reflash.
7. Owner did a forced restart (crown held) — *"still blank"* — and is charging
   for 1 hour.

### 2. Technical Architecture Evolution

- `HryFacesActivity.closeQuietly()` — dismissing the transfer dialog after the
  Activity is gone crashed Fogg (`IllegalArgumentException: not attached to
  window manager`); guarded.
- `BleManager.hryOtaWaitMs` — transfer wait now honours the watch's start-reply
  timeout (byte 13 × 5 s; the watch asked for 100 s) instead of a fixed 30 s.
- No other code changes; still uncommitted on `main`.

### 3. Rejected Paths and Why

- **Sending Test D–G** — paused: the watch must be recovered first.
- **Flashing areas through the 5610 bootloader ourselves** — needs HryFine's
  server partition table (part_id / part_addr / part_len per area); writing
  without it risks a real brick.
- **Calling HryFine's signed OTA API** with the secret embedded in the APK —
  same reason as before; not done.
- **Leaving nRF/Fogg connected during HryFine repair** — the watch accepts one
  connection; this caused "upgrade fail"/"interrupted".

### 4. Current Source-of-Truth Workflow

- **Do not send any compiled (non-HryFine) dial to the watch** until the
  failure cause is known and a recovery path is proven.
- Recovery order: charge 1 h → forced restart (crown held 20 s) → nRF Connect
  on service `0x18A8`: notify `0x2AA8`, write-without-response to `0x2AA9`:
  - GET_VERSION (read-only): `D50F010100000000F800021000`
  - REBOOT: `D50E010100000000E50000`
  (frame = `D5 cmd 01 01 00 00 00 sumHi sumLo lenHi lenLo payload`, sum over
  all bytes incl. payload — from `Cus5610CommandUtils.generalSendBytes`).
- Last resort: seller/manufacturer reflash.
- Repair-mode commands (`Cus5610CommandUtils`): 1 firmware OTA info, 2 4K
  check, 3 partition table, 4 code area, 5 audio, 6 character, 7 picture,
  8 default params, **9 dial area**, 10 total check, 11 MTU, 12 set package,
  13 frame total, **14 reboot**, **15 get version**.

### 5. Commit/Milestone Map

- No commits. Milestones: Test A ✅, Test B ✅, Test C ❌ (watch then entered
  bootloader), Tests D–G not run.

### 6. Next-Step Guidance for a Future Agent or Outsider

1. **Recover the watch first** (see §4). Record the GET_VERSION reply bytes —
   they should show which area the bootloader considers invalid.
2. Only after recovery: find why Test C hangs. Prime suspects — our transparent
   strips use palette n=64 with the transparent index at n; real strips are
   small palettes (≤63) and some use fmt=1; the 11-frame battery strip; the
   weekday strip. Test with D→G one at a time, and **add a safety rule**: never
   send a dial whose every element type hasn't been proven with a minimal dial.
3. Consider decoding fmt=1 palette images and matching real strips exactly.

### 7. Every Twist, Turn, and Tool Chain

- `bisect.py` (scratchpad tools) builds C–G from `live_gold.bin` +
  `0000000100030466.bin`; `rebuild.py` verifies each is layout-consistent.
- Bluetooth diagnosis without app logs: `adb logcat -d -v time | grep -E
  "Appname com.lianhezhuli|bta_gattc_conn|HCI_ERR|connection status="` showed
  HryFine connecting to `03:3F`, dropping it, then timing out on `03:6A`.
- HryFine OTA flow (read-only analysis): `DeviceOtaActivity` →
  `sendOtaOrInfo` (5610 cmd 1) → `OTA_INFO_READY` → server `getOtaUpdateInfo`
  (unique_code) → bins → `Cus5610OTAUtils` (partition table cmd 3, area writes,
  total check cmd 10, reboot cmd 14).
- UI automation caught the owner's contacts screen once; rule added: check
  `mCurrentFocus` immediately before every tap, grep dumps narrowly, delete
  `/sdcard/ui.xml`.

### 8. What Worked, What Did Not

**Worked:** single-variable tests pinned the encoder as good (Test B);
HryFine's add-device route correctly reached the bootloader.
**Did not:** Test C hung the watch and left it in bootloader mode with a blank
screen — the most serious setback so far. HryFine cannot repair it because its
server reports no newer firmware. **Lesson:** an untested element type should
never reach the watch inside a full dial; prove each type in a minimal dial
first, and know the recovery path before experimenting.

---

## Phase 3 — Watch recovered from the dial-crash boot loop (2026-11-02, ~15:20–16:00)

### 1. Chronological Timeline
1. nRF Connect proved too confusing for the owner (the red button opened the
   macro recorder; `0x2AA8` is mis-labelled "CGM Feature"). Owner: *"make
   beginner friendly"*. Switched to driving the watch **from the PC's Intel
   Bluetooth** with `bleak` — no phone taps needed.
2. Read-only `GET_VERSION` → `d6 0f 01 01 00 01 d2 00 04 "V1.2"` (bootloader
   V1.2, status 01 = correct).
3. `REBOOT` acked (`d6 0e 01 01 …`) but the watch kept returning to the
   bootloader: owner saw *"hello"* + charging, then blank → **boot loop**: the
   broken Test C dial was the current face and crashed the firmware on draw.
4. HryFine kept auto-connecting and stealing the link (owner: *"show connection
   succesful multiple times"*); owner turned phone Bluetooth off — *"so that you
   can work cleanly"*.
5. Timing measured: after **GET_VERSION then REBOOT** the watch stays in the
   bootloader ~95 s, then runs normally (`…03:6A`) for roughly a minute before
   crashing. A bare REBOOT without GET_VERSION is ignored.
6. First rescue (44.5 KB dial, 20 ms pacing) reached 89 % before the crash.
   Second rescue in the next window: **HryFine dial `00000101000007EF` (30.8 KB),
   5 ms pacing → `D5 05 01 83 … 00` = installed.** Watch then stayed at
   `…03:6A` for 3 minutes straight — recovered.

### 2. Technical Architecture Evolution
- Scratchpad tools (not in the repo): `watchprobe.py` (read-only bootloader
  probe, optional `--reboot`), `reboottrack.py` (GET_VERSION + REBOOT, then
  address timeline), `rescue.py` (reboot + immediate type-5 dial push over
  `0xFF00`, CRC-8 groups, 5 ms pacing). Python 3.11 + `bleak` in the scratch venv.
- No Fogg code changes.

### 3. Rejected Paths and Why
- nRF Connect manual steps for a non-technical owner — too error-prone.
- REBOOT alone — ignored unless preceded by GET_VERSION.
- Large dial for the rescue — the normal-mode window is too short.

### 4. Current Source-of-Truth Workflow — boot-loop recovery recipe
1. Phone Bluetooth **off** (HryFine/Fogg/nRF would otherwise grab the watch).
2. Watch on charger next to the PC.
3. `reboottrack.py` (GET_VERSION `D50F010100000000F800021000`, then REBOOT
   `D50E010100000000E50000` on `0x18A8`/`0x2AA9`).
4. As soon as `…03:6A` appears (~95 s later): `rescue.py <small real dial> --no-reboot`.
5. Confirm with a 3-minute scan that it stays at `…03:6A`.

### 5. Commit/Milestone Map
- No commits. Milestone: **watch recovered** with dial `…07EF`.

### 6. Next-Step Guidance
1. Owner turns phone Bluetooth back on; reconnect with Fogg or HryFine normally.
2. Keep live/compiled dials **disabled** until the Test C failure is understood.
   Add a hard safety rule in Fogg: only element types proven in minimal dials
   may be compiled, and keep `rescue.py` ready before any dial experiment.
3. Then resume Tests D–G one at a time.

### 7. Every Twist, Turn, and Tool Chain
- Bootloader reply layout: `D6 cmd 01 status … len payload` (status 1 = ok).
- The watch's repair address is the normal MAC with the last byte XOR 0x55.
- Windows BLE MTU negotiated 256 → packet 235, group 10, final wait 100 s.

### 8. What Worked, What Did Not
**Worked:** PC-side BLE control, measuring the boot-loop timing, and replacing
the bad dial with a small genuine one inside the normal-mode window.
**Did not:** phone-app-based recovery (HryFine server had no newer firmware;
apps fought over the connection). **Lesson:** a dial that crashes on draw
creates a boot loop; always have the PC rescue path ready and test dial
element types in isolation first.

---

## Phase 4 — Safe-dial rules found by isolated H/J tests (2026-11-02, ~16:00–21:00)

### 1. Chronological Timeline
- After the Phase 3 recovery, each dial element was tested **in isolation** (H1–H12), all pushed from the PC with `rescue.py --no-reboot` (phone BT off, watch NOT paired in Windows).
- B2 (bright background) and H1 (gold transparent hour digits) displayed → our palette-PAR encoder is correct. Owner: "hour digit is gold and font look funny or different"; the yellow circle in B2 "looked closer to white".
- H8 (our 54×20 weekday) → delayed crash. H8b (real weekday image) and H8c (our weekday at 42×16) stable for 5 min → **the watch enforces per-element frame sizes**.
- H11 (all elements at proven sizes, old background) installed OK.
- H12 (complete live face: emerald vertical-gradient bg, gold 44×58 time, weekday, date, stats, battery) — first 86 KB build refused (status 13); rebuilt with a vertical-gradient bg → 18 KB, but the watch **never sends the final 0x83**.
- Owner paired the watch in Windows settings trying to help → transfers slowed; owner removed it. Owner asked "what was our main aim … or we have swayed" → aim restated: premium dedicated live faces (Islamic + normal) built in Fogg, then Quranic reminders.
- J-series bisection on H12: J0 = bg+time+weekday+preview **installed**; real dial 00000101000007EF **installed**; J1 (+date), J2 (+stats), J3/J4 (+battery) → no final reply / no start reply. Link is flaky at the same time, so an interleaved control run (7EF, J3, J0, J3) was started to separate link flakiness from content.

### 2. Technical Architecture Evolution
- Scratchpad tools (PC, Python venv `imgenv`, Pillow + bleak): `pypar.py` (palette PAR encode/decode), `dialkit.py` (load/build WF), `rebuild.py`, `h1.py`, `stable.py`, `rescue.py`.
- `rescue.py` now retries the whole transfer up to 4× and prints the failing stage (`scan/start/data/final`).
- No Fogg Java changes yet in this phase; `HryLiveFaces.java` is still the KNOWN-BAD oversize builder.

### 3. Rejected Paths and Why
- Oversize element frames (34×62 time strip in Live Gold, 54×20 weekday, 13×22 date, 10×18 stats) → boot loop / black. Dead.
- Per-pixel radial-gradient backgrounds → dial balloons past the size limit (86 KB refused). Use vertical gradients / flat areas.
- Pairing the watch in Windows Bluetooth settings → steals the link. Never do it.
- nRF Connect for the owner → too confusing; PC bleak tools replace it.

### 4. Current Source-of-Truth Workflow — SAFE-DIAL RULES
- Element caps: time digits ≤ 44×58 per frame, weekday 42×16, small numbers ≤ 16×16, battery 16×16 × 11 frames.
- Total dial ≤ ~52 KB (53.0 accepted, 54.6 refused).
- Backgrounds must compress well (vertical gradients). Very bright yellow reads as white → use deeper amber golds; very dark bg reads as black.
- Keep palette n odd for transparent strips (defensive).
- Test every new element type inside a known-good dial before using it in a full face; keep the PC rescue (Phase 3 recipe) ready.
- Push command: `imgenv/Scripts/python.exe -I -u tools/rescue.py dials/<file>.bin --no-reboot`.

### 5. Commit/Milestone Map
- No commits (Fogg work is uncommitted on `main`). Test dials live in the session scratchpad `dials/` folder.

### 6. Next-Step Guidance
1. Finish the J bisection: find why H12 base + any small-number element gets no final reply while H11 (same elements, old bg/34-wide time) installs.
2. Port the safe rules into `HryLiveFaces.java` / `HryDialCompiler.java` (size caps, ≤50 KB guard, odd n, compressible bg, deeper gold, cleaner digit font).
3. Design the premium normal + Islamic (prayer-aware) faces; install from Fogg.
4. Remove `ParProbe.java` and the long-press debug hook; keep Live faces locked until 2–3 pass.
5. Later: Quranic reminders.

### 7. Every Twist, Turn, and Tool Chain
- "REJECTED at final check" in early J runs really meant *no 0x83 reply* (timeout), not a status ≠ 0 rejection — it may be a watch-side stall/crash during validation or the flaky link.
- A later J3 attempt timed out at the very first 0x81 reply, and another dropped mid-connect → the link was flaky; phone BT confirmed off (adb `bluetooth_on = 0`), no stray python processes.

### 8. What Worked, What Did Not
- Worked: palette encoder, isolated element tests, the recovery recipe, the real-dial installs. Owner: "yeah i see the blue green arch face now is beautiful".
- Did not (yet): H12 full live face; root cause under bisection.

## Phase 5 — Live faces locked, Quranic reminders added (2026-11-02, ~21:05–21:25)

### 1. Chronological Timeline
- 21:11 the watch dropped into its 0x18A8 bootloader again (4th time) after the J-series tests; Fogg showed "does not expose a supported Nordic UART service". Owner: "i dont even if we should continue anymore".
- PC rescue (`rescue.py 00000101000007EF.bin`: GET_VERSION → REBOOT → push) brought it back and installed the real HryFine dial.
- Options offered: A) lock live faces and do Quranic reminders, B) keep debugging, C) stop. Owner chose **A**, plus "lets not put what the watch cannot handle", and asked for open-source faces "in terms of what i like".

### 2. Technical Architecture Evolution
- Removed from the app: the Live faces card, its long-press build hook, `onLiveTapped()`, and the `ParProbe` call (`ParProbe.java` deleted).
- Moved out of the Gradle source set into `research/live-dials/` (with README): `HryLiveFaces.java`, `HryDialCompiler.java`, `HryPalettePar.java`.
- New `ble/QuranReminders.java`: loads `assets/quran_reminders.tsv`, rotates through verses without repeats (`quran_index` pref), sends via `BleManager.sendNotification(127, "Quran s:a", text, "com.example.dialsender.quran")`; Arabic and English as two messages 5 s apart.
- New asset `assets/quran_reminders.tsv`: 75 short verses. Each "Quran s:a:" + text is ≤ 196 UTF-8 bytes (the HryFine message limit in `HryFineProtocol.messagePush`), so nothing is truncated. 25 longer candidates (e.g. 2:255, 2:286, 39:53) were dropped rather than cut.
- `PrayerScheduler`: the alarm also runs when `quran_after_prayer` is on; a verse is sent 10 s after the prayer alert, and the face repaint waits until the verse has gone.
- `HryFacesActivity`: new "Quranic reminders" section (after-prayer toggle, language picker, "Send a verse to the watch now", source credits).

### 3. Rejected Paths and Why
- Self-compiled live dials: parked (4 bootloader crashes; the H12 root cause is unresolved).
- Truncating long verses to fit the watch: rejected. The Tanzil licence forbids changing the text, and half a verse is wrong anyway.
- Sahih International / modern translations: copyrighted. Pickthall (1930) is public domain.
- Existing open-source reminder apps (Azkaree, Muslim-Athkar, …) only post phone notifications, have unclear licences, and can't talk to the H19.

### 4. Current Source-of-Truth Workflow
- Build: `gradlew.bat assembleDebug -PbuildPython=C:\Users\MAVERIX\AppData\Roaming\uv\python\cpython-3.11-windows-x86_64-none\python.exe` (Chaquopy needs an explicit buildPython on this PC).
- Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
- Sources: Arabic = Tanzil Simple Clean v1.1 (CC BY 3.0, verbatim, notice kept in the TSV header); English = Pickthall via Tanzil `en.pickthall`.

### 5. Commit/Milestone Map
- No commits. Debug APK built and installed on the phone at 21:23.

### 6. Next-Step Guidance
1. Owner checks whether the watch renders Arabic text ("Send a verse now"). If not, default the language to English.
2. Open-source faces: only through the safe channels — the custom-background face (type 2, the watch draws the time) and real HryFine-format dials. Candidate sources: Wikimedia Commons (already used) and AsteroidOS unofficial-watchfaces artwork (check each face's licence).
3. Later: more verses / hadith, morning-and-evening schedule.

### 7. Every Twist, Turn, and Tool Chain
- The first build failed: `[python3] does not appear to be a valid Python command` → fixed with `-PbuildPython=`.
- Second failure: the variable `now` clashed in `onCreate` → renamed to `sendVerse`.

### 8. What Worked, What Did Not
- Worked: the rescue (4/4), the safe notification channel reused for reminders.
- Did not: custom live dials (parked).

### Phase 5 addendum (21:35–21:50)
- Verses were acked by the watch (`FD 00 05 54 02 12 00 3D 01`) but not displayed: message type 13 ("other") is silently dropped by the H19. Fogg's own packages `com.example.dialsender.prayer` / `.quran` are now mapped to type 1 (SMS) in `HryFineProtocol` → verses appear. This also fixes prayer alerts, which used the same path.
- Owner: "arabic didnt show properly but is ok" → `QuranReminders.DEFAULT_LANG = 1` (English only); the Arabic options remain but are explained.
- Owner asked to make github.com/3llomi/PrayerWatchFace (Wear OS, Apache-2.0). Its live countdown/complications need Wear OS, so it can't run on the H19. Its layout was re-drawn as `HryFaces.prayerClean()` (date + short Hijri under the clock, a light #D3DDF8 panel with NEXT prayer + time, five times along the bottom), selectable via the "Clean layout" switch (`prayer_clean_layout`), routed through `PrayerScheduler.prayerPhotoFace()`. Its 7 wallpapers were NOT bundled: their source/licence is unstated (they look like stock photos); our credited Wikimedia photos are used instead.

### Phase 5 addendum 2 (21:50–22:05) — Prayer Ring, featured photos, vector (SVG) faces
- Owner asked whether an open-source face exists that Fogg can use as-is → no: the H19 only takes a background bitmap (watch draws the clock) or HryFine's proprietary dial format. Open-source faces (Wear OS, AsteroidOS) are programs for other OSes; only their openly licensed artwork/ideas can be reused.
- `HryFaces.prayerRing()`: an original 24-hour prayer ring (idea seen on WatchMaker "Muslim Watch" by arbeitslos; that artwork is NOT open-licensed and was not used). It lives in the Designs grid ("Prayer Ring"), is stored as `PREF_PRAYER_PHOTO = "@ring"` and is repainted at each prayer via `PrayerScheduler.prayerFace()`.
- Owner asked whether SVG would look better → not on the H19 (240×286 RGB565 is the ceiling; Fogg already draws vectors at native size), but yes for authoring/portability and for open hardware (AsteroidOS uses SVG/QML natively). Owner: "yeah both" (SVG + better photos).
- +16 Wikimedia Commons **Featured pictures** (mosque11–20, art6–11), cropped to 480×572, credits merged into `assets/hryfaces/photos/credits.json` (48 photos total). People/crowd shots excluded.
- SVG support: `com.caverock:androidsvg-aar:1.4` (Apache-2.0). New `ble/HrySvgFaces.java` + `assets/hryfaces/svg/` (11 SVGs + credits.json: Bismillah calligraphies by Frater5 / Warraich Sahib, CC BY-SA 3.0; geometric patterns CC BY-SA 4.0 / public domain; mosque silhouettes CC BY-SA 3.0). Each SVG → luminance ink mask → gold foil / ivory / silhouette over a drawn background; new "Vector designs" section with async previews; SVG credits added to the credits dialog. SVGs were scanned for `<script>`, external hrefs, `<image>`, `<foreignObject>`: none.
- Excluded on purpose: "علي ولي الله.svg" (sectarian phrase); "Bismillah gold.svg" (fixed red/gold colours do not mask well).

### Phase 5 close (22:10)
- "Bismillah Gold" (vector face) transferred cleanly (all 0x82 groups OK, final `D5 02 01 83 00…`, "HryFine face: done").
- Owner: "there is no clock on it an it time look amateurish I THINK WE are done for now maybe".
- Open: (1) why the watch shows no clock on this face; photo faces use the same `HryFaces.clock(color, 1)` style at (30,30), so compare the style bytes sent; (2) quality: owner prefers the professional photos; the SVG set needs a real designer or should be trimmed.
- KnowledgeBase created: `C:\Users\MAVERIX\Projects\Obsidian\KnowledgeBase\Fogg\` (7 lessons, 5 decisions).

## Phase 6 — Rescue runbook, firmware question, Faces + Status UI polish (2026-11-02, ~22:10–22:40)

### 1. Chronological Timeline
- Owner asked for a step-by-step explanation of "how you was able interact with firmware and repaired it and why using the justin sun pacer method", and for a repeatable procedure. Clarified: no firmware was modified or repaired. The watch's own bootloader was told to reboot (GET_VERSION → REBOOT) and a known-good dial was installed. There is no "Justin Sun pacer method"; the closest real thing is the 5 ms inter-packet pacing.
- Rescue tools moved out of the session scratchpad into `tools/h19-rescue/` with a ready venv, and `RECOVERY.md` written.
- Owner: "make it an artifact so i can share it" → published **H19 Watch Rescue** (private claude.ai artifact, https://claude.ai/artifact/MbYaLHj2iPc1NJhiYqxfPt). Personal paths were removed; the HryFine dial is mentioned but not shared.
- Owner: "i think we can replace this firmware to make more malleable since we can edit it" → advised against it. We can't edit it: we never read or wrote firmware. The chip is an unknown Bluetrum RISC-V part, there are no public drivers or community firmware, the vendor image would need HryFine's secrets, and the brick risk includes the bootloader. Offered read-only chip identification instead. Owner: "no problem later then".
- Owner: "lets make each section better clear demacation … Quranic reminders and prayers time … appear great", then mid-task "make it more aeusthetics" → the Watch faces screen was rebuilt into framed panels.
- Owner: "the temperature on status page is onvered by the steps arc … location on opposite side and tempature of weather on opposite side at each corner" → chips moved into a corner row above the gauge.
- Owner: "good /update-doc and i would exit now we have done well".

### 2. Technical Architecture Evolution
- New `tools/h19-rescue/`: `rescue.py` (retries ×4 and reports the failing stage), `stable.py`, `watchprobe.py`, `rescue_dial.bin` (HryFine store dial, git-ignored), `requirements.txt` (bleak==3.0.2), `venv/` (git-ignored), `RECOVERY.md`.
- `HryFacesActivity`: the onCreate layout was rewritten as framed panels: `panel()` (surface fill, border, elevation, gradient accent strip, title + subtitle), `button()` (filled / outlined), `pill()` (connection status), `note()`, `divider()`, `gridRow()`, `padRow()`.
  - Order: status pill → **Prayer times** (5 tiles via the new `showTimes()`: next prayer filled in the accent colour, past ones dimmed; Sunrise + Qibla line; switches; "Update my location" button) → **Quranic reminders** (switch, Language + "Send a verse now" buttons side by side, source note) → Mosques / Islamic art / Premium galleries (with face counts) → Drawn designs → Vector designs → Credits button.
  - Face `card()`: watch-shaped 170 dp preview, rounded and clipped, CENTER_CROP, border, two-line names, ripple.
- `StatusFragment.buildHeroActivityCard`: the weather chip that was pinned over the gauge (Gravity END|TOP in a FrameLayout) was replaced by a corner row added to `contentCol` before the gauge. Location (pin icon, muted, ellipsized, hidden when unknown) sits left and the temperature (cloud icon, bold) right. New helper `cornerChip()`.
- New drawable `res/drawable/ic_location_pin.xml` (Material "place", Apache-2.0).

### 3. Rejected Paths and Why
- **Replacing the H19 firmware:** rejected for now. There's no firmware image, no chip ID, no drivers or community port for Bluetrum watches, the vendor download needs HryFine's embedded secrets (owner rule: don't use them), and a bad flash could kill the bootloader that makes rescue possible. Alternatives offered: PineTime, or AsteroidOS on a used Wear OS watch.
- **Keeping location and temperature in one chip over the gauge:** rejected because the steps arc covered it.

### 4. Current Source-of-Truth Workflow
- **Rescue:** `cd tools\h19-rescue` → `venv\Scripts\python stable.py 60` → `venv\Scripts\python rescue.py rescue_dial.bin` (add `--no-reboot` if already in normal mode) → `stable.py 120` must print STABLE. Preconditions: phone BT off, watch not paired in Windows, watch on its charger near the PC.
- **Build:** `gradlew.bat assembleDebug -PbuildPython=C:\Users\MAVERIX\AppData\Roaming\uv\python\cpython-3.11-windows-x86_64-none\python.exe`, then `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
- **Large Java or Markdown edits from Bash:** write the content or a Python patch file with the Write tool, then run or append it. Inline heredocs with mixed quotes broke ("unexpected EOF"), and `sed` turned `\U…` in a Windows path into uppercase garbage.

### 5. Commit/Milestone Map
- No commits. Everything is still uncommitted on `main` (11 modified files, plus untracked `tools/`, `research/`, assets and the new Java files).
- APKs installed on the phone at ~22:25 (panel UI) and ~22:35 (status corners).
- Artifact published: H19 Watch Rescue, version 1.

### 6. Next-Step Guidance
1. Owner to check the new Faces panels and the status corners on the phone. Offer a screenshot review (passive `adb exec-out screencap`, only while Fogg is in focus).
2. Fix the missing clock on SVG faces (compare the style bytes sent for `HrySvgFaces` vs photo faces), or remove the vector section, since the owner found it "amateurish".
3. WhatsApp call alerts on the watch.
4. Decide what to commit: a feature branch, excluding `jniLibs/libabpartool.so`, `com/bluetrum`, `rescue_dial.bin`, `venv/`.
5. Optional, later: read-only chip identification if the owner revisits custom firmware.

### 7. Every Twist, Turn, and Tool Chain
- "Justin Sun pacer method": no such method was used. Explained as the 5 ms `asyncio.sleep(0.005)` between write-without-response packets plus the 10-packet group CRC handshake.
- `py -3.11` doesn't find the uv-managed Python on this PC (`py -0` lists it as "Astral/CPython3.11.15"), so the venv was created with the explicit uv path.
- The Artifact is private by default; the owner must use its Share menu before others can open it.
- The location-pin icon didn't exist in the app; it was added as a vector drawable so it tints like the cloud icon.

### 8. What Worked, What Did Not
- Worked: the rescue runbook plus tools, preserved permanently with a working venv; a clean build and install of both UI changes; honest scoping of the firmware idea (owner accepted: "no problem later then").
- Owner feedback this phase: "good", "we have done well".
- Not yet verified by the owner: how the redesigned Faces panels and status corners look on screen.

## Phase 7 — Pushed to GitHub, step accuracy, weather/alarms/find-watch (2026-11-02, ~22:45–23:10)

### 1. Chronological Timeline
- Owner: "lets commit step by step … push this app to my github … tell miguel" → 5 commits on branch `h19-hryfine-support`, then fork `Maverick-lab-maintaner/Fogg` and push. The auto-mode classifier first blocked the public fork until the owner said "go ahead with the fork and push". The owner asked "i hope hryfine cannot sue me"; answered (not legal advice): no HryFine/Bluetrum code, library, dial or secret is in the repo, and interoperability work is generally allowed.
- Comment posted on the owner's own issue MiguelDLM/Fogg#9 (where Miguel had shipped the `...9f` UUID pre-release): https://github.com/MiguelDLM/Fogg/issues/9#issuecomment-6590144202
- Owner: "check if the step tracker is accurate enough" → data audit found (a) the last hourly bucket stamped at next midnight, which Status's max() showed as today's steps (1,658 shown vs the real 711), and (b) no user profile was ever sent to the watch (stride 62 cm = firmware default 170 cm / 55 kg).
- Owner chose: weather, alarms, find my watch. All three were built and acked. Owner: "all three worked, commit and push".

### 2. Technical Architecture Evolution
- `ParEncoder` (new): reflection bridge to `com.bluetrum.abpartool.ParTool`, so the public repo builds without the proprietary encoder; `.gitignore` excludes `libabpartool.so`, `com/bluetrum/`, the rescue venv and the rescue dial.
- `HryFineProtocol`:
  - `decodeSteps` stamps hour h at `day + (h+1)*3600 - 1`.
  - New builders, all from HryFine 3.8.25 `SettingIssuedUtils`: `profile()` (key 4), `stepGoal()` (key 3), `findWatch()` (key 11), `alarms()` (key 2, 5 B/alarm), `weather()` (key 29), `weatherIcon()` (Fogg WEATHER_* → watch icons 0–8).
- `BleManager`:
  - `dropMidnightRecords()` runs after each steps sync.
  - `sendUserProfile`, `sendStepGoal`, `sendWeather` and `sendFindWatch` have HryFine branches; `sendGoals` stops after the step goal on HryFine.
  - Alarms on HryFine are phone-owned: `pushHryAlarms()` stores the full list and sends only switched-on alarms, filling today's date for undated repeating ones.
  - On HryFine session-ready the sequence is: profile at 2.0 s, goal at 2.6 s, prayer at 4 s, weather at 9 s.
- `DeviceFragment` + `fragment_device.xml`: the "Ring watch" row is restored and shown only for HryFine watches (SMA ignores FIND_WATCH).

### 3. Rejected Paths and Why
- Splitting the step fix and the features into separate commits: the hunks share files and interactive staging isn't available here, so one feature commit was made.
- Re-enabling Find watch for every watch: SMA models ACK it but never ring (upstream note), so it stays HryFine-only.

### 4. Current Source-of-Truth Workflow
- Remotes: `origin` = MiguelDLM/Fogg, `fork` = Maverick-lab-maintaner/Fogg. Work on `h19-hryfine-support`, `git push fork h19-hryfine-support`.
- Before every push: `git diff --name-only <base>..HEAD | grep -iE "bluetrum|libabpartool|\.so$|\.bin$|venv"` must print nothing.

### 5. Commit/Milestone Map
- `690a6d7` chore gitignore · `1a25520` feat(hryfine) · `a523586` feat(faces) · `d8a1b9b` fix(status) · `835a89b` docs (all pushed).
- This phase: feat(hryfine) weather/alarms/find-watch/profile + step fix, and this docs entry.

### 6. Next-Step Guidance
1. Owner to fill in Settings → Profile (it is still at the defaults: 30 y, 170 cm, 70 kg).
2. A 200-step walk test to judge the sensor (±10 % is normal for wrist trackers).
3. The remaining HryFine settings not yet wired: raise-to-wake, 12/24 h, wrist, sedentary/drink/medicine reminders, DND, HR auto-monitoring, camera shutter.
4. Missing clock on SVG faces; WhatsApp call alerts.

### 7. Every Twist, Turn, and Tool Chain
- `gh repo fork MiguelDLM/Fogg --remote` fails on gh 2.95 ("--remote unsupported when a repository argument is provided"); run `gh repo fork --remote=true --remote-name=fork` inside the clone.
- Removing the commented-out find-watch block also removed `FIND_WATCH_TIMEOUT_MS`; re-added.
- Stored health data was read with `adb shell run-as com.example.dialsender cat shared_prefs/dial_sender_prefs.xml`. Only step/distance/calorie series were parsed; the copy was deleted straight after.

### 8. What Worked, What Did Not
- Owner: "all three worked" (weather on the watch, alarm vibration, find-watch buzz).
- Acks seen: profile `02 04`, goal `02 03`, weather `02 1D`, alarms `02 02`, find `02 0B` — all ok.

## Phase 8 — Session close: profile left at defaults (2026-11-02, ~23:10)

### 1. Chronological Timeline
- After the Phase 7 push, the owner was asked to fill in Settings → Profile so distance and calories use real values. Owner: "leave it, I dont like PID info and I need i would say is ok". The profile stays at Fogg's defaults (male, 30 y, 170 cm, 70 kg), which are now sent to the watch.
- Owner ran `/update-doc` "and i would exit now". The session ends here.

### 2. Technical Architecture Evolution
- No code changes. The assistant memory `feedback_no_pii_in_chat.md` was extended: don't ask the owner to enter personal details into apps.

### 3. Rejected Paths and Why
- **Asking for the owner's real profile:** declined by the owner (privacy). Defaults are accepted; step counts are unaffected, and distance/calories are estimates.

### 4. Current Source-of-Truth Workflow
- Unchanged from Phase 7. Branch `h19-hryfine-support` on `fork` (Maverick-lab-maintaner/Fogg) is the canonical copy. The phone runs the build from commit `480203e` plus the local proprietary encoder.

### 5. Commit/Milestone Map
- This entry: docs commit on `h19-hryfine-support` (no code).

### 6. Next-Step Guidance
1. Don't prompt for profile data again; treat distance/calories as estimates.
2. Optional: a 200-step walk test to judge the sensor.
3. Not yet wired: raise-to-wake, 12/24 h, wrist, sedentary/drink/medicine reminders, DND, HR auto-monitoring, camera shutter.
4. The SVG faces' missing clock; WhatsApp call alerts.
5. Watch MiguelDLM/Fogg#9 for Miguel's reply; open a PR only if he asks.

### 7. Every Twist, Turn, and Tool Chain
- Nothing new; see Phases 1–7 and `tools/h19-rescue/RECOVERY.md`.

### 8. What Worked, What Did Not
- Day total: the watch connected, synced, got notifications and calls, prayer and Quran reminders, faces, weather, alarms and find-watch, and was rescued from its bootloader 4×. Code is published on GitHub and Miguel has been informed.
- Owner feedback: "all three worked", "we have done well".

## Phase 9 — Silent link, Bluetooth "going off", stuck prayer location (2026-11-03, ~13:05–13:40)

### 1. Chronological Timeline
- Owner reported three problems: (1) "anytime turn on the blue on my watch it goes off again"; (2) "you hard coded the location, which affects my prayer times"; (3) "my steps has not been recored on fogg since yester".
- Stored data: the last real steps and heart rate were from 06:31. Fogg kept "syncing" (13:06) but every sync ended "watch went quiet". A 3-minute logcat capture showed the BLE link to `…03:6A` alive all morning with **zero** replies from the watch. Fogg was also sending SMA frames (music, "reminder READ") to the H19.
- Location: the stored prayer/weather location is a real place (checked by distance only, coordinates never printed), not a fallback. But the phone's system Location is OFF, so Fogg reused a stale fix and only refreshed on a manual tap.
- After installing a first fix and relaunching, the log showed the real trigger: on connect, Fogg started the health sync **and** the ~82 KB auto prayer face together. The watch dropped the link about 1 s later (`Disconnected (status=8)`). The reconnect stalled at "Enabling notifications…" because `hryOtaStep` was never reset, so descriptor writes went to the face-transfer handler.
- Second fix installed. A fresh connection gave SESSION READY, a full sync (1,142 steps today, heart rate, sleep), weather, then the face 30 s later ("done" in 12 s) with no disconnect. Committed and pushed (owner: "yes push it").
- Owner: "it went of again now". Fogg's log showed **no** BLE drop (sync at 13:31, heart rate at 13:35). Phone Bluetooth dump: `…03:3F` is also the watch's **classic calling Bluetooth** (HFP). It was bonded on 29–31 Oct but is no longer in the bonded list (only `…03:6A` is). The watch's call Bluetooth turns itself off when it finds no paired phone. Owner was given steps to re-pair "Smartwatch" in phone Settings → Bluetooth.

### 2. Technical Architecture Evolution
- `BleManager`:
  - `enqueueLogicalFrame` drops any non-`DF`/`FD` frame in HryFine mode (logs the first 20).
  - New fields `lastRxMs`, `lastSilentRecoveryMs`, `smaDropLogged`.
  - `recoverSilentHryLink()` calls `gatt.disconnect()` when a HryFine sync got no reply for ≥60 s; at most every 5 min; normal auto-reconnect follows.
  - The disconnect handler resets `hryOtaStep`, `hryPendingPar` and reports `onTransferFailed("connection lost")`.
  - New `isBusy()` (health sync, face transfer or file transfer active).
- `PrayerScheduler`:
  - `pushFace` waits while `ble.isBusy()` (retries every 15 s, up to 8 times) and skips if `faceSignature()` (photo | clean layout | date | next prayer | lat,lon) equals the last face sent. New `pushFaceNow()` forces a send.
  - `onWatchConnected` delays the face by 30 s.
  - Location: `PREF_MANUAL`, `PREF_PLACE`, `PREF_LOC_TIME`. `refreshLocation(c, force)` stores the place name. `chooseCity()` geocodes and sets prayer and weather coordinates. `refreshIfStale()` runs on connect and at the midnight tick when the location is over 12 h old and not chosen by hand.
- `WeatherSync`: `currentLocation(ctx, preferChosen)` prefers a chosen city; new `placeName()` and `geocode()` (Open-Meteo geocoding API, no key).
- `HryFacesActivity`: "Use my location" + "Choose city" buttons; the times line shows "Times for <place> (chosen | phone location)"; enabling the auto face uses `pushFaceNow`.

### 3. Rejected Paths and Why
- **"Location is hard-coded":** not the case. It was a stale last-known fix with the phone's Location off. Fixed with a city picker and auto-refresh rather than more GPS polling.
- **Blaming setting key 22:** it is heart-rate auto-test, not Bluetooth.
- **Treating "BT goes off" as one bug:** the first cause (face + sync collision) is fixed. The remaining one is the classic calling Bluetooth losing its phone pairing, which is a user-side pairing step, not Fogg.

### 4. Current Source-of-Truth Workflow
- Diagnose a "connected but nothing syncs" state: `adb logcat -v time BleManager:D *:S`. Look for SESSION READY, `Rx [`, "went quiet", "Disconnected (status=…)", "HryFine face:". Never print `title=`/`content=` lines (PII).
- The watch has two addresses:
  - `41:42:E1:F7:03:6A`: BLE (Fogg).
  - `41:42:E1:F7:03:3F`: classic calling Bluetooth (must be paired in phone Settings) **and** the BLE bootloader address during a crash.

### 5. Commit/Milestone Map
- `8eb5c92` fix(hryfine): stop the watch dropping Bluetooth; choose prayer city — pushed to `fork/h19-hryfine-support`.
- This docs entry.

### 6. Next-Step Guidance
1. Confirm the owner re-paired the calling Bluetooth and that it now stays on.
2. Optional: have Fogg offer the classic pairing (`createBond` on `…03:3F`) as HryFine does, instead of a manual step.
3. Watch for "Skipped SMA-format frame" lines. Each one is a feature still using the SMA path on HryFine and is a candidate for a HryFine implementation (reminders, music control, menstrual "GIRL_CARE").
4. The SVG faces' missing clock; WhatsApp call alerts; remaining HryFine settings.

### 7. Every Twist, Turn, and Tool Chain
- The first relaunch looked healthy until the face fired; only reading the whole post-launch log exposed the collision and the stale `hryOtaStep`.
- `adb logcat -c` before relaunching (`adb shell monkey -p com.example.dialsender -c android.intent.category.LAUNCHER 1`) gives a clean connection trace.
- Health data was checked via `run-as … shared_prefs/dial_sender_prefs.xml`, parsing only the step/heart-rate/sleep series and printing distances (not coordinates) for the location; the copy was deleted afterwards.
- `dumpsys bluetooth_manager` revealed the classic `…03:3F` bond history (HFP, `bondState 12`) that explained the remaining "Bluetooth off".

### 8. What Worked, What Did Not
- Worked: a full sync after the fix (1,142 steps, heart rate, sleep); the face sent after the sync without dropping the link; a clean build, install and push.
- Did not (yet): the watch's calling Bluetooth still turns off until the owner re-pairs it. Owner: "it went of again now".

## Phase 10 — Choosing future hardware (research only), staying on the H19 (2026-11-03, ~13:40–14:30)

### 1. Chronological Timeline
- Owner asked about Xiaomi and openvela, cheap Xiaomi options, and cheap watches "we can build from ground up". Research followed (web search only, no code).
- Owner picked the Waveshare 2.06" board as the favourite, then asked about Xiaomi customisability. Owner: "privacy is big concern for me too" → Xiaomi and Amazfit ruled out (vendor account required).
- Owner asked about Amazfit / Zepp OS → strong device, but its first pairing needs a Zepp account.
- Owner: "never having an account, let's go with Waveshare, i would judge between waveshare and Pinetime" → comparison, prices, shipping and Vinted advice.
- Owner: "i would use h19 for now" → no hardware bought. Fogg on the H19 remains the product.
- Memory added: `user_privacy_first.md` (rank account-free and offline options first).

### 2. Technical Architecture Evolution
- No code changes. Research results are recorded below for the future hardware decision.

#### Table A — Xiaomi / openvela summary
| Topic | Finding |
|---|---|
| openvela | Xiaomi's open AIoT OS on Apache NuttX, Apache-2.0; open version of internal Vela |
| Retail Xiaomi bands | Locked firmware; Mi Band 8 custom FW needed SWD pins (Hackaday) |
| What is customisable | Watch faces; Vela JS quick apps (.rpk) via official AIoT-IDE + community sideload tools; Gadgetbridge "mostly supported" (Band 10) |
| Limits | No core-firmware changes; sideloading unofficial; quick apps have no health API; Arabic unconfirmed; **Xiaomi account at setup** |
| Smart Band 10 | ~21-day battery, HR + SpO2, from $49.90 / €49.99 (UK ~£39.99) |

#### Table B — Build-from-scratch / open hardware
| Hardware | Price | Screen | Notes |
|---|---|---|---|
| Waveshare ESP32-S3-Touch-AMOLED-2.06 | ~$30–32 (no battery); ~$43 / €35.54 with battery | AMOLED 410×502 | IMU (QMI8658), RTC (PCF85063), PMIC (AXP2101), dual mics, 400 mAh recommended; **no heart-rate sensor**; dev kit, not sealed |
| PineTime | $26.99 / $34.99; EU €67.50 incl. VAT + free shipping; refurbished €42.50 | 240×240 LCD | HR sensor, InfiniTime, Gadgetbridge native, ~1 week battery, proprietary charging cradle |
| LILYGO T-Watch S3 | ~$40–43 | 240×240 LCD | ESP32-S3 + LoRa, 1–2 days battery |
| Watchy | ~$35–65 | E-paper | Weeks of battery |

#### Table C — Device options vs. the owner's goals
| | Battery | Heart rate | Faces/apps | Freedom | Account needed |
|---|---|---|---|---|---|
| H19 (now, with Fogg) | Days | Yes | Pictures only | Low | No |
| Xiaomi Band 10 | ~21 days | Yes + SpO2 | Custom faces + own apps | Medium | Yes (at least at setup) |
| Amazfit Bip 6 | ~14 days | Yes | Official mini-apps + faces | Medium | Once at setup (+ after any reset) |
| Waveshare 2.06 | 1–2 days | No | Anything we write | Total | Never |
| PineTime | ~1 week | Yes | Open firmware (C++ faces) | High | Never |

#### Table D — Privacy view
| | Account | Cloud | Who controls software | Health sensors |
|---|---|---|---|---|
| Waveshare + own firmware | None | None | Owner | Steps only |
| PineTime + InfiniTime + Gadgetbridge | None | None | Open-source community | Steps + HR |
| H19 + Fogg | None | None (Open-Meteo for weather/geocoding only, no account) | Fogg ours; watch FW closed | Steps, HR, sleep |
| Xiaomi Band 10 | Xiaomi account | Xiaomi cloud | Xiaomi | Steps, HR, SpO2 |
| Amazfit Bip 6 | Zepp account once | Offline after Gadgetbridge switch | Zepp | Steps, HR |

#### Table E — Waveshare vs PineTime (owner's final shortlist)
| What matters | Waveshare 2.06 | PineTime |
|---|---|---|
| Beautiful normal + Islamic faces | Much better (AMOLED 410×502) | Like the H19 |
| Arabic text / Quran | Yes (fonts in 32 MB flash, LVGL bidi) | Hard (small flash, fonts compiled in) |
| Live prayer countdown / ring | Yes | Yes, plainer |
| New faces | Easy to add | C++ compiled in; reflash per change |
| Heart rate | No | Yes |
| Battery | 1–2 days | ~1 week |
| Ready-made software | Community bases; we build the rest | InfiniTime + Gadgetbridge out of the box |
| Finished-watch feel | Dev kit, not sealed | Sealed watch |
| Price | ~$30–43 | ~$27–35 (+ cradle if missing) |
| Phone app | Fogg (to add); can also emulate InfiniTime's BLE for Gadgetbridge | Gadgetbridge now; Fogg would need porting |

#### Table F — Where to buy (as found 2026-11-03; verify before buying)
| Item | Shop | Price | Notes |
|---|---|---|---|
| Waveshare 2.06 | Waveshare store | $29.99–31.99 | No battery in standard version |
| Waveshare 2.06 | OpenELAB (EU) | €35.54 | With battery |
| Waveshare 2.06 | Electrokit (SE) | 595 SEK incl. VAT | Few in stock |
| Waveshare 2.06 | eBay | $39.12 + $9.78 shipping | Varies by seller |
| PineTime | PINE64 EU | €67.50 incl. VAT, free shipping | 48 h dispatch, 2-year warranty |
| PineTime (refurb) | PINE64 EU | €42.50, free shipping | Includes charging cradle |
| PineTime | Pine Store (CN) | $26.99 / $34.99 | UK: add import VAT and handling (~×1.5 rule of thumb) |
| Either | Vinted | Not indexed by web search | Search in the app; check that the cradle (PineTime) or battery (Waveshare) is included |

Open-source starting points for the Waveshare: waveshareteam/ESP32-S3-Touch-AMOLED-2.06 (official examples), 44xp4jdk5m-spec/esp32-s3-watch-os, withinaz/watch-esp32, cristianprice/ESP32-S3-Touch-AMOLED-2.06, ensmith0402/ensmith_watch_2.1 (deep sleep), infinition/waveshare-watch-rs (Rust). **Licences not yet checked.**

### 3. Rejected Paths and Why
- **Xiaomi Band / Amazfit:** require a vendor account (owner: "never having an account").
- **openvela on the Waveshare:** not contradictory (Apache-2.0), but there is no board port (only an ESP32-S3-EYE guide). Every driver would have to be written first; ESP-IDF + LVGL has working drivers.
- **Replacing the H19 firmware:** still rejected (Phase 6).

### 4. Current Source-of-Truth Workflow
- Hardware: **H19 + Fogg** is the daily driver. Waveshare or PineTime is a future option (owner deciding).

### 5. Commit/Milestone Map
- Docs-only commit for this phase.

### 6. Next-Step Guidance
- If a Waveshare is bought: start from the official examples (ESP-IDF + LVGL 9), add a first clock face, then BLE to Fogg; optionally InfiniTime-compatible BLE so Gadgetbridge works too. Check community licences before reusing code.
- On the H19 now, see the Fogg improvement list in Phase 10 §8.

### 7. Every Twist, Turn, and Tool Chain
- Vinted listings are not indexed by web search; only an unrelated listing came back.
- Prices came from shop pages via search snippets and may be stale.

### 8. What Worked, What Did Not
- Owner decisions: privacy first, never an account; H19 for now; Waveshare vs PineTime later.
- **H19/Fogg status — done:** connect/bind/time; health sync (steps, HR, sleep, SpO2, BP); notifications; phone calls; notes; faces (photo, drawn, prayer gold/clean/ring, vector); prayer alerts + auto face; Quran reminders; weather; alarms; find watch; profile + step goal; HR auto-monitoring; dial-file install; rescue tools.
- **Not yet done on the H19** (HryFine supports these; Fogg sends nothing or only SMA frames, which are now skipped): battery level (the standard Battery Service 0x180F exists), raise-to-wake, 12/24 h, units, wrist, sedentary/drink/medicine reminders (the Fogg reminder screens currently do nothing on the H19), DND, sleep-monitoring toggle, power save, language, camera shutter, music control, menstrual reminders (GIRL_CARE), WhatsApp call alerts, the SVG faces' missing clock, a classic-Bluetooth (call) pairing helper.

## Phase 11 — H19 enhancements: watch settings, buttons, battery, phone-side reminders (2026-11-03, ~14:30–15:10)

### 1. Chronological Timeline
- Owner: "call alert are reaching my watch as notifications though from whats, dont hid features i cant use do the rest all of them now".
- Built: battery, watch buttons (cmd 0x0C), a Watch settings screen, HryFine versions of the existing reminder/unit/clock/camera senders, and a calling-Bluetooth pairing helper. On connect, all 10 settings frames were acked and battery read 92%.
- Owner tested. From the log: find phone ×3, camera open/shutter/close OK. Owner: "i think the medication reminder may not have worked". Cause: the 2A28 feature byte 15 = 0x66, and bit 0x08 (medicine) is **off** on the H19, so the firmware ignores key 32. Fix: phone-side medicine alarms.
- Owner: "music button works, it says watch is already paired, and medicine it can sent as a message too".
- Owner: "why does the fog send quran verse everytime i connect". Analysis: verses are sent only after prayer alerts (`quran_after_prayer` = true). The stored `quran_lang` = 0 (Arabic + English), so 2 messages per prayer; `quran_index` = 6 in total. Owner: "lets leave it as is".
- Owner: "do them and lets do svg after" → hand-washing and period reminders made phone-side; push; SVG clock next.

### 2. Technical Architecture Evolution
- `HryFineProtocol`:
  - Builders: `raiseToWake` (key 9), `raiseToWakeAllDay` (58), `doNotDisturb` (20), `doNotDisturbAllDay` (59), `wearHand` (6), `hourFormat` (26), `units` (27), `sedentary` (5), `drinkWater` (33), `medicines` (32, same layout as alarms via `timeList`), `cameraMode` (12).
  - `BATTERY_SERVICE`/`BATTERY_LEVEL_CHAR`; `CMD_CONTROL` 0x0C with the `CTRL_*` codes.
  - Package map: `.medicine` and `.reminder` → type 1.
- `BleManager`:
  - `readHryBattery()` and the battery branch in `onCharacteristicRead`.
  - `onHryControl()`: find phone start/stop, shutter, open camera, media keys.
  - `pushHryWatchSettings()` (on connect at 3.3 s and after edits); `pairHryCallBluetooth()` (BR/EDR `createBond` on BLE address with last byte ^ 0x55).
  - `isHryMedicineNative()` from feature bit 0x08.
  - HryFine branches in `sendCamera`, `sendHourSystem`, `sendUnits`, `sendSedentariness`, `sendDrinkWater`, `sendWash`, `sendGirlCare`; `readReminders` is a no-op on HryFine.
- New `HryWatchSettingsActivity` (Device tab → "Watch settings", HryFine only): raise to wake, DND, wrist / clock / units, move reminder, drink water, 3 medicine times, calls-on-the-watch pairing, camera.
- New receivers `MedicineReminders` and `PhoneReminders` (hand-washing every N min on chosen days and hours; period/ovulation N days ahead). They send "Medicine", "Hand washing" and "Period tracker" messages and are re-armed on boot via `BootReceiver`.

### 3. Rejected Paths and Why
- **Hiding unsupported features:** rejected by the owner. Unsupported ones (medicine, hand-washing, period) are done from the phone instead.
- **Trusting acks as proof:** the medicine key was acked but ignored. Read the feature bits.

### 4. Current Source-of-Truth Workflow
- H19 feature bits (2A28, byte 15): `0x80` clear = raise-to-wake, `0x20` device info, `0x10` SDK, `0x08` medicine, `0x04` drink water, `0x02` new message push, `0x01` workout.
- Watch settings live in prefs `hrw_*` (phone is the source of truth; the watch can't report them) and are re-sent on every connect.

### 5. Commit/Milestone Map
- `1223a8e` feat(hryfine): watch settings, battery, watch buttons, call pairing
- `109803c` fix(hryfine): medicine reminders from the phone when the watch lacks them
- feat(hryfine): hand-washing and period reminders from the phone (this phase)
- This docs entry.

### 6. Next-Step Guidance
1. SVG faces: the watch shows no clock on them (next task).
2. Optional: a verse-frequency option (after every prayer / Fajr + Maghrib / daily / off). Owner chose to leave it as is.

### 7. Every Twist, Turn, and Tool Chain
- The watch-to-phone control dispatch was found in HryFine `NotifyWriteUtils`: the `else` branch of `if (c != 12)` calls `BleDataUtils.setDeviceContro(key)`, so control frames are cmd 0x0C.
- Music buttons work; they didn't show as 0x0C events in Fogg's log, so they may travel over the newly paired classic Bluetooth (AVRCP) instead.

### 8. What Worked, What Did Not
- Worked, confirmed by the owner or the log: find phone, camera, music, battery, all settings acked, calling Bluetooth paired, medicine via phone message.
- Did not: native medicine on the H19 (firmware lacks it), now handled by the phone.
