package com.example.dialsender.ble;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encoder for Bluetrum's palette PAR images, the compact form real dials use.
 *
 *   "PA" 01 00 | w h (LE) | 00 02 | K | n | 00 00 00 00        (18 bytes)
 *   palette: n RGB565 LE, padded to an even count
 *   row table: h int16 — row i ends at dataStart + K*(i+1) + table[i]
 *   rows: byte c < 0x80 -> run of c+1 pixels of the next index byte;
 *         c >= 0x80 -> (c - 0x7F) literal index bytes follow.
 * Index n (one past the palette) is transparent.
 */
public final class HryPalettePar {
    private HryPalettePar() {
    }

    /**
     * @param matte colour that semi-transparent edges are blended onto (palette images
     *              have only on/off transparency); ignored for opaque images.
     */
    public static byte[] encode(Bitmap b, int maxColors, int matte) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        boolean[] clear = new boolean[px.length];
        int[] rgb = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i], a = p >>> 24;
            if (a < 40) {
                clear[i] = true;
                continue;
            }
            int r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255;
            if (a < 255) {
                r = (r * a + ((matte >> 16) & 255) * (255 - a)) / 255;
                g = (g * a + ((matte >> 8) & 255) * (255 - a)) / 255;
                bl = (bl * a + (matte & 255) * (255 - a)) / 255;
            }
            rgb[i] = to565(r, g, bl);
        }
        int[] palette = medianCut(rgb, clear, Math.min(maxColors, 255));
        int n = palette.length;
        int[] lut = new int[65536];
        Arrays.fill(lut, -1);
        byte[] idx = new byte[px.length];
        for (int i = 0; i < px.length; i++) {
            if (clear[i]) {
                idx[i] = (byte) n;
                continue;
            }
            int c = rgb[i];
            if (lut[c] < 0)
                lut[c] = nearest(palette, c);
            idx[i] = (byte) lut[c];
        }

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] rowEnd = new int[h];
        for (int y = 0; y < h; y++) {
            encodeRow(idx, y * w, w, data);
            rowEnd[y] = data.size();
        }
        int total = data.size();
        int K = Math.max(1, Math.round(total / (float) h));
        int padded = (n + 1) & ~1;
        ByteBuffer bb = ByteBuffer.allocate(18 + 2 * padded + 2 * h + total).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 'P').put((byte) 'A').put((byte) 1).put((byte) 0);
        bb.putShort((short) w).putShort((short) h).put((byte) 0).put((byte) 2);
        bb.putShort((short) K).putShort((short) n).putInt(0);
        for (int i = 0; i < padded; i++)
            bb.putShort((short) (i < n ? palette[i] : 0));
        for (int y = 0; y < h; y++)
            bb.putShort((short) (rowEnd[y] - K * (y + 1)));
        bb.put(data.toByteArray());
        return bb.array();
    }

    private static void encodeRow(byte[] idx, int off, int w, ByteArrayOutputStream out) {
        int x = 0;
        List<Byte> lit = new ArrayList<>();
        while (x < w) {
            int run = 1;
            while (x + run < w && run < 128 && idx[off + x + run] == idx[off + x])
                run++;
            if (run >= 3) {
                flush(lit, out);
                out.write(run - 1);
                out.write(idx[off + x]);
                x += run;
            } else {
                lit.add(idx[off + x]);
                if (lit.size() == 128)
                    flush(lit, out);
                x++;
            }
        }
        flush(lit, out);
    }

    private static void flush(List<Byte> lit, ByteArrayOutputStream out) {
        if (lit.isEmpty())
            return;
        out.write(0x7F + lit.size());
        for (byte v : lit)
            out.write(v);
        lit.clear();
    }

    // ------------------------------------------------------------ quantisation

    private static int to565(int r, int g, int b) {
        return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
    }

    private static int r5(int c) { return (c >> 11) & 31; }
    private static int g6(int c) { return (c >> 5) & 63; }
    private static int b5(int c) { return c & 31; }

    /** Median cut over the distinct RGB565 colours, weighted by frequency. */
    private static int[] medianCut(int[] rgb, boolean[] clear, int k) {
        int[] hist = new int[65536];
        for (int i = 0; i < rgb.length; i++)
            if (!clear[i]) hist[rgb[i]]++;
        List<Integer> colors = new ArrayList<>();
        for (int c = 0; c < 65536; c++)
            if (hist[c] > 0) colors.add(c);
        if (colors.isEmpty())
            return new int[] { 0 };
        if (colors.size() <= k) {
            int[] p = new int[colors.size()];
            for (int i = 0; i < p.length; i++) p[i] = colors.get(i);
            return p;
        }
        List<int[]> boxes = new ArrayList<>();
        int[] all = new int[colors.size()];
        for (int i = 0; i < all.length; i++) all[i] = colors.get(i);
        boxes.add(all);
        while (boxes.size() < k) {
            int best = -1;
            long bestScore = -1;
            for (int i = 0; i < boxes.size(); i++) {
                int[] bx = boxes.get(i);
                if (bx.length < 2) continue;
                long score = (long) range(bx) * weight(bx, hist);
                if (score > bestScore) { bestScore = score; best = i; }
            }
            if (best < 0) break;
            int[] bx = boxes.remove(best);
            int ch = widestChannel(bx);
            Integer[] boxed = new Integer[bx.length];
            for (int i = 0; i < bx.length; i++) boxed[i] = bx[i];
            Arrays.sort(boxed, (a, c) -> Integer.compare(channel(a, ch), channel(c, ch)));
            long half = weight(bx, hist) / 2, acc = 0;
            int cut = 1;
            for (int i = 0; i < boxed.length - 1; i++) {
                acc += hist[boxed[i]];
                if (acc >= half) { cut = i + 1; break; }
            }
            int[] lo = new int[cut], hi = new int[boxed.length - cut];
            for (int i = 0; i < boxed.length; i++)
                if (i < cut) lo[i] = boxed[i]; else hi[i - cut] = boxed[i];
            boxes.add(lo);
            boxes.add(hi);
        }
        int[] pal = new int[boxes.size()];
        for (int i = 0; i < pal.length; i++) {
            long r = 0, g = 0, b = 0, wsum = 0;
            for (int c : boxes.get(i)) {
                int wt = hist[c];
                r += (long) r5(c) * wt; g += (long) g6(c) * wt; b += (long) b5(c) * wt; wsum += wt;
            }
            pal[i] = (int) (((r / wsum) << 11) | ((g / wsum) << 5) | (b / wsum));
        }
        return pal;
    }

    private static int channel(int c, int ch) {
        return ch == 0 ? r5(c) * 2 : ch == 1 ? g6(c) : b5(c) * 2;
    }

    private static int widestChannel(int[] bx) {
        int best = 0, bestR = -1;
        for (int ch = 0; ch < 3; ch++) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int c : bx) { int v = channel(c, ch); lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi - lo > bestR) { bestR = hi - lo; best = ch; }
        }
        return best;
    }

    private static int range(int[] bx) {
        int r = 0;
        for (int ch = 0; ch < 3; ch++) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int c : bx) { int v = channel(c, ch); lo = Math.min(lo, v); hi = Math.max(hi, v); }
            r = Math.max(r, hi - lo);
        }
        return r;
    }

    private static long weight(int[] bx, int[] hist) {
        long s = 0;
        for (int c : bx) s += hist[c];
        return s;
    }

    private static int nearest(int[] pal, int c) {
        int best = 0, bd = Integer.MAX_VALUE;
        for (int i = 0; i < pal.length; i++) {
            int dr = (r5(pal[i]) - r5(c)) * 2, dg = g6(pal[i]) - g6(c), db = (b5(pal[i]) - b5(c)) * 2;
            int d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }
}
