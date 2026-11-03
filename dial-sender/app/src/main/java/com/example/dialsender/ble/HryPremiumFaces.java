package com.example.dialsender.ble;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Original faces drawn in code at the watch's native size, composed around the
 * watch's own clock in the centre (block ≈ x 20–220, y 100–188). The centre is
 * left calm by design, so no dark band is needed behind the time.
 */
public final class HryPremiumFaces {
    private HryPremiumFaces() {
    }

    public static final String[] NAMES = { "Mihrab Arch", "Night Dome", "Zellige", "Minimal Dark", "Aurora" };
    /** The watch's clock block is ~100 px wide (HryFine caps x at 140 on 240 px), so x 70 centres it. */
    private static final int TIME_X = 70, TIME_Y = 100;

    public static int count() {
        return NAMES.length;
    }

    public static HryFaces.Face render(int index, int w, int h) {
        return render(null, index, w, h, null, 0);
    }

    /** Is the info block (next prayer, dates) drawn above the clock for this design? */
    private static boolean infoOnTop(int index) {
        return index == 1;
    }

    /**
     * With {@code times} (today's prayer times), the face also carries the next
     * prayer and the Gregorian + Hijri dates. Fogg repaints it after each prayer
     * and just after midnight, so these stay correct.
     */
    public static HryFaces.Face render(android.content.Context ctx, int index, int w, int h, long[] times, long now) {
        fonts(ctx);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        boolean info = times != null;
        int clock;
        switch (index) {
            case 0: clock = mihrab(c, w, h, info); break;
            case 1: clock = nightDome(c, w, h, info); break;
            case 2: clock = zellige(c, w, h, info); break;
            case 3: clock = minimal(c, w, h, info); break;
            default: clock = aurora(c, w, h); break;
        }
        if (info)
            info(c, w, times, now, infoOnTop(index), clock, index == 3 ? 206 : 200);
        return new HryFaces.Face(b, new int[] { TIME_X, TIME_Y, HryFaces.rgb565(clock), 0, 0 });
    }

    private static android.graphics.Typeface cinzel;

    /** Cinzel (SIL OFL), the face used on the Prayer Ring; falls back to serif. */
    static void fonts(android.content.Context ctx) {
        if (cinzel != null || ctx == null)
            return;
        try {
            cinzel = android.graphics.Typeface.createFromAsset(ctx.getAssets(), "fonts/hry/Cinzel.ttf");
        } catch (RuntimeException e) {
            cinzel = android.graphics.Typeface.SERIF;
        }
    }

    /**
     * Prayer Ring-style block, sized by importance:
     *   NEXT PRAYER          (small, spaced, muted)
     *   MAGHRIB  16:35       (prayer name large gold Cinzel, time bold white)
     *   TUE 3 NOV · 23 JUMADA I 1448   (small, spaced, muted)
     */
    private static void info(Canvas c, int w, long[] t, long now, boolean top, int accent, int belowY) {
        int[] rows = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        int next = -1;
        for (int r : rows)
            if (t[r] > now) { next = r; break; }
        java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH);
        String name = next >= 0 ? PrayerTimes.NAMES[next].toUpperCase(java.util.Locale.ENGLISH) : "ISHA PRAYED";
        String time = next >= 0 ? hm.format(new java.util.Date(t[next])) : "";
        String greg = new java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.ENGLISH).format(new java.util.Date(now));
        String hij = hijri(now);
        String dates = (hij.isEmpty() ? greg : greg + "  ·  " + hij).toUpperCase(java.util.Locale.ENGLISH);

        float y0 = top ? 42 : belowY;          // label baseline
        Paint lab = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        lab.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lab.setTextSize(8.5f);
        lab.setLetterSpacing(0.18f);
        lab.setColor(0xFF8FA3BF);
        lab.setTextAlign(Paint.Align.CENTER);
        c.drawText(next >= 0 ? "NEXT PRAYER" : "TONIGHT", w / 2f, y0, lab);

        Paint big = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        big.setTypeface(cinzel != null ? android.graphics.Typeface.create(cinzel, android.graphics.Typeface.BOLD)
                : android.graphics.Typeface.SERIF);
        big.setTextSize(19);
        big.setShader(HryFaces.foil(w / 2f - 70, 0, w / 2f + 70, 0));
        big.setShadowLayer(3, 0, 1, 0xB0000000);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tp.setTextSize(15);
        tp.setColor(0xFFFFFFFF);
        tp.setShadowLayer(3, 0, 1, 0xB0000000);
        float gap = time.isEmpty() ? 0 : 9;
        float wn = big.measureText(name), wt = time.isEmpty() ? 0 : tp.measureText(time);
        float x = w / 2f - (wn + gap + wt) / 2f, y1 = y0 + 22;
        c.drawText(name, x, y1, big);
        if (!time.isEmpty())
            c.drawText(time, x + wn + gap, y1, tp);

        Paint dp = new Paint(lab);
        dp.setTextSize(8.5f);
        dp.setLetterSpacing(0.10f);
        dp.setColor(0xFFC9D3E0);
        c.drawText(dates, w / 2f, y1 + 17, dp);
    }

    private static String hijri(long now) {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("en@calendar=islamic-umalqura");
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(loc);
            cal.setTimeInMillis(now);
            return new android.icu.text.SimpleDateFormat("d MMMM y", loc).format(cal.getTime());
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------ Mihrab Arch

    private static int mihrab(Canvas c, int w, int h, boolean info) {
        vertical(c, w, h, 0xFF0D4434, 0xFF041610);
        HryFaces.starPattern(c, w, h, 0x0FE3C267, 30);
        vignette(c, w, h, 0xA0000000);

        float l = 13, r = w - 13, base = h - 16, spring = 118, apex = 30;
        Path arch = new Path();
        arch.moveTo(l, base);
        arch.lineTo(l, spring);
        arch.cubicTo(l, spring - 52, w / 2f - 30, apex + 22, w / 2f, apex);
        arch.cubicTo(w / 2f + 30, apex + 22, r, spring - 52, r, spring);
        arch.lineTo(r, base);
        Paint gold = stroke(2.2f);
        gold.setShader(HryFaces.foil(0, 0, w, h));
        c.drawPath(arch, gold);
        // inner line, inset
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale((w - 34f) / (w - 26f), (base - apex - 8) / (base - apex), w / 2f, base);
        Path inner = new Path(arch);
        inner.transform(m);
        Paint thin = stroke(0.9f);
        thin.setShader(HryFaces.foil(0, 0, w, h));
        thin.setAlpha(170);
        c.drawPath(inner, thin);

        // lantern hanging from the apex, above the clock
        float lx = w / 2f;
        Paint chain = stroke(1f);
        chain.setShader(HryFaces.foil(lx - 4, apex, lx + 4, apex + 30));
        c.drawLine(lx, apex + 6, lx, apex + 26, chain);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(lx, apex + 40, 26, 0x55FFD27A, 0x00FFD27A, Shader.TileMode.CLAMP));
        c.drawCircle(lx, apex + 40, 26, glow);
        Path lantern = new Path();
        lantern.moveTo(lx, apex + 26);
        lantern.lineTo(lx + 9, apex + 34);
        lantern.lineTo(lx + 7, apex + 50);
        lantern.lineTo(lx, apex + 56);
        lantern.lineTo(lx - 7, apex + 50);
        lantern.lineTo(lx - 9, apex + 34);
        lantern.close();
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setShader(new LinearGradient(lx, apex + 26, lx, apex + 56, 0xFFFFE6A8, 0xFFC9963A, Shader.TileMode.CLAMP));
        c.drawPath(lantern, body);
        Paint edge = stroke(0.8f);
        edge.setColor(0xFF6E4E17);
        c.drawPath(lantern, edge);

        // base: a thin gold rule and three small stars
        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setShader(HryFaces.foil(l, 0, r, 0));
        c.drawRect(l + 22, base - 22, r - 22, base - 21, rule);
        if (!info)
            for (int i = -1; i <= 1; i++)
                star8(c, w / 2f + i * 22, base - 36, i == 0 ? 5 : 3.5f, rule);
        return 0xFFF6E3A1;
    }

    // ------------------------------------------------------------ Night Dome

    private static int nightDome(Canvas c, int w, int h, boolean info) {
        Paint sky = new Paint();
        sky.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0xFF050B1E, 0xFF0F2A52, 0xFF1D5662 },
                new float[] { 0f, 0.62f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, sky);
        Random rnd = new Random(42);
        Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 90; i++) {
            float x = rnd.nextFloat() * w, y = rnd.nextFloat() * 205;
            if (y > (info ? 40 : 92) && y < 196 && x > 18 && x < w - 18)
                continue;   // keep the clock (and info) area clean
            star.setColor((150 + rnd.nextInt(105)) << 24 | 0xFFFFFF);
            c.drawCircle(x, y, 0.4f + rnd.nextFloat() * 1.1f, star);
        }
        if (info)
            HryFaces.crescent(c, w / 2f, 26, 10, HryFaces.foil(w / 2f - 12, 14, w / 2f + 12, 38));
        else
            HryFaces.crescent(c, w - 48, 44, 15, HryFaces.foil(w - 66, 26, w - 30, 62));

        // skyline: central dome, two small domes, two minarets — all below the clock
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(0xFF040912);
        float g = h - 8;
        c.drawRect(0, g - 26, w, h, ink);
        RectF dome = new RectF(w / 2f - 40, g - 86, w / 2f + 40, g - 6);
        c.drawArc(dome, 180, 180, true, ink);
        c.drawRect(w / 2f - 40, g - 46, w / 2f + 40, g - 18, ink);
        c.drawArc(new RectF(34, g - 60, 82, g - 12), 180, 180, true, ink);
        c.drawArc(new RectF(w - 82, g - 60, w - 34, g - 12), 180, 180, true, ink);
        for (float mx : new float[] { 20, w - 20 }) {
            c.drawRect(mx - 5, g - 78, mx + 5, g, ink);
            c.drawRect(mx - 8, g - 64, mx + 8, g - 60, ink);
            Path tip = new Path();
            tip.moveTo(mx - 5, g - 78);
            tip.lineTo(mx, g - 92);
            tip.lineTo(mx + 5, g - 78);
            tip.close();
            c.drawPath(tip, ink);
        }
        Paint finial = new Paint(Paint.ANTI_ALIAS_FLAG);
        finial.setShader(HryFaces.foil(w / 2f - 4, g - 100, w / 2f + 4, g - 86));
        c.drawRect(w / 2f - 1, g - 98, w / 2f + 1, g - 86, finial);
        HryFaces.crescent(c, w / 2f, g - 102, 4, HryFaces.foil(w / 2f - 5, g - 107, w / 2f + 5, g - 97));
        // warm windows
        Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        lit.setColor(0xFFFFC96B);
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (float wx : new float[] { w / 2f - 22, w / 2f - 7, w / 2f + 8, w / 2f + 23, 52, 64, w - 64, w - 52 }) {
            float wy = (wx > 40 && wx < w - 40 && Math.abs(wx - w / 2f) < 30) ? g - 40 : g - 28;
            halo.setShader(new RadialGradient(wx + 2, wy + 4, 9, 0x40FFC96B, 0x00FFC96B, Shader.TileMode.CLAMP));
            c.drawCircle(wx + 2, wy + 4, 9, halo);
            c.drawRoundRect(new RectF(wx, wy, wx + 4, wy + 9), 2, 2, lit);
        }
        return 0xFFFFFFFF;
    }

    // ------------------------------------------------------------ Zellige

    private static int zellige(Canvas c, int w, int h, boolean info) {
        Paint bg = new Paint();
        bg.setShader(new RadialGradient(w / 2f, h / 2f, h * 0.75f, 0xFF12325A, 0xFF060E1F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);

        Bitmap layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        Paint line = stroke(1f);
        line.setShader(HryFaces.foil(0, 0, w, h));
        float s = 40;
        for (float y = -s / 2; y < h + s; y += s)
            for (float x = -s / 2; x < w + s; x += s) {
                star8Outline(lc, x, y, s * 0.42f, line);
                lc.drawLine(x + s * 0.42f, y, x + s - s * 0.42f, y, line);
                lc.drawLine(x, y + s * 0.42f, x, y + s - s * 0.42f, line);
            }
        // fade the tiling out where the clock sits
        Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        // Fully clear a soft-edged band where the clock sits (x 20–220, y 100–188), then
        // fade the rest gently towards the centre.
        float clearEnd = info ? TIME_Y + 160 : TIME_Y + 114;
        fade.setShader(new LinearGradient(0, TIME_Y - 26, 0, clearEnd,
                new int[] { 0x00000000, 0xFF000000, 0xFF000000, 0x00000000 }, new float[] { 0f, 0.15f, 0.85f, 1f },
                Shader.TileMode.CLAMP));
        lc.drawRect(14, TIME_Y - 26, w - 14, clearEnd, fade);
        Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
        soft.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        soft.setShader(new RadialGradient(w / 2f, TIME_Y + 44, 150, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        lc.drawRect(0, 0, w, h, soft);
        Paint put = new Paint();
        put.setAlpha(200);
        c.drawBitmap(layer, 0, 0, put);
        layer.recycle();

        Paint frame = stroke(1.6f);
        frame.setShader(HryFaces.foil(0, 0, w, h));
        c.drawRoundRect(new RectF(6, 6, w - 6, h - 6), 22, 22, frame);
        Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        mark.setShader(HryFaces.foil(w / 2f - 8, 0, w / 2f + 8, 0));
        star8(c, w / 2f, 52, 9, mark);
        if (!info)
            star8(c, w / 2f, h - 46, 7, mark);
        return 0xFFF6E3A1;
    }

    // ------------------------------------------------------------ Minimal Dark

    private static int minimal(Canvas c, int w, int h, boolean info) {
        vertical(c, w, h, 0xFF16181D, 0xFF0A0B0E);
        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF ring = new RectF(10, 10, w - 10, h - 10);
        // twelve ticks on a rounded-rectangle bezel
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            float cx = w / 2f, cy = h / 2f;
            float rx = ring.width() / 2f, ry = ring.height() / 2f;
            float x = cx + (float) Math.cos(a) * rx, y = cy + (float) Math.sin(a) * ry;
            x = Math.max(ring.left, Math.min(ring.right, x));
            y = Math.max(ring.top, Math.min(ring.bottom, y));
            boolean hour = i % 5 == 0;
            tick.setColor(hour ? (i == 0 ? 0xFFFF7A45 : 0xFFE8E8EA) : 0x55E8E8EA);
            float len = hour ? 9 : 4, wd = hour ? 2.2f : 1f;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            tick.setStrokeWidth(wd);
            tick.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x - dx * len, y - dy * len, x, y, tick);
        }
        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(0xFFFF7A45);
        if (!info)   // the prayer block sits here when shown
            c.drawRoundRect(new RectF(w / 2f - 16, TIME_Y + 96, w / 2f + 16, TIME_Y + 99), 2, 2, accent);
        return 0xFFF2F2F4;
    }

    // ------------------------------------------------------------ Aurora

    private static int aurora(Canvas c, int w, int h) {
        c.drawColor(0xFF070A18);
        blob(c, w * 0.15f, h * 0.18f, 150, 0xAA6C3BD6);
        blob(c, w * 0.95f, h * 0.35f, 140, 0x9900A6B4);
        blob(c, w * 0.30f, h * 0.95f, 160, 0xAA1E5BFF);
        blob(c, w * 0.90f, h * 0.92f, 120, 0x88D63B8F);
        // calm the centre so the time reads cleanly
        Paint calm = new Paint(Paint.ANTI_ALIAS_FLAG);
        calm.setShader(new RadialGradient(w / 2f, TIME_Y + 44, 120, 0x99070A18, 0x00070A18, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, calm);
        return 0xFFFFFFFF;
    }

    // ------------------------------------------------------------ helpers

    private static void vertical(Canvas c, int w, int h, int top, int bottom) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void vignette(Canvas c, int w, int h, int edge) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w / 2f, h / 2f, h * 0.72f, new int[] { 0x00000000, 0x00000000, edge },
                new float[] { 0f, 0.6f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void blob(Canvas c, float x, float y, float r, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    private static Paint stroke(float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(0xFFFFFFFF);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    /** Filled eight-pointed star (two overlapping squares). */
    private static void star8(Canvas c, float cx, float cy, float r, Paint p) {
        Paint fill = new Paint(p);
        fill.setStyle(Paint.Style.FILL);
        c.save();
        c.translate(cx, cy);
        for (int k = 0; k < 2; k++) {
            c.drawRect(-r * 0.72f, -r * 0.72f, r * 0.72f, r * 0.72f, fill);
            c.rotate(45);
        }
        c.restore();
    }

    private static void star8Outline(Canvas c, float cx, float cy, float r, Paint p) {
        Path path = new Path();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 - 90);
            float rr = (i % 2 == 0) ? r : r * 0.62f;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0)
                path.moveTo(x, y);
            else
                path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, p);
    }
}
