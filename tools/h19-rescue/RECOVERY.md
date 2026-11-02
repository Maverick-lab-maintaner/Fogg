# H19 watch rescue — what happened, how it works, how to do it again

## 1. What was actually wrong (and what was NOT done)

- **The firmware was never modified or "repaired".** Nothing was flashed. The watch's own firmware and its bootloader stayed intact the whole time.
- What broke was a **watch face (dial file)** we had installed. On every start-up the watch loads the active dial. A bad dial made the firmware crash, and its watchdog then dropped the watch into its **bootloader**, a small safe-mode program that only waits for a firmware update.
- In bootloader mode the screen stays dark (or flashes "hello"/charging), HryFine shows "upgrade fail", and Fogg says *"does not expose a supported Nordic UART service … service 0x18A8"*.
- The rescue therefore does two things:
  1. Tells the bootloader to **reboot** into the normal firmware.
  2. Immediately **installs a known-good dial**, so the next start-up doesn't load the bad one again.

## 2. How the watch is reached over Bluetooth

The PC talks to the watch over Bluetooth Low Energy using **bleak**, a Python BLE library, in Windows' built-in Bluetooth. No pairing is needed.

| Mode | Address | What it offers |
|---|---|---|
| Normal | `41:42:E1:F7:03:6A` | Nordic-UART-style service (watch protocol) and service `0xFF00` (file transfer) |
| Bootloader | `41:42:E1:F7:03:3F` (last byte XOR 0x55) | Only service `0x18A8`: notify `0x2AA8`, write `0x2AA9` |

`stable.py` scans for both addresses and prints which one the watch is using over time.

## 3. Where the commands came from

- **Normal-mode dial transfer (service 0xFF00):** read from HryFine's own Android app (its OTA/dial transfer code) and reimplemented in Fogg (`BleManager`) and in `rescue.py`.
- **Bootloader commands (service 0x18A8):** taken from the same app's Bluetrum OTA code, then confirmed by testing:
  - `GET_VERSION` = `D5 0F 01 01 00 00 00 00 F8 00 02 10 00` → the watch replies with "V1.2".
  - `REBOOT` = `D5 0E 01 01 00 00 00 00 E5 00 00` → **only accepted after GET_VERSION**. That is why the tool always asks for the version first.
- After REBOOT the watch takes about **95 seconds** to come back at its normal address.

## 4. How the good dial is installed (transfer protocol)

All on service `0xFF00`: write to `FF02`, replies arrive on `FF01`.

1. **Start:** `D5 05 01 01 00 00 <len16> <packet size 2 bytes><file size 4 bytes> <sum>` (type 5 = dial).
   - The watch answers `D5 05 01 81 …` with the packet size it wants, the group size (10) and a timeout byte (×5 s, here 100 s).
   - Status 13 in this reply means the file is too large.
2. **Data:** each packet is `D6` + a chunk of the file. After every group of 10 packets, send the **CRC-8** (polynomial 0x31) of that group.
   - The watch answers `D5 05 01 82 … 00` (OK) per group.
3. **Finish:** send the CRC-8 of the whole file. The watch answers `D5 05 01 83 … 00` when it has checked and stored the dial.
   - Wait up to the watch's timeout (100 s) for this reply.

## 5. Why the 5 ms pacing

- Data packets are sent as *write-without-response*: fast, but nothing tells the PC the watch has caught up. Sent back-to-back, they overflow the watch's small Bluetooth buffer, packets get dropped, and the group CRC then fails.
- Waiting **5 milliseconds** between packets (`asyncio.sleep(0.005)` in `rescue.py`) gives the watch time to keep up. It's a pause between packets, nothing more.
- The per-group CRC reply acts as a second brake: the PC never gets more than 10 packets ahead.

## 6. Step-by-step rescue (do this if the watch goes dark again)

**Before you start (most failures come from skipping these):**

1. Turn your **phone's Bluetooth OFF**. Otherwise Fogg or HryFine grabs the watch first.
2. Make sure the watch is **not paired in Windows** (Settings → Bluetooth & devices). If "Smartwatch" is listed there, remove it.
3. Put the watch **on its charger, next to the PC**.
4. Close any other Bluetooth tools (nRF Connect, old python windows).

**One-time setup (already done on this PC, 2026-11-02; only needed on a new PC):**
```
cd C:\Users\MAVERIX\Documents\Fogg-src\tools\h19-rescue
C:\Users\MAVERIX\AppData\Roaming\uv\python\cpython-3.11-windows-x86_64-none\python.exe -m venv venv
venv\Scripts\pip install -r requirements.txt
```

**Check which mode the watch is in:**
```
venv\Scripts\python stable.py 60
```
- Only `03:6A` → normal mode. The watch is fine. If the screen is dark, it's a dial problem: run the rescue with `--no-reboot`.
- `03:3F` seen → bootloader. Run the full rescue.
- Nothing seen → do a forced restart (hold the side button 10–15 s), charge 30–60 min, try again.

**Full rescue (bootloader → normal → install good dial):**
```
venv\Scripts\python rescue.py rescue_dial.bin
```
Expected output:
```
boot RX d6 0f 01 01 00 01 d2 00 04 56 31 2e 32     <- version "V1.2"
reboot sent
normal mode seen, connecting
RX d5 05 01 81 ...                                 <- watch accepts the transfer
RX d5 05 01 83 00 00 00 00 5e                      <- dial stored OK
RESULT: installed
```

**Watch already in normal mode, just replace the dial:**
```
venv\Scripts\python rescue.py rescue_dial.bin --no-reboot
```

**Afterwards:** run `stable.py 120`. It should print `STABLE`. Then turn the phone's Bluetooth back on.

## 7. If something goes wrong

| Symptom | Cause / fix |
|---|---|
| `ATTEMPT n failed at stage: scan` | Watch not seen: phone BT still on, the watch is paired in Windows, or it's too far away. |
| `failed at stage: start` repeatedly | Watch connected but its transfer service is stuck. Do a forced restart and try again. |
| Boot replies but no reboot | GET_VERSION must come first; `rescue.py` does this. Run it again. |
| `failed at stage: final` | The watch rejected or choked on the file. Use `rescue_dial.bin`, not a self-made dial. |
| Status 13 at start | File too big (limit ≈ 52 KB). |

## 8. Files here

- `rescue.py`: the rescue. Bootloader GET_VERSION + REBOOT, then the dial transfer, with 4 retries.
- `stable.py`: shows which address (normal or bootloader) the watch advertises over time.
- `watchprobe.py`: read-only. Lists the bootloader's services and asks for its version (`--reboot` also reboots).
- `rescue_dial.bin`: a small analog dial from HryFine's store (30.8 KB) that the watch is known to accept. **HryFine's content: keep it private, do not commit it upstream** (it is git-ignored).
- `requirements.txt`: `bleak==3.0.2`.
