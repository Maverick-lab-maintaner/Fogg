import asyncio, time, sys
from bleak import BleakScanner
async def m(sec):
    t0 = time.time(); spans = []
    def cb(d, a):
        if d.address.upper().startswith("41:42:E1:F7:03"):
            ad, t = d.address[-5:], round(time.time() - t0)
            if not spans or spans[-1][0] != ad: spans.append([ad, t, t])
            else: spans[-1][2] = t
    async with BleakScanner(cb): await asyncio.sleep(sec)
    for a, s, e in spans: print(a, "from", s, "s to", e, "s")
    print("STABLE" if spans and all(a == "03:6A" for a, _, _ in spans) else "NOT STABLE")
asyncio.run(m(int(sys.argv[1]) if len(sys.argv) > 1 else 120))
