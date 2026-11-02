package com.example.dialsender.ble;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Backgrounds for HryFine custom faces, drawn at the watch's native size.
 * The watch paints the live clock itself at (timeX, timeY), so every design
 * keeps the top band ({@link #CLOCK_BAND}px) free and states where and in
 * which colour the clock goes. Fonts: Amiri and Cinzel (SIL OFL, assets/fonts/hry).
 */
public final class HryFaces {
    /** {timeX, timeY, colorRGB565, aboveWidget, belowWidget}; widget 0 = none, 1 = date. */
    public static final class Face {
        public final Bitmap image;
        public final int[] style;

        Face(Bitmap image, int[] style) {
            this.image = image;
            this.style = style;
        }
    }

    public static final String[] NAMES = { "Prayer Times", "Night Mosque", "Arabesque Gold", "Marble & Gold", "Prayer Ring" };
    public static final int RING = 4;
    static final int CLOCK_BAND = 100;
    private static final int GOLD = 0xFFE3C267;
    private static final int[] FOIL = { 0xFF7A5A1E, 0xFFC9963A, 0xFFF6E3A1, 0xFFD9AE4F, 0xFF8A6624 };
    private static final String[] ARABIC = { "الفجر", "", "الظهر",
            "العصر", "المغرب", "العشاء" };

    private static Typeface amiri, amiriBold, cinzel;

    private HryFaces() {
    }

    private static void fonts(Context c) {
        if (cinzel != null)
            return;
        try {
            amiri = Typeface.createFromAsset(c.getAssets(), "fonts/hry/Amiri-Regular.ttf");
            amiriBold = Typeface.createFromAsset(c.getAssets(), "fonts/hry/Amiri-Bold.ttf");
            cinzel = Typeface.createFromAsset(c.getAssets(), "fonts/hry/Cinzel.ttf");
        } catch (RuntimeException e) {
            amiri = amiriBold = Typeface.SERIF;
            cinzel = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
        }
    }

    public static Face render(Context c, int index, int w, int h, long[] prayerTimes, double lat, double lon) {
        switch (index) {
            case 0: return prayer(c, w, h, prayerTimes, System.currentTimeMillis(), lat, lon);
            case 1: return nightMosque(c, w, h);
            case 2: return arabesque(c, w, h);
            case RING: return prayerRing(c, w, h, prayerTimes, System.currentTimeMillis());
            default: return marble(c, w, h);
        }
    }

    public static int rgb565(int argb) {
        return ((Color.red(argb) >> 3) << 11) | ((Color.green(argb) >> 2) << 5) | (Color.blue(argb) >> 3);
    }

    static int[] clock(int color, int below) {
        return new int[] { 30, 30, rgb565(color), 0, below };
    }

    // ---------------------------------------------------------------- prayer

    public static Face prayer(Context ctx, int w, int h, long[] times, long now, double lat, double lon) {
        fonts(ctx);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        radialBg(c, w, h, w * 0.5f, h * 0.15f, 0xFF125440, 0xFF02110D);
        starPattern(c, w, h, 0x14E3C267, 34);
        vignette(c, w, h);
        crescent(c, w - 34, 36, 15, foil(w - 50, 20, w - 18, 52));
        ornament(c, w / 2f, CLOCK_BAND + 2, 80);

        Paint hijri = font(cinzel, 11.5f, 0, true);
        hijri.setShader(foil(40, 0, w - 40, 0));
        hijri.setTextAlign(Paint.Align.CENTER);
        hijri.setLetterSpacing(0.06f);
        c.drawText(hijri().toUpperCase(Locale.ENGLISH), w / 2f, CLOCK_BAND + 20, hijri);
        Paint sub = font(cinzel, 8.5f, 0xFF9DB3AA, false);
        sub.setTextAlign(Paint.Align.CENTER);
        sub.setLetterSpacing(0.08f);
        String greg = new SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(new Date(now)).toUpperCase(Locale.ENGLISH);
        c.drawText(greg + "   ·   QIBLA " + PrayerTimes.qibla(lat, lon) + "°", w / 2f, CLOCK_BAND + 33, sub);

        int[] rows = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        int next = -1;
        for (int r : rows)
            if (times[r] > now) { next = r; break; }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        float top = CLOCK_BAND + 41, rowH = (h - top - 6) / rows.length;
        for (int i = 0; i < rows.length; i++) {
            int r = rows[i];
            float y = top + i * rowH, base = y + rowH / 2 + 5;
            boolean isNext = r == next, past = times[r] <= now;
            if (isNext) {
                RectF box = new RectF(12, y + 1.5f, w - 12, y + rowH - 1.5f);
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setShader(new LinearGradient(0, y, w, y, 0x40E3C267, 0x14E3C267, Shader.TileMode.CLAMP));
                c.drawRoundRect(box, 9, 9, fill);
                Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
                edge.setStyle(Paint.Style.STROKE);
                edge.setStrokeWidth(1.1f);
                edge.setShader(foil(12, y, w - 12, y + rowH));
                c.drawRoundRect(box, 9, 9, edge);
            }
            int col = isNext ? 0xFFF6E3A1 : past ? 0xFF4F6660 : 0xFFE9F0EC;
            Paint time = font(cinzel, 14, col, true);
            c.drawText(hm.format(new Date(times[r])), 22, base, time);
            Paint en = font(cinzel, 8.5f, isNext ? GOLD : past ? 0xFF4F6660 : 0xFF9DB3AA, false);
            en.setLetterSpacing(0.1f);
            c.drawText(PrayerTimes.NAMES[r].toUpperCase(Locale.ENGLISH), 80, base - 1, en);
            Paint ar = font(isNext ? amiriBold : amiri, 16, isNext ? 0xFFF6E3A1 : past ? 0xFF4F6660 : GOLD, false);
            ar.setTextAlign(Paint.Align.RIGHT);
            c.drawText(ARABIC[r], w - 22, base + 1, ar);
        }
        return new Face(b, clock(GOLD, 0));
    }

    // ---------------------------------------------------------- night mosque

    public static Face nightMosque(Context ctx, int w, int h) {
        fonts(ctx);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint sky = new Paint();
        sky.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0xFF070A24, 0xFF18204F, 0xFF4A2E57, 0xFF8A4B45 },
                new float[] { 0f, 0.45f, 0.78f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, sky);
        Random rnd = new Random(7);
        Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 70; i++) {
            float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h * 0.62f;
            star.setColor(Color.argb(60 + rnd.nextInt(170), 255, 248, 225));
            c.drawCircle(x, y, rnd.nextFloat() < 0.12f ? 1.3f : 0.7f, star);
        }
        crescent(c, w - 42, 40, 20, foil(w - 62, 20, w - 22, 60));

        float ground = h - 34;
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w / 2f, ground, w * 0.75f, 0x88F2B66B, 0x00F2B66B, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.45f, w, h, glow);

        Paint calli = font(amiri, 22, 0, false);
        calli.setShader(foil(60, 0, w - 60, 0));
        calli.setTextAlign(Paint.Align.CENTER);
        c.drawText("سُبْحَانَ اللَّه", w / 2f, CLOCK_BAND + 34, calli);

        mosque(c, w, ground, 0xFF05060F);
        Paint floor = new Paint();
        floor.setColor(0xFF05060F);
        c.drawRect(0, ground, w, h, floor);
        return new Face(b, clock(0xFFFFF4D6, 1));
    }

    /** Central dome on a drum, two minarets, side domes, lit arched windows. */
    private static void mosque(Canvas c, int w, float ground, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float cx = w / 2f;
        c.drawRect(cx - 62, ground - 34, cx + 62, ground, p);              // hall
        c.drawRect(cx - 34, ground - 52, cx + 34, ground - 33, p);         // drum
        Path dome = new Path();
        dome.moveTo(cx - 36, ground - 51);
        dome.cubicTo(cx - 38, ground - 92, cx - 6, ground - 104, cx, ground - 112);
        dome.cubicTo(cx + 6, ground - 104, cx + 38, ground - 92, cx + 36, ground - 51);
        dome.close();
        c.drawPath(dome, p);
        c.drawRect(cx - 1, ground - 124, cx + 1, ground - 110, p);         // finial
        Paint fin = new Paint(Paint.ANTI_ALIAS_FLAG);
        fin.setColor(color);
        crescentSolid(c, cx, ground - 127, 4, fin);
        for (int s = -1; s <= 1; s += 2) {
            float sx = cx + s * 52;
            c.drawArc(new RectF(sx - 16, ground - 52, sx + 16, ground - 22), 180, 180, true, p); // side dome
            float mx = cx + s * 86;
            c.drawRect(mx - 6, ground - 118, mx + 6, ground, p);           // minaret shaft
            c.drawRect(mx - 9, ground - 92, mx + 9, ground - 88, p);       // balcony
            Path cap = new Path();
            cap.moveTo(mx - 7, ground - 118);
            cap.lineTo(mx, ground - 140);
            cap.lineTo(mx + 7, ground - 118);
            cap.close();
            c.drawPath(cap, p);
        }
        Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        lit.setColor(0xCCF2C46B);
        for (int i = -2; i <= 2; i++) {
            float x = cx + i * 20;
            Path arch = new Path();
            arch.moveTo(x - 4, ground - 4);
            arch.lineTo(x - 4, ground - 18);
            arch.quadTo(x, ground - 26, x + 4, ground - 18);
            arch.lineTo(x + 4, ground - 4);
            arch.close();
            c.drawPath(arch, lit);
        }
    }

    // ------------------------------------------------------------- arabesque

    public static Face arabesque(Context ctx, int w, int h) {
        fonts(ctx);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        radialBg(c, w, h, w / 2f, h * 0.6f, 0xFF10303A, 0xFF02070A);
        starPattern(c, w, h, 0x12E3C267, 30);
        float cx = w / 2f, cy = CLOCK_BAND + 70;
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setShader(new RadialGradient(cx, cy, 95, 0x50E3C267, 0x00E3C267, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 95, halo);
        Shader f = foil(cx - 80, cy - 80, cx + 80, cy + 80);
        for (int k = 0; k < 4; k++)
            rosette(c, cx, cy, 74 - k * 17, f, 1.7f - k * 0.3f, k * 11.25f);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setShader(f);
        c.drawCircle(cx, cy, 4, dot);
        Paint calli = font(amiri, 19, 0, false);
        calli.setShader(foil(50, 0, w - 50, 0));
        calli.setTextAlign(Paint.Align.CENTER);
        c.drawText("بِسْمِ اللَّهِ", cx, h - 16, calli);
        frame(c, w, h);
        return new Face(b, clock(0xFFFFFFFF, 1));
    }

    // --------------------------------------------------------- marble & gold

    public static Face marble(Context ctx, int w, int h) {
        fonts(ctx);
        int[] px = new int[w * h];
        Random rnd = new Random(42);
        float[] grid = new float[33 * 33];
        for (int i = 0; i < grid.length; i++)
            grid[i] = rnd.nextFloat();
        // Classic procedural marble: a diagonal sine warped by turbulence, sharpened
        // with a high power so veins are thin streaks, over soft low-frequency clouding.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float turb = 0, amp = 1, freq = 1f / 150;
                for (int o = 0; o < 3; o++) {
                    turb += amp * noise(grid, x * freq + o * 5, y * freq + o * 3);
                    amp *= 0.45f;
                    freq *= 2;
                }
                float cloud = noise(grid, x / 160f + 7, y / 160f + 3);
                double grey = Math.pow(1 - Math.abs(Math.sin(Math.PI * (x * 0.004 + y * 0.008 + turb * 2.2))), 22);
                double soft = Math.pow(1 - Math.abs(Math.sin(Math.PI * (x * 0.004 + y * 0.008 + turb * 2.2))), 4);
                double gold = Math.pow(1 - Math.abs(Math.sin(Math.PI * (x * 0.003 - y * 0.006 + turb * 1.4 + 0.3))), 70);
                double base = 243 - cloud * 12;
                double r = base, g = base - 1.5, bl = base - 5;
                double d = grey * 85 + soft * 14;
                r -= d; g -= d; bl -= d * 0.92;
                r += (212 - r) * gold; g += (168 - g) * gold; bl += (82 - bl) * gold;
                px[y * w + x] = Color.rgb(clamp((int) r), clamp((int) g), clamp((int) bl));
            }
        }
        Bitmap b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setShader(foil(20, 0, w - 20, 0));
        line.setStrokeWidth(1.4f);
        c.drawLine(28, CLOCK_BAND + 6, w - 28, CLOCK_BAND + 6, line);
        Paint brand = font(cinzel, 10, 0xFF3A3328, false);
        brand.setTextAlign(Paint.Align.CENTER);
        brand.setLetterSpacing(0.35f);
        c.drawText("MARBLE  ·  OR", w / 2f, h - 20, brand);
        frameColor(c, w, h, foil(0, 0, w, h));
        return new Face(b, clock(0xFF141414, 1));
    }

    // ------------------------------------------------------- mosque photo + prayer

    /** One bundled photo; all come from Wikimedia Commons with the licence recorded. */
    public static final class Photo {
        public final String file, label, title, author, license, url, group;

        Photo(org.json.JSONObject o) {
            file = o.optString("file");
            label = o.optString("label");
            title = o.optString("title");
            author = o.optString("author");
            license = o.optString("license");
            url = o.optString("url");
            group = file.startsWith("mosque") || file.startsWith("icon") ? "Mosques"
                    : file.startsWith("art") ? "Islamic art" : "Premium";
        }

        /** Islamic photos carry the prayer bar; the rest are plain premium faces. */
        public boolean prayer() {
            return !"Premium".equals(group);
        }
    }

    private static java.util.List<Photo> catalog;

    /** assets/hryfaces/photos/credits.json, in file order. */
    public static synchronized java.util.List<Photo> photos(Context ctx) {
        if (catalog != null)
            return catalog;
        java.util.List<Photo> list = new java.util.ArrayList<>();
        try (java.io.InputStream in = ctx.getAssets().open("hryfaces/photos/credits.json")) {
            byte[] buf = new byte[in.available()];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            org.json.JSONArray a = new org.json.JSONArray(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++)
                list.add(new Photo(a.getJSONObject(i)));
        } catch (Exception e) {
            // No catalogue: the photo sections simply do not appear.
        }
        catalog = list;
        return list;
    }

    public static Photo photoByFile(Context ctx, String file) {
        for (Photo p : photos(ctx))
            if (p.file.equals(file))
                return p;
        return null;
    }

    public static Bitmap loadPhoto(Context ctx, Photo p) {
        try (java.io.InputStream in = ctx.getAssets().open("hryfaces/photos/" + p.file)) {
            return android.graphics.BitmapFactory.decodeStream(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * Full-bleed mosque photo; the top is shaded for the watch's clock with the
     * Hijri date beneath it, and a dark glass bar at the bottom carries the next
     * prayer plus all five in Arabic, the coming one lit in gold.
     */
    public static Face prayerOnPhoto(Context ctx, Bitmap src, int w, int h, long[] times, long now) {
        fonts(ctx);
        Bitmap b = photo(src, w, h).image;
        Canvas c = new Canvas(b);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.55f, w, h, shade);

        Paint hijri = font(cinzel, 9.5f, 0, true);
        hijri.setShader(foil(30, 0, 200, 0));
        hijri.setShadowLayer(3, 0, 1, 0xCC000000);
        hijri.setLetterSpacing(0.05f);
        String hijriText = hijri().toUpperCase(Locale.ENGLISH);
        Paint pillBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillBg.setColor(0x99000000);
        c.drawRoundRect(new RectF(24, CLOCK_BAND - 2, 36 + hijri.measureText(hijriText), CLOCK_BAND + 15), 8, 8, pillBg);
        c.drawText(hijriText, 30, CLOCK_BAND + 10, hijri);

        int[] rows = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        int next = -1;
        for (int r : rows)
            if (times[r] > now) { next = r; break; }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

        RectF bar = new RectF(8, h - 84, w - 8, h - 8);
        Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
        glass.setColor(0xB8060A0C);
        c.drawRoundRect(bar, 16, 16, glass);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f);
        edge.setShader(foil(bar.left, bar.top, bar.right, bar.bottom));
        c.drawRoundRect(bar, 16, 16, edge);

        Paint head = font(cinzel, 11, 0, true);
        head.setShader(foil(40, 0, w - 40, 0));
        head.setTextAlign(Paint.Align.CENTER);
        head.setLetterSpacing(0.08f);
        String nextText = next >= 0 ? "NEXT  ·  " + PrayerTimes.NAMES[next].toUpperCase(Locale.ENGLISH)
                + "  " + hm.format(new Date(times[next])) : "ISHA PRAYED  ·  GOOD NIGHT";
        c.drawText(nextText, w / 2f, bar.top + 18, head);

        float colW = (bar.width() - 8) / rows.length;
        for (int i = 0; i < rows.length; i++) {
            int r = rows[i];
            float cx = bar.left + 4 + colW * (i + 0.5f);
            boolean isNext = r == next, past = times[r] <= now;
            if (isNext) {
                Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
                pill.setColor(0x33E3C267);
                c.drawRoundRect(new RectF(cx - colW / 2 + 2, bar.top + 25, cx + colW / 2 - 2, bar.bottom - 5), 10, 10, pill);
            }
            int col = isNext ? 0xFFF6E3A1 : past ? 0xFF7C8580 : 0xFFF2F2EE;
            Paint ar = font(isNext ? amiriBold : amiri, 13, col, false);
            ar.setTextAlign(Paint.Align.CENTER);
            c.drawText(ARABIC[r], cx, bar.top + 45, ar);
            Paint t = font(cinzel, 9.5f, col, isNext);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(hm.format(new Date(times[r])), cx, bar.top + 63, t);
        }
        return new Face(b, clock(0xFFFFFFFF, 0));
    }

    /**
     * 24-hour prayer ring (an original drawing; the idea of prayer times around a
     * 24h dial is common to many faces). Midnight at the bottom, noon at the top.
     * Daylight (sunrise→maghrib) is a warm arc, night a deep one; each prayer is a
     * marker on the ring with its name, the coming one in gold. The centre holds
     * the next prayer and the Hijri date. The watch draws the digital clock above.
     * There is no moving "now" hand: the face is repainted at every prayer.
     */
    public static Face prayerRing(Context ctx, int w, int h, long[] times, long now) {
        fonts(ctx);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        radialBg(c, w, h, w * 0.5f, h * 0.62f, 0xFF10263A, 0xFF03080F);
        starPattern(c, w, h, 0x0EE3C267, 30);

        float cx = w / 2f, cy = CLOCK_BAND + (h - CLOCK_BAND) / 2f + 2;
        float r = Math.min(w / 2f - 22, (h - CLOCK_BAND) / 2f - 16);
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        long midnight = day.getTimeInMillis();
        java.util.function.LongFunction<Float> ang = t -> 90f + (t - midnight) / 3_600_000f * 15f;

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(9);
        ring.setColor(0xFF1C3350);                       // night
        c.drawOval(oval, ring);
        float a0 = ang.apply(times[PrayerTimes.SUNRISE]), a1 = ang.apply(times[PrayerTimes.MAGHRIB]);
        ring.setShader(new android.graphics.SweepGradient(cx, cy,
                new int[] { 0xFFB8862E, 0xFFE9C46A, 0xFFB8862E }, null));
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRotate(a0, cx, cy);
        ring.getShader().setLocalMatrix(m);
        c.drawArc(oval, a0, a1 - a0, false, ring);       // daylight

        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setColor(0x66FFFFFF);
        Paint hourTxt = font(Typeface.DEFAULT_BOLD, 8, 0x99FFFFFF, false);
        hourTxt.setTextAlign(Paint.Align.CENTER);
        for (int hr = 0; hr < 24; hr++) {
            double a = Math.toRadians(90 + hr * 15);
            float in = r - 14, out = r - (hr % 6 == 0 ? 20 : 17);
            tick.setStrokeWidth(hr % 6 == 0 ? 1.6f : 1f);
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, tick);
            if (hr % 6 == 0) {
                float tr = r - 28;
                c.drawText(String.valueOf(hr), cx + (float) Math.cos(a) * tr, cy + (float) Math.sin(a) * tr + 3, hourTxt);
            }
        }

        int[] rows = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        int next = -1;
        for (int p : rows)
            if (times[p] > now) { next = p; break; }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        for (int p : rows) {
            double a = Math.toRadians(ang.apply(times[p]));
            float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
            boolean isNext = p == next;
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(0xFF03080F);
            c.drawCircle(px, py, isNext ? 7 : 5.5f, dot);
            dot.setColor(isNext ? 0xFFF6E3A1 : 0xFFFFFFFF);
            c.drawCircle(px, py, isNext ? 5 : 3.5f, dot);
            float lr = r + 13;
            float lx = cx + (float) Math.cos(a) * lr, ly = cy + (float) Math.sin(a) * lr;
            Paint name = font(Typeface.DEFAULT_BOLD, 8.5f, isNext ? 0xFFF6E3A1 : 0xDDFFFFFF, false);
            name.setTextAlign(Math.cos(a) > 0.3 ? Paint.Align.LEFT : Math.cos(a) < -0.3 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            name.setShadowLayer(2, 0, 1, 0xFF000000);
            float nx = Math.max(2, Math.min(w - 2, lx - (float) Math.cos(a) * 8));
            c.drawText(PrayerTimes.NAMES[p].toUpperCase(Locale.ENGLISH), nx, ly + 3, name);
        }

        Paint lab = font(Typeface.DEFAULT_BOLD, 8.5f, 0xFF8FA3BF, false);
        lab.setTextAlign(Paint.Align.CENTER);
        lab.setLetterSpacing(0.15f);
        c.drawText(next >= 0 ? "NEXT" : "TONIGHT", cx, cy - 16, lab);
        Paint big = font(cinzel, 17, 0, true);
        big.setShader(foil(cx - 50, 0, cx + 50, 0));
        big.setTextAlign(Paint.Align.CENTER);
        c.drawText(next >= 0 ? PrayerTimes.NAMES[next].toUpperCase(Locale.ENGLISH) : "ALL PRAYED", cx, cy + 3, big);
        Paint t = font(Typeface.DEFAULT_BOLD, 13, 0xFFFFFFFF, false);
        t.setTextAlign(Paint.Align.CENTER);
        if (next >= 0)
            c.drawText(hm.format(new Date(times[next])), cx, cy + 20, t);
        c.drawText(hijriShort(), cx, cy + 34, lab);
        return new Face(b, clock(0xFFFFFFFF, 1));
    }

    /**
     * "Clean" prayer layout, inspired by the open-source PrayerWatchFace for Wear OS
     * (github.com/3llomi/PrayerWatchFace, Apache-2.0, by AbdulAlim Rajjoub): date and
     * Hijri date under the clock, and a light rounded panel with the next prayer.
     * Redrawn from scratch for this watch (no code or images copied). The live
     * countdown is left out: the watch cannot tick it, and Fogg repaints the face
     * at each prayer instead.
     */
    public static Face prayerClean(Context ctx, Bitmap src, int w, int h, long[] times, long now) {
        fonts(ctx);
        Bitmap b = photo(src, w, h).image;
        Canvas c = new Canvas(b);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.5f, w, h, shade);

        // Date row under the watch's clock: weekday + date left, Hijri right.
        Paint date = font(Typeface.DEFAULT_BOLD, 12, 0xFFFFFFFF, false);
        date.setShadowLayer(3, 0, 1, 0xCC000000);
        String greg = new SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(new Date(now)).toUpperCase(Locale.ENGLISH);
        c.drawText(greg, 30, CLOCK_BAND + 12, date);
        Paint hij = font(Typeface.DEFAULT_BOLD, 12, 0xFFD3DDF8, false);
        hij.setShadowLayer(3, 0, 1, 0xCC000000);
        hij.setTextAlign(Paint.Align.RIGHT);
        c.drawText(hijriShort(), w - 22, CLOCK_BAND + 12, hij);

        int[] rows = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        int next = -1;
        for (int r : rows)
            if (times[r] > now) { next = r; break; }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

        // Light panel: "NEXT" + prayer name on the left, its time large on the right.
        final int ink = 0xFF1B2440, soft = 0xFF55607E;
        RectF panel = new RectF(14, h - 92, w - 14, h - 40);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xF0D3DDF8);
        c.drawRoundRect(panel, 18, 18, bg);
        Paint div = new Paint();
        div.setColor(0x401B2440);
        float mid = panel.centerX() - 6;
        c.drawRect(mid, panel.top + 10, mid + 1, panel.bottom - 10, div);
        Paint lab = font(Typeface.DEFAULT_BOLD, 9, soft, false);
        lab.setLetterSpacing(0.12f);
        lab.setTextAlign(Paint.Align.CENTER);
        float lx = (panel.left + mid) / 2f, rx = (mid + panel.right) / 2f;
        c.drawText(next >= 0 ? "NEXT" : "TONIGHT", lx, panel.top + 20, lab);
        Paint name = font(Typeface.DEFAULT_BOLD, 17, ink, false);
        name.setTextAlign(Paint.Align.CENTER);
        c.drawText(next >= 0 ? PrayerTimes.NAMES[next].toUpperCase(Locale.ENGLISH) : "ALL PRAYED", lx, panel.top + 42, name);
        c.drawText("TIME", rx, panel.top + 20, lab);
        Paint big = font(Typeface.DEFAULT_BOLD, 20, ink, false);
        big.setTextAlign(Paint.Align.CENTER);
        c.drawText(next >= 0 ? hm.format(new Date(times[next])) : "—", rx, panel.top + 43, big);

        // All five times along the bottom edge, the coming one highlighted.
        float colW = (w - 28) / (float) rows.length;
        for (int i = 0; i < rows.length; i++) {
            int r = rows[i];
            boolean isNext = r == next, past = times[r] <= now;
            Paint t = font(Typeface.DEFAULT_BOLD, 9, isNext ? 0xFFD3DDF8 : past ? 0x99FFFFFF : 0xFFFFFFFF, false);
            t.setTextAlign(Paint.Align.CENTER);
            t.setShadowLayer(2, 0, 1, 0xCC000000);
            float cx = 14 + colW * (i + 0.5f);
            c.drawText(PrayerTimes.NAMES[r].substring(0, Math.min(4, PrayerTimes.NAMES[r].length())).toUpperCase(Locale.ENGLISH)
                    + " " + hm.format(new Date(times[r])), cx, h - 20, t);
        }
        return new Face(b, clock(0xFFFFFFFF, 0));
    }

    // ----------------------------------------------------------------- photo

    /** Centre-crops a photo to the screen and shades the clock band so the time stays readable. */
    public static Face photo(Bitmap src, int w, int h) {
        Bitmap b = HryImage.sharpen(HryImage.cover(src, w, h), 0.45f);
        Canvas c = new Canvas(b);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, 0, 0, CLOCK_BAND + 30, 0xB0000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, CLOCK_BAND + 30, shade);
        return new Face(b, clock(0xFFFFFFFF, 1));
    }

    // --------------------------------------------------------------- helpers

    private static String hijri() {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("en@calendar=islamic-umalqura");
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(loc);
            return new android.icu.text.SimpleDateFormat("d MMMM y", loc).format(cal.getTime()) + " AH";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** e.g. "11 JUM. I" — Umm al-Qura, short month. */
    private static String hijriShort() {
        try {
            android.icu.util.ULocale loc = new android.icu.util.ULocale("en@calendar=islamic-umalqura");
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(loc);
            return new android.icu.text.SimpleDateFormat("d MMM", loc).format(cal.getTime()).toUpperCase(Locale.ENGLISH);
        } catch (RuntimeException e) {
            return "";
        }
    }

    static Shader foil(float x0, float y0, float x1, float y1) {
        return new LinearGradient(x0, y0, x1, y1, FOIL, new float[] { 0f, 0.3f, 0.5f, 0.72f, 1f }, Shader.TileMode.MIRROR);
    }

    private static Paint font(Typeface tf, float size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(bold ? Typeface.create(tf, Typeface.BOLD) : tf);
        p.setTextSize(size);
        // A shader is modulated by the paint's alpha; colour 0 would make foil text invisible.
        p.setColor(color == 0 ? 0xFFFFFFFF : color);
        return p;
    }

    static void radialBg(Canvas c, int w, int h, float cx, float cy, int inner, int outer) {
        Paint p = new Paint();
        p.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.95f, inner, outer, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void vignette(Canvas c, int w, int h) {
        Paint p = new Paint();
        p.setShader(new RadialGradient(w / 2f, h / 2f, h * 0.75f, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    /** Tiled eight-point stars (two overlaid squares), the classic girih motif. */
    static void starPattern(Canvas c, int w, int h, int color, int step) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.9f);
        p.setColor(color);
        for (int y = 0; y <= h + step; y += step)
            for (int x = ((y / step) % 2) * step / 2; x <= w + step; x += step)
                star(c, x, y, step * 0.36f, p);
    }

    private static void star(Canvas c, float cx, float cy, float r, Paint p) {
        Path path = new Path();
        for (int k = 0; k < 2; k++) {
            double a0 = Math.PI / 4 * k;
            for (int i = 0; i < 4; i++) {
                double a = a0 + Math.PI / 2 * i;
                float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
        }
        c.drawPath(path, p);
    }

    /** Sixteen-point rosette with an inner ring, rotated by {@code rot} degrees. */
    private static void rosette(Canvas c, float cx, float cy, float r, Shader shader, float stroke, float rot) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setShader(shader);
        c.save();
        c.rotate(rot, cx, cy);
        star(c, cx, cy, r, p);
        c.rotate(22.5f, cx, cy);
        star(c, cx, cy, r, p);
        c.restore();
        c.drawCircle(cx, cy, r * 0.42f, p);
    }

    static void crescent(Canvas c, float cx, float cy, float r, Shader shader) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(shader);
        crescentSolid(c, cx, cy, r, p);
        star(c, cx + r * 0.62f, cy - r * 0.18f, r * 0.26f, p);
    }

    private static void crescentSolid(Canvas c, float cx, float cy, float r, Paint p) {
        Path moon = new Path();
        moon.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + r * 0.45f, cy - r * 0.2f, r * 0.85f, Path.Direction.CW);
        moon.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(moon, p);
    }

    /** Hairline with a small diamond at its centre. */
    private static void ornament(Canvas c, float cx, float y, float half) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(foil(cx - half, y, cx + half, y));
        p.setStrokeWidth(0.9f);
        c.drawLine(cx - half, y, cx - 7, y, p);
        c.drawLine(cx + 7, y, cx + half, y, p);
        Path d = new Path();
        d.moveTo(cx, y - 3.5f);
        d.lineTo(cx + 4, y);
        d.lineTo(cx, y + 3.5f);
        d.lineTo(cx - 4, y);
        d.close();
        c.drawPath(d, p);
    }

    private static void frame(Canvas c, int w, int h) {
        frameColor(c, w, h, foil(0, 0, w, h));
    }

    private static void frameColor(Canvas c, int w, int h, Shader s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f);
        p.setShader(s);
        c.drawRoundRect(new RectF(5, 5, w - 5, h - 5), 20, 20, p);
        p.setStrokeWidth(0.6f);
        c.drawRoundRect(new RectF(9, 9, w - 9, h - 9), 17, 17, p);
    }

    /** Smoothed value noise on a 32x32 lattice. */
    private static float noise(float[] g, float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
        float a = g[(yi & 31) * 33 + (xi & 31)], b = g[(yi & 31) * 33 + ((xi + 1) & 31)];
        float c = g[((yi + 1) & 31) * 33 + (xi & 31)], d = g[((yi + 1) & 31) * 33 + ((xi + 1) & 31)];
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
