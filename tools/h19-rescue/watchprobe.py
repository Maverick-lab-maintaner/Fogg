"""Read-only probe of the H19 in its 0x18A8 bootloader: list services, ask GET_VERSION.

Only sends the GET_VERSION query unless --reboot is given.
"""
import asyncio, sys
from bleak import BleakScanner, BleakClient

SVC = "000018a8-0000-1000-8000-00805f9b34fb"
NOTIFY = "00002aa8-0000-1000-8000-00805f9b34fb"
WRITE = "00002aa9-0000-1000-8000-00805f9b34fb"
TARGETS = ("41:42:E1:F7:03:3F", "41:42:E1:F7:03:6A")
GET_VERSION = bytes.fromhex(sys.argv[1]) if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else bytes.fromhex("D50F010100000000F800021000")
REBOOT = bytes.fromhex("D50E010100000000E50000")


async def main():
    print("scanning 15 s...")
    found = {}
    def cb(d, adv):
        if d.address.upper() in TARGETS or (d.name or "").lower().startswith("smart"):
            found[d.address.upper()] = (d, adv.rssi, d.name)
    async with BleakScanner(cb):
        await asyncio.sleep(15)
    for a, (_, rssi, name) in found.items():
        print(f"  seen {a} name={name} rssi={rssi}")
    dev = next((found[a][0] for a in TARGETS if a in found), None)
    if dev is None:
        print("watch not seen - is it nearby and not connected to the phone?")
        return
    async with BleakClient(dev, timeout=20) as c:
        print("connected", dev.address)
        for s in c.services:
            print(" service", s.uuid, [ (ch.uuid[4:8], ",".join(ch.properties)) for ch in s.characteristics ])
        if c.services.get_service(SVC) is None:
            print("no 0x18A8 service - watch is not in bootloader mode")
            return
        got = []
        await c.start_notify(NOTIFY, lambda _, data: (got.append(bytes(data)), print("  RX", bytes(data).hex(" "))))
        await asyncio.sleep(0.5)
        print("TX GET_VERSION", GET_VERSION.hex(" "))
        await c.write_gatt_char(WRITE, GET_VERSION, response=False)
        await asyncio.sleep(5)
        if "--reboot" in sys.argv:
            print("TX REBOOT", REBOOT.hex(" "))
            await c.write_gatt_char(WRITE, REBOOT, response=False)
            await asyncio.sleep(5)
        print("replies:", [g.hex(" ") for g in got])


asyncio.run(main())
