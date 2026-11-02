"""Rescue: reboot the H19 out of its bootloader and immediately install a known-good dial.

Steps: (1) if the watch is at the bootloader address, send REBOOT (5610 cmd 14);
(2) wait for the normal address; (3) push the dial over the 0xFF00 transfer
service exactly like HryFine's OTAUtils (type 5): start -> groups of D6 packets
with CRC-8 -> file CRC-8 -> wait for completion.
"""
import asyncio, sys, time
from bleak import BleakScanner, BleakClient

BOOT, NORMAL = "41:42:E1:F7:03:3F", "41:42:E1:F7:03:6A"
B_WRITE, B_NOTIFY = "00002aa9-0000-1000-8000-00805f9b34fb", "00002aa8-0000-1000-8000-00805f9b34fb"
O_WRITE, O_NOTIFY = "0000ff02-0000-1000-8000-00805f9b34fb", "0000ff01-0000-1000-8000-00805f9b34fb"
REBOOT = bytes.fromhex("D50E010100000000E50000")
TYPE_DIAL = 5


def crc8(crc, data):
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc << 1) ^ 0x31) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc


def cmd(step, payload=b""):
    f = bytearray([0xD5, TYPE_DIAL, 1, step, 0, 0, len(payload) >> 8, len(payload) & 255]) + payload + b"\0"
    f[-1] = sum(f) & 0xFF
    return bytes(f)


async def find(addr, seconds):
    hit = asyncio.get_running_loop().create_future()

    def cb(d, _adv):
        if d.address.upper() == addr and not hit.done():
            hit.set_result(d)

    async with BleakScanner(cb):
        try:
            return await asyncio.wait_for(hit, seconds)
        except asyncio.TimeoutError:
            return None


async def reboot_bootloader():
    d = await find(BOOT, 30)
    if not d:
        print("bootloader address not seen (maybe already in normal mode)")
        return
    async with BleakClient(d, timeout=20) as c:
        await c.start_notify(B_NOTIFY, lambda _, x: print("  boot RX", bytes(x).hex(" ")))
        await asyncio.sleep(0.5)
        await c.write_gatt_char(B_WRITE, bytes.fromhex("D50F010100000000F800021000"), response=False)
        await asyncio.sleep(5)
        await c.write_gatt_char(B_WRITE, REBOOT, response=False)
        print("reboot sent")
        await asyncio.sleep(1.5)


STAGE = "scan"


async def push_dial(path):
    global STAGE
    STAGE = "scan"
    data = open(path, "rb").read()
    d = await find(NORMAL, 180)
    if not d:
        print("normal address never appeared")
        return False
    print("normal mode seen, connecting")
    async with BleakClient(d, timeout=20) as c:
        mtu = c.mtu_size
        print("connected, mtu", mtu); STAGE = "start"
        q = asyncio.Queue()
        await c.start_notify(O_NOTIFY, lambda _, x: q.put_nowait(bytes(x)))
        await asyncio.sleep(0.3)

        async def reply(step, timeout):
            while True:
                r = await asyncio.wait_for(q.get(), timeout)
                print("  RX", r.hex(" "))
                if r[0] == 0xD6:
                    raise RuntimeError("watch reported transfer error")
                if r[0] == 0xD5 and r[3] == step:
                    return r

        ps = mtu - 3
        await c.write_gatt_char(O_WRITE, cmd(1, bytes([ps >> 8, ps & 255]) + len(data).to_bytes(4, "big")), response=False)
        r = await reply(0x81, 15)
        if len(r) < 14:
            print("watch refused the transfer, status", r[5])
            return False
        packet = min((r[8] << 8) | r[9], ps)
        group = max(1, r[12])
        wait_final = max(30, r[13] * 5)
        print(f"packet={packet} group={group} final-wait={wait_final}s size={len(data)}")
        off, file_crc = 0, 0; STAGE = "data"
        while off < len(data):
            gcrc = 0
            for _ in range(group):
                if off >= len(data):
                    break
                chunk = data[off:off + packet - 1]
                gcrc = crc8(gcrc, chunk)
                file_crc = crc8(file_crc, chunk)
                await c.write_gatt_char(O_WRITE, b"\xD6" + chunk, response=False)
                off += len(chunk)
                await asyncio.sleep(0.005)
            await c.write_gatt_char(O_WRITE, cmd(2, bytes([gcrc])), response=False)
            r = await reply(0x82, 15)
            if r[5] != 0:
                print("group CRC rejected")
                return False
            print(f"  {off}/{len(data)}")
        await c.write_gatt_char(O_WRITE, cmd(3, bytes([file_crc])), response=False)
        STAGE = "final"
        r = await reply(0x83, wait_final)
        print("DONE" if r[5] == 0 else "file CRC rejected")
        return r[5] == 0


async def main():
    if "--no-reboot" not in sys.argv:
        await reboot_bootloader()
    ok = False
    for attempt in range(4):
        try:
            ok = await push_dial(sys.argv[1]); break
        except (asyncio.TimeoutError, asyncio.CancelledError, OSError, Exception) as e:
            print(f"ATTEMPT {attempt+1} failed at stage:", STAGE, type(e).__name__)
            await asyncio.sleep(10)
    print("RESULT:", "installed" if ok else "failed")


asyncio.run(main())
