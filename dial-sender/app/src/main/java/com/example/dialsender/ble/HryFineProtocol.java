package com.example.dialsender.ble;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.UUID;

/**
 * Frame codec for watches driven by the HryFine app (com.lianhezhuli.hyfit),
 * which answer on the ...dcca9f Nordic UART variant and ignore SMA (0xAB) frames.
 *
 * Data frame: DF | len(BE16) | sum | cmd | ver=1 | key | payloadLen(BE16) | payload
 *   len = payloadLen + 5, total = len + 4, sum = (sum of every other byte) & 0xFF.
 * Ack frame:  FD | 00 05 | sum | cmd | key | 00 00 | status   (status 1 = ok)
 * Every DF frame received must be answered with an ack.
 */
final class HryFineProtocol {
    static final byte DATA_HEADER = (byte) 0xDF;
    static final byte ACK_HEADER = (byte) 0xFD;

    static final byte CMD_SETTING = 0x02;
    static final byte CMD_BIND = 0x03;
    static final byte CMD_FIRMWARE_QUERY = (byte) 0xF0;
    static final byte CMD_VERIFY_CONFIRM = (byte) 0xF1;
    static final byte CMD_DEVICE_INFO = (byte) 0xF3;

    static final java.util.UUID DEVICE_INFO_SERVICE = java.util.UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    static final java.util.UUID PROTOCOL_VERSION_CHAR = java.util.UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
    static final java.util.UUID FEATURES_CHAR = java.util.UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");

    /** Read from 0x2A27 on connect; it goes into byte 5 of every data frame. */
    static byte protocolVersion = 1;

    private HryFineProtocol() {
    }

    /** "V2.1" / "v2" / "2.0" -> 2. */
    static byte parseProtocolVersion(String raw) {
        String s = raw.trim();
        if (s.startsWith("V") || s.startsWith("v"))
            s = s.substring(1);
        if (s.contains("."))
            s = s.substring(0, s.indexOf('.'));
        return (byte) Integer.parseInt(s.trim());
    }

    static byte[] frame(byte cmd, byte key, byte[] payload) {
        int n = payload.length;
        byte[] f = new byte[n + 9];
        f[0] = DATA_HEADER;
        f[1] = (byte) ((n + 5) >> 8);
        f[2] = (byte) ((n + 5) & 0xFF);
        f[4] = cmd;
        f[5] = protocolVersion;
        f[6] = key;
        f[7] = (byte) (n >> 8);
        f[8] = (byte) (n & 0xFF);
        System.arraycopy(payload, 0, f, 9, n);
        f[3] = checksum(f);
        return f;
    }

    static byte[] ack(byte cmd, byte key) {
        byte[] a = { ACK_HEADER, 0x00, 0x05, 0x00, cmd, key, 0x00, 0x00, 0x01 };
        a[3] = checksum(a);
        return a;
    }

    /** Sum of every byte except index 3 (the checksum slot itself). */
    static byte checksum(byte[] f) {
        int s = 0;
        for (int i = 0; i < f.length; i++)
            if (i != 3)
                s += f[i] & 0xFF;
        return (byte) s;
    }

    static boolean checksumOk(byte[] f) {
        return f.length > 3 && f[3] == checksum(f);
    }

    /** Random 8-char challenge; the watch must echo sum(b ^ 0xFF) & 0xFF at frame byte 10. */
    static byte[] newChallenge() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                .getBytes(StandardCharsets.US_ASCII);
    }

    static int expectedChallengeCode(byte[] challenge) {
        int c = 0;
        for (byte b : challenge)
            c += (b & 0xFF) ^ 0xFF;
        return c & 0xFF;
    }

    // ===== Health (cmd 0x05). Every history payload starts with a packed date:
    // byte0 = 0yyyyyym, byte1 = mmmddddd (year-2000, month, day).

    static final byte CMD_HEALTH = 0x05;
    static final int HEALTH_STEPS = 2;
    static final int HEALTH_HEART_RATE = 4;
    static final int HEALTH_BLOOD_PRESSURE = 5;
    static final int HEALTH_SYNC_START = 7;
    static final int HEALTH_SYNC_END = 8;
    static final int HEALTH_SLEEP = 9;
    static final int HEALTH_LIVE_TOTAL = 12;
    static final int HEALTH_SPO2 = 14;

    static byte[] syncAllHistory() {
        return frame(CMD_HEALTH, (byte) 1, new byte[0]);
    }

    static byte[] liveSteps(boolean on) {
        return frame(CMD_HEALTH, (byte) 6, new byte[] { (byte) (on ? 1 : 0) });
    }

    /** Local midnight of the packed date at p[off], p[off+1], in epoch seconds. */
    static long dayStart(byte[] p, int off) {
        int b0 = p[off] & 0xFF, b1 = p[off + 1] & 0xFF;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2000 + ((b0 & 0x7F) >> 1), (((b0 & 1) << 3) + ((b1 & 0xE0) >> 5)) - 1, b1 & 0x1F, 0, 0, 0);
        return c.getTimeInMillis() / 1000L;
    }

    static int be(byte[] p, int off, int len) {
        int v = 0;
        for (int i = 0; i < len; i++)
            v = (v << 8) | (p[off + i] & 0xFF);
        return v;
    }

    /**
     * 24 hourly buckets of steps(2) distance-m(2) calories-cal(4); 0xFFFF = no data.
     * Fogg's activity series are running day totals, so the buckets are summed,
     * stamped at the end of each hour. Returns {steps, kcal, metres} record lists.
     */
    static java.util.List<java.util.List<String>> decodeSteps(byte[] p, long nowSec) {
        java.util.List<String> steps = new java.util.ArrayList<>();
        java.util.List<String> kcal = new java.util.ArrayList<>();
        java.util.List<String> metres = new java.util.ArrayList<>();
        long day = dayStart(p, 0);
        int s = 0, d = 0, cal = 0;
        for (int h = 0; h < 24 && 2 + h * 8 + 8 <= p.length; h++) {
            int o = 2 + h * 8;
            int hs = be(p, o, 2);
            if (hs == 0xFFFF)
                continue;
            s += Math.min(hs, 20000);
            d += be(p, o + 2, 2);
            cal += be(p, o + 4, 4);
            // End of the hour minus one second: stamping at (h+1)*3600 put hour 23's
            // total at the next midnight, so yesterday's total showed up as today's max.
            long t = Math.min(day + (h + 1) * 3600L - 1, nowSec);
            steps.add(t + ":" + s);
            kcal.add(t + ":" + cal / 1000);
            metres.add(t + ":" + d);
        }
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();
        out.add(steps);
        out.add(kcal);
        out.add(metres);
        return out;
    }

    /** date(2) ?(1) count(1), then count x [secOfDay(4) type(1) ?(2) bpm(1)]. */
    static java.util.List<String> decodeHeartRate(byte[] p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        long day = dayStart(p, 0);
        int n = p[3] & 0xFF;
        for (int i = 0; i < n && 4 + i * 8 + 8 <= p.length; i++) {
            int o = 4 + i * 8;
            int bpm = p[o + 7] & 0xFF;
            if (bpm >= 40 && bpm <= 160)
                out.add((day + be(p, o, 4)) + ":" + bpm);
        }
        return out;
    }

    /** date(2) ?(1) count(1), then count x [secOfDay(4) ?(2) systolic(1) diastolic(1)]. */
    static java.util.List<String> decodeBloodPressure(byte[] p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        long day = dayStart(p, 0);
        int n = p[3] & 0xFF;
        for (int i = 0; i < n && 4 + i * 8 + 8 <= p.length; i++) {
            int o = 4 + i * 8;
            int sys = p[o + 6] & 0xFF, dia = p[o + 7] & 0xFF;
            if (sys >= 50 && sys <= 300 && dia >= 30 && dia <= 200)
                out.add((day + be(p, o, 4)) + ":" + sys + "/" + dia);
        }
        return out;
    }

    /** date(2) ?(2) secOfDay(4) ?(3) spo2(1). */
    static java.util.List<String> decodeSpo2(byte[] p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (p.length >= 12 && (p[11] & 0xFF) > 0)
            out.add((dayStart(p, 0) + be(p, 4, 4)) + ":" + (p[11] & 0xFF));
        return out;
    }

    /**
     * Night summary: date(2) startOff(2) light(2) deep(2) endOff(2) awake(2), minutes.
     * Only the totals are sent, so the night is rebuilt as one session ending at
     * endOff (minutes after the date's midnight): deep, then light, then awake,
     * in Fogg's "ts:mode:0:0" form.
     */
    static java.util.List<String> decodeSleep(byte[] p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (p.length < 12)
            return out;
        int light = be(p, 4, 2), deep = be(p, 6, 2), endOff = be(p, 8, 2), awake = be(p, 10, 2);
        if (light > 1439 || deep > 1439 || awake > 1439 || light + deep + awake == 0 || endOff > 1439)
            return out;
        // H19 firmware sends both offsets as 0 (totals only); place the night
        // ending at 07:00 so it lands on the right day with a plausible shape.
        long end = dayStart(p, 0) + (endOff > 0 ? endOff : 7 * 60) * 60L;
        long t = end - (light + deep + awake) * 60L;
        out.add(t + ":17:0:0");
        if (deep > 0)
            out.add((t + 1) + ":1:0:0");
        if (light > 0)
            out.add((t + deep * 60L + 2) + ":2:0:0");
        if (awake > 0)
            out.add((t + (deep + light) * 60L + 3) + ":3:0:0");
        out.add(end + ":34:0:0");
        return out;
    }

    /** Live day totals: steps(4) metres(4) cal(4). Returns {steps, kcal, metres} or null. */
    static int[] decodeLiveTotal(byte[] p) {
        if (p.length < 12)
            return null;
        int s = be(p, 0, 4), d = be(p, 4, 4), c = be(p, 8, 4);
        if (s > 240000 || d > 192000 || c > 4464000)
            return null;
        return new int[] { s, c / 1000, d };
    }

    // ===== Custom watch face (cmd 0x0F) + file transfer on the 0xFF00 service.
    // Face: 0F 04 asks for settings -> watch answers 0F 05 [code(8) x(2) y(2)
    // color(2) above below bgUpdate]; we echo 0F 05 with bgUpdate=1, the watch
    // answers 0F 06 [01] and the PAR-encoded background goes over 0xFF00.

    static final byte CMD_WATCH_FACE = 0x0F;
    static final java.util.UUID OTA_SERVICE = java.util.UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb");
    static final java.util.UUID OTA_NOTIFY = java.util.UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final java.util.UUID OTA_WRITE = java.util.UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final byte OTA_HEADER = (byte) 0xD5;
    static final byte OTA_DATA = (byte) 0xD6;
    static final byte OTA_TYPE_PAPER = 2;
    static final byte OTA_TYPE_DIAL = 5;

    /** HryFine's stored .bin: base64 where md5("ZZ22")/md5("Z22Z")/md5("XGHJ") stand for '=', '+', '/'. */
    static byte[] decodeDialFile(byte[] file) {
        String s = new String(file, StandardCharsets.ISO_8859_1).replaceAll("\\s", "");
        if (!s.matches("[A-Za-z0-9+/=]+"))
            return file; // already raw binary
        s = s.replace(md5Hex("ZZ22"), "=").replace(md5Hex("Z22Z"), "+").replace(md5Hex("XGHJ"), "/");
        try {
            return android.util.Base64.decode(s, android.util.Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String md5Hex(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    static final byte OTA_START = 1, OTA_GROUP_CRC = 2, OTA_FILE_CRC = 3;

    static byte[] requestCustomFace() {
        return frame(CMD_WATCH_FACE, (byte) 4, new byte[0]);
    }

    /** style = {timeX, timeY, colorRGB565, above, below}, or null to keep the watch's. */
    static byte[] customFaceWithNewBackground(byte[] settings, int[] style) {
        byte[] p = applyFaceStyle(settings, style);
        p[16] = 1;
        return frame(CMD_WATCH_FACE, (byte) 5, p);
    }

    static byte[] applyFaceStyle(byte[] settings, int[] style) {
        byte[] p = settings.clone();
        if (style == null)
            return p;
        p[8] = (byte) (style[0] >> 8);
        p[9] = (byte) style[0];
        p[10] = (byte) (style[1] >> 8);
        p[11] = (byte) style[1];
        p[12] = (byte) (style[2] >> 8);
        p[13] = (byte) style[2];
        p[14] = (byte) style[3];
        p[15] = (byte) style[4];
        return p;
    }

    /** D5 type sub step 00 00 len(2) payload sum — sum over every byte. */
    static byte[] otaCommand(byte type, byte step, byte[] payload) {
        int n = payload == null ? 0 : payload.length;
        byte[] f = new byte[n + 9];
        f[0] = OTA_HEADER;
        f[1] = type;
        f[2] = 1;
        f[3] = step;
        f[6] = (byte) (n >> 8);
        f[7] = (byte) n;
        if (n > 0)
            System.arraycopy(payload, 0, f, 8, n);
        int s = 0;
        for (byte b : f)
            s += b;
        f[n + 8] = (byte) s;
        return f;
    }

    static byte[] otaStart(byte type, int packetSize, int fileLen) {
        return otaCommand(type, OTA_START, new byte[] { (byte) (packetSize >> 8), (byte) packetSize,
                (byte) (fileLen >> 24), (byte) (fileLen >> 16), (byte) (fileLen >> 8), (byte) fileLen });
    }

    /** CRC-8, poly 0x31, init 0, no reflection — carried across calls via crc. */
    static int crc8(int crc, byte[] data) {
        for (byte b : data) {
            crc = (crc ^ b) & 0xFF;
            for (int i = 0; i < 8; i++)
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x31) & 0xFF : (crc << 1) & 0xFF;
        }
        return crc;
    }

    // ===== Notifications (cmd 0x02 key 18) and calls (cmd 0x02 key 17)

    static final int CALL_IDLE = 0;
    static final int CALL_RINGING = 1;
    static final int CALL_OFFHOOK = 2;
    private static final int MSG_SMS = 1;
    private static final int MSG_OTHER = 13;
    private static final java.util.Map<String, Integer> MSG_TYPES = new java.util.HashMap<>();

    static {
        String[][] map = {
                { "com.google.android.apps.messaging", "1" }, { "com.android.mms", "1" },
                // Fogg's own prayer alerts and Quran verses: the H19 acks type 13 ("other")
                // but does not display it, so they go out as SMS-type messages.
                { "com.example.dialsender.prayer", "1" }, { "com.example.dialsender.quran", "1" },
                { "com.samsung.android.messaging", "1" }, { "com.tencent.mobileqq", "2" },
                { "com.tencent.mm", "3" }, { "com.facebook.katana", "4" }, { "com.twitter.android", "5" },
                { "com.skype.raider", "6" }, { "jp.naver.line.android", "7" }, { "com.whatsapp", "8" },
                { "com.whatsapp.w4b", "8" }, { "com.kakao.talk", "9" }, { "com.instagram.android", "10" },
                { "com.viber.voip", "11" }, { "com.zing.zalo", "12" }, { "com.alibaba.android.rimet", "14" },
                { "com.sina.weibo", "15" }, { "com.eg.android.AlipayGphone", "16" },
                { "com.microsoft.teams", "17" }, { "com.snapchat.android", "18" },
                { "com.facebook.orca", "19" }, { "com.linkedin.android", "20" },
                { "org.telegram.messenger", "21" }, { "com.vkontakte.android", "22" },
                { "com.microsoft.office.outlook", "23" },
        };
        for (String[] e : map)
            MSG_TYPES.put(e[0], Integer.parseInt(e[1]));
    }

    /** App icon the watch shows; unknown apps use the generic "other" icon. */
    static int messageType(String pkg) {
        Integer t = pkg == null ? null : MSG_TYPES.get(pkg);
        return t != null ? t : MSG_OTHER;
    }

    /** [type, 0, 0] + UTF-8 "title:content", capped at 196 bytes on a character boundary. */
    static byte[] messagePush(int type, String title, String content) {
        String text = (title == null || title.isEmpty()) ? (content == null ? "" : content)
                : title + ":" + (content == null ? "" : content);
        byte[] body = utf8Prefix(text, 196);
        byte[] p = new byte[body.length + 3];
        p[0] = (byte) type;
        System.arraycopy(body, 0, p, 3, body.length);
        return frame(CMD_SETTING, (byte) 18, p);
    }

    /**
     * User profile (setting key 4), as HryFine sends it: sex(1 bit, 1 = male) + age(7),
     * height cm (8 bits, LSB carried on), weight kg (8 bits). The watch uses it for
     * stride length (distance) and calories; without it the firmware assumes 170 cm / 55 kg.
     */
    static byte[] profile(boolean male, int age, int heightCm, int weightKg) {
        int sex = male ? 1 : 0;
        return frame(CMD_SETTING, (byte) 4, new byte[] {
                (byte) ((sex << 7) + (age & 0x7F)),
                (byte) (heightCm >> 1),
                (byte) (((heightCm & 1) << 7) + (weightKg >> 3)),
                (byte) ((weightKg & 7) << 5) });
    }

    /** Daily step goal (setting key 3), int32 big-endian. */
    static byte[] stepGoal(int goal) {
        return frame(CMD_SETTING, (byte) 3, new byte[] {
                (byte) (goal >>> 24), (byte) (goal >>> 16), (byte) (goal >>> 8), (byte) goal });
    }

    /** Make the watch buzz (setting key 11): 1 starts, 0 stops. */
    static byte[] findWatch(boolean start) {
        return frame(CMD_SETTING, (byte) 11, new byte[] { (byte) (start ? 1 : 0) });
    }

    /**
     * Full alarm list (setting key 2), 5 bytes each, as HryFine sends it:
     * year-2000(6) month(4) day(5) hour(5) minute(6) slot(3) pad(3), then the
     * repeat mask (bit0 = Monday … bit6 = Sunday, 0 = once). There is no on/off
     * flag: the list replaces the watch's, so only switched-on alarms are sent.
     * An empty payload clears every alarm.
     */
    static byte[] alarms(java.util.List<int[]> list) {
        byte[] b = new byte[list.size() * 5];
        for (int i = 0; i < list.size(); i++) {
            int[] a = list.get(i); // year, month, day, hour, minute, slot, repeat
            int y = a[0] - 2000, mo = a[1], d = a[2], h = a[3], mi = a[4], slot = a[5] & 7;
            int o = i * 5;
            b[o] = (byte) ((y << 2) + ((mo & 15) >> 2));
            b[o + 1] = (byte) (((mo & 3) << 6) + (d << 1) + (h >> 4));
            b[o + 2] = (byte) (((h & 15) << 4) + (mi >> 2));
            b[o + 3] = (byte) (((mi & 3) << 6) + (slot << 3));
            b[o + 4] = (byte) (a[6] & 127);
        }
        return frame(CMD_SETTING, (byte) 2, b);
    }

    /**
     * Today's weather (setting key 29): year(2) month day 0 icon min max
     * cityLen city(UTF-8, ≤50) current. Icons: 0 clear, 1 few clouds, 2 clouds,
     * 3 overcast, 4 showers, 5 rain, 6 thunder, 7 snow, 8 mist.
     */
    static byte[] weather(Calendar day, int icon, int min, int max, int current, String city) {
        byte[] c = utf8Prefix(city == null ? "" : city, 50);
        if (max <= min)
            min = max - 1;
        byte[] p = new byte[c.length + 10];
        int y = day.get(Calendar.YEAR);
        p[0] = (byte) (y >> 8);
        p[1] = (byte) y;
        p[2] = (byte) (day.get(Calendar.MONTH) + 1);
        p[3] = (byte) day.get(Calendar.DAY_OF_MONTH);
        p[5] = (byte) icon;
        p[6] = (byte) min;
        p[7] = (byte) max;
        p[8] = (byte) c.length;
        System.arraycopy(c, 0, p, 9, c.length);
        p[c.length + 9] = (byte) current;
        return frame(CMD_SETTING, (byte) 29, p);
    }

    /** Fogg's WEATHER_* condition (0..20) → the watch's 9 icons. */
    static int weatherIcon(int fogg) {
        switch (fogg) {
            case 1: return 0;                 // sunny
            case 2: return 1;                 // partly cloudy
            case 3: return 3;                 // overcast
            case 13: return 4;                // drizzle → showers
            case 4: case 14: return 5;        // rain, heavy rain
            case 5: case 6: case 15: case 19: return 6; // thunder, lightning, tornado
            case 8: case 16: case 17: case 18: case 20: return 7; // snow family, sleet
            case 9: case 10: case 11: return 8; // fog, sandstorm, haze
            case 7: case 12: return 2;        // wind → clouds
            default: return 1;
        }
    }

    /** [state, 0] + UTF-8 caller name/number. */
    static byte[] call(int state, String who) {
        byte[] name = utf8Prefix(who == null ? "" : who, 64);
        byte[] p = new byte[name.length + 2];
        p[0] = (byte) state;
        System.arraycopy(name, 0, p, 2, name.length);
        return frame(CMD_SETTING, (byte) 17, p);
    }

    private static byte[] utf8Prefix(String s, int maxBytes) {
        int end = s.length();
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        while (b.length > maxBytes && end > 0) {
            end--;
            if (Character.isLowSurrogate(s.charAt(end)) && end > 0)
                end--;
            b = s.substring(0, end).getBytes(StandardCharsets.UTF_8);
        }
        return b;
    }

    /** Packed clock: 6b year-2000, 4b month, 5b day, 5b hour, 6b minute, 6b second. */
    static byte[] timePayload(Calendar cal) {
        int y = cal.get(Calendar.YEAR) - 2000;
        int mo = cal.get(Calendar.MONTH) + 1;
        int d = cal.get(Calendar.DAY_OF_MONTH);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int mi = cal.get(Calendar.MINUTE);
        int s = cal.get(Calendar.SECOND);
        return new byte[] {
                (byte) ((y << 2) + (mo >> 2)),
                (byte) (((mo & 3) << 6) + (d << 1) + (h >> 4)),
                (byte) (((h & 15) << 4) + (mi >> 2)),
                (byte) (((mi & 3) << 6) + s)
        };
    }
}
