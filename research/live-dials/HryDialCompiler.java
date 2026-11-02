package com.example.dialsender.ble;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds HryFine/Bluetrum "WF" dial files (installed as transfer type 5).
 *
 * Layout, reverse-engineered from dials served to the HryFine app:
 *   0x00 "WF", fc 03, dial code (8), width (2 LE), height (2 LE), 10 header bytes
 *   0x1A element table: 16-byte records
 *        type, kind, count(2), x(2), y(2), a(2), b(2), c(2), d(2)   (all LE)
 *        each followed by the u32 file offset of the NEXT record's image,
 *        except the last record; the first record's image starts right after
 *        the table.
 *   then the PAR images, in order of first use (shared ones stored once).
 * Coordinates are element centres. Multi-frame images (digits, weekdays,
 * icon states) are vertical strips; frame height = image height / count.
 * The watch does not verify the header bytes (tested: an edited dial installs).
 */
public final class HryDialCompiler {

    public static final int T_HAND = 1, T_IMAGE = 2, T_NUMBER = 4, T_TOUCH = 5;

    public static final int K_IMAGE = 0x10, K_PREVIEW = 0x11, K_BATTERY_ICON = 0x15, K_BT_ICON = 0x16;
    public static final int K_MONTH = 0x02, K_DAY = 0x03, K_WEEKDAY = 0x04, K_HOUR = 0x05, K_MINUTE = 0x06,
            K_SECOND = 0x07, K_AMPM = 0x4e, K_CALORIES = 0x40, K_STEPS = 0x41, K_HEART = 0x42,
            K_BATTERY = 0x45, K_DIST_INT = 0x46, K_DIST_DEC = 0x49, K_DIST_UNIT = 0x4a;
    public static final int K_HAND_HOUR = 5, K_HAND_MINUTE = 6, K_HAND_SECOND = 7;
    public static final int ALIGN_CENTER = 1;

    /** One table record; {@code image} is PAR-encoded and may be shared between elements. */
    public static final class Element {
        final int type, kind, count, x, y, a, b, c, d;
        final byte[] image;

        public Element(int type, int kind, int count, int x, int y, int a, int b, int c, int d, byte[] image) {
            this.type = type;
            this.kind = kind;
            this.count = count;
            this.x = x;
            this.y = y;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.image = image;
        }
    }

    private static final byte[] HEADER_TAIL = { 0x16, 0x06, (byte) 0xd5, (byte) 0xc5, 0x1e, 0x24, (byte) 0x80, 0x31, 0x00,
            (byte) 0xfd };

    private HryDialCompiler() {
    }

    /** elements[0] must be the full-screen background; a preview record is appended automatically. */
    public static byte[] compile(byte[] code8, int w, int h, List<Element> elements, byte[] previewPar) {
        List<Element> all = new ArrayList<>(elements);
        all.add(new Element(T_IMAGE, K_PREVIEW, 1, 160, 192, 0, 0, 0, 0, previewPar));

        // Unique images in order of first use.
        Map<byte[], Integer> index = new IdentityHashMap<>();
        List<byte[]> images = new ArrayList<>();
        for (Element e : all)
            if (!index.containsKey(e.image)) {
                index.put(e.image, images.size());
                images.add(e.image);
            }
        int tableEnd = 0x1A + all.size() * 20 - 4;
        int[] start = new int[images.size()];
        int pos = tableEnd;
        for (int i = 0; i < images.size(); i++) {
            start[i] = pos;
            pos += images.get(i).length;
        }

        ByteBuffer bb = ByteBuffer.allocate(pos).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 'W').put((byte) 'F').put((byte) 0xfc).put((byte) 0x03);
        bb.put(code8, 0, 8);
        bb.putShort((short) w).putShort((short) h);
        bb.put(HEADER_TAIL);
        for (int i = 0; i < all.size(); i++) {
            Element e = all.get(i);
            bb.put((byte) e.type).put((byte) e.kind).putShort((short) e.count)
                    .putShort((short) e.x).putShort((short) e.y)
                    .putShort((short) e.a).putShort((short) e.b).putShort((short) e.c).putShort((short) e.d);
            if (i + 1 < all.size())
                bb.putInt(start[index.get(all.get(i + 1).image)]);
        }
        for (byte[] img : images)
            bb.put(img);
        return bb.array();
    }

    // ------------------------------------------------------------ encoding helpers

    /** Opaque image (backgrounds, previews) as a compact palette PAR, like real dials. */
    public static byte[] opaque(Bitmap b) {
        return HryPalettePar.encode(b, 255, 0);
    }

    /** Image with on/off transparency (digits, icons); soft edges are blended onto {@code matte}. */
    public static byte[] alpha(Bitmap b, int matte) {
        return HryPalettePar.encode(b, 64, matte);
    }

    /** Rotatable image (analog hands), the mode real dials use for hands. */
    public static byte[] hand(Bitmap b) {
        return com.bluetrum.abpartool.ParTool.rawToPar(raw(b), b.getWidth(), b.getHeight(), true, false, true);
    }

    private static byte[] raw(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        byte[] r = new byte[px.length * 4];
        for (int i = 0; i < px.length; i++) {
            r[i * 4] = (byte) (px[i] >>> 24);
            r[i * 4 + 1] = (byte) (px[i] >>> 16);
            r[i * 4 + 2] = (byte) (px[i] >>> 8);
            r[i * 4 + 3] = (byte) px[i];
        }
        return r;
    }
}
