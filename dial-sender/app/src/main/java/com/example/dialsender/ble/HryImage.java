package com.example.dialsender.ble;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Image preparation for the watch's 16-bit (RGB565) screen: high-quality
 * downscaling, a light unsharp mask, and Floyd-Steinberg dithering so
 * gradients and skies do not band.
 */
public final class HryImage {
    private HryImage() {
    }

    /** Centre-crop to the target aspect, then scale down in halving steps for clean detail. */
    public static Bitmap cover(Bitmap src, int w, int h) {
        float targetAspect = w / (float) h;
        int sw = src.getWidth(), sh = src.getHeight();
        Rect crop;
        if (sw / (float) sh > targetAspect) {
            int cw = Math.round(sh * targetAspect);
            crop = new Rect((sw - cw) / 2, 0, (sw - cw) / 2 + cw, sh);
        } else {
            int ch = Math.round(sw / targetAspect);
            crop = new Rect(0, (sh - ch) / 2, sw, (sh - ch) / 2 + ch);
        }
        Bitmap cur = Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height());
        while (cur.getWidth() >= w * 2 && cur.getHeight() >= h * 2) {
            Bitmap half = Bitmap.createScaledBitmap(cur, cur.getWidth() / 2, cur.getHeight() / 2, true);
            if (cur != src) cur.recycle();
            cur = half;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(cur, null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** Unsharp mask with a 3x3 box blur; amount ~0.3-0.6 is subtle. */
    public static Bitmap sharpen(Bitmap b, float amount) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], out = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, bl = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        int p = px[yy * w + xx];
                        r += (p >> 16) & 255; g += (p >> 8) & 255; bl += p & 255; n++;
                    }
                }
                int p = px[y * w + x];
                int pr = (p >> 16) & 255, pg = (p >> 8) & 255, pb = p & 255;
                out[y * w + x] = 0xFF000000
                        | clamp(Math.round(pr + amount * (pr - r / (float) n))) << 16
                        | clamp(Math.round(pg + amount * (pg - g / (float) n))) << 8
                        | clamp(Math.round(pb + amount * (pb - bl / (float) n)));
            }
        }
        Bitmap res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        res.setPixels(out, 0, w, 0, 0, w, h);
        return res;
    }

    /** Floyd-Steinberg error diffusion to 5-6-5 bit levels, returned as ARGB_8888. */
    public static Bitmap dither565(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        float[] er = new float[(w + 2) * 2], eg = new float[(w + 2) * 2], eb = new float[(w + 2) * 2];
        for (int y = 0; y < h; y++) {
            int cur = (y & 1) * (w + 2), nxt = ((y + 1) & 1) * (w + 2);
            for (int i = 0; i < w + 2; i++) { er[nxt + i] = 0; eg[nxt + i] = 0; eb[nxt + i] = 0; }
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x];
                float r = ((p >> 16) & 255) + er[cur + x + 1];
                float g = ((p >> 8) & 255) + eg[cur + x + 1];
                float bl = (p & 255) + eb[cur + x + 1];
                int qr = quant(r, 5), qg = quant(g, 6), qb = quant(bl, 5);
                px[y * w + x] = 0xFF000000 | qr << 16 | qg << 8 | qb;
                spread(er, cur, nxt, x, r - qr);
                spread(eg, cur, nxt, x, g - qg);
                spread(eb, cur, nxt, x, bl - qb);
            }
        }
        Bitmap res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        res.setPixels(px, 0, w, 0, 0, w, h);
        return res;
    }

    /** Everything a face needs before PAR encoding. Photos get sharpened; drawn art does not. */
    public static Bitmap prepare(Bitmap design, int w, int h, boolean sharpen) {
        Bitmap sized = design.getWidth() == w && design.getHeight() == h ? design : cover(design, w, h);
        if (sharpen)
            sized = sharpen(sized, 0.45f);
        return dither565(sized);
    }

    private static void spread(float[] e, int cur, int nxt, int x, float err) {
        e[cur + x + 2] += err * 7 / 16f;
        e[nxt + x] += err * 3 / 16f;
        e[nxt + x + 1] += err * 5 / 16f;
        e[nxt + x + 2] += err * 1 / 16f;
    }

    /** Nearest representable 8-bit value at the given bit depth (as the watch will show it). */
    private static int quant(float v, int bits) {
        int levels = (1 << bits) - 1;
        int q = Math.round(Math.max(0, Math.min(255, v)) * levels / 255f);
        return Math.round(q * 255f / levels);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
