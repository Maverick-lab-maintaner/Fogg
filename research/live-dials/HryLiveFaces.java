package com.example.dialsender.ble;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

import static com.example.dialsender.ble.HryDialCompiler.*;

/** Live (watch-rendered) dials compiled to HryFine WF files. */
public final class HryLiveFaces {
    private static final int[] FOIL = { 0xFF8A6624, 0xFFC9963A, 0xFFF6E3A1, 0xFFD9AE4F, 0xFF8A6624 };
    private static final String[] WEEK = { "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT" };

    private HryLiveFaces() {
    }

    /** First live dial: big gold digital time, date, steps, heart rate, battery. */
    public static byte[] liveGold(Context ctx, int w, int h, Bitmap backgroundOrNull) {
        Typeface cinzel = Typeface.createFromAsset(ctx.getAssets(), "fonts/hry/Cinzel.ttf");
        Typeface bold = Typeface.create(cinzel, Typeface.BOLD);

        // Background with the static parts (colon, icons, rules) painted in.
        Bitmap bg = backgroundOrNull != null ? HryImage.cover(backgroundOrNull, w, h)
                : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bg);
        if (backgroundOrNull == null) {
            Paint p = new Paint();
            p.setShader(new RadialGradient(w / 2f, h * 0.3f, h, 0xFF0E4A3A, 0xFF010907, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        } else {
            Paint shade = new Paint();
            shade.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0xC0000000, 0x40000000, 0xD0000000 },
                    new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, shade);
        }
        Paint colon = new Paint(Paint.ANTI_ALIAS_FLAG);
        colon.setShader(foil(0, 60, 0, 100));
        c.drawCircle(w / 2f, 72, 3.5f, colon);
        c.drawCircle(w / 2f, 94, 3.5f, colon);
        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setShader(foil(30, 0, w - 30, 0));
        rule.setStrokeWidth(1f);
        c.drawLine(36, 128, w - 36, 128, rule);
        c.drawLine(36, 214, w - 36, 214, rule);
        int statY = 240;
        heartIcon(c, 40, statY - 16, foil(30, 0, 52, 0));
        stepsIcon(c, w / 2f - 6, statY - 22, foil(0, 0, w, 0));
        batteryIcon(c, w - 52, statY - 18, foil(w - 60, 0, w - 30, 0));

        List<Element> els = new ArrayList<>();
        els.add(new Element(T_IMAGE, K_IMAGE, 1, w / 2, h / 2, 0, 0, 0, 0, opaque(bg)));

        byte[] big = strip(digits(), bold, 50, 34, 62, true);
        els.add(new Element(T_NUMBER, K_HOUR, 10, 74, 83, ALIGN_CENTER, 0, 0, 0, big));
        els.add(new Element(T_NUMBER, K_MINUTE, 10, w - 74, 83, ALIGN_CENTER, 0, 0, 0, big));

        String[] week14 = new String[14];
        for (int i = 0; i < 14; i++) week14[i] = WEEK[i % 7];
        els.add(new Element(T_NUMBER, K_WEEKDAY, 14, w / 2, 152, ALIGN_CENTER, 0, 0, 0,
                strip(week14, cinzel, 15, 54, 20, true)));
        byte[] mid = strip(digits(), cinzel, 18, 13, 22, false);
        els.add(new Element(T_NUMBER, K_DAY, 10, w / 2 - 20, 182, ALIGN_CENTER, 0, 0, 0, mid));
        els.add(new Element(T_NUMBER, K_MONTH, 10, w / 2 + 20, 182, ALIGN_CENTER, 0, 0, 0, mid));

        byte[] small = strip(digits(), cinzel, 14, 10, 18, false);
        els.add(new Element(T_NUMBER, K_HEART, 10, 46, statY + 12, ALIGN_CENTER, 0, 0, 0, small));
        els.add(new Element(T_NUMBER, K_STEPS, 10, w / 2, statY + 12, ALIGN_CENTER, 0, 0, 0, small));
        String[] batt = new String[11];
        System.arraycopy(digits(), 0, batt, 0, 10);
        batt[10] = "-";
        els.add(new Element(T_NUMBER, K_BATTERY, 11, w - 46, statY + 12, ALIGN_CENTER, 0, 0, 0,
                strip(batt, cinzel, 14, 10, 18, false)));

        Bitmap pv = HryImage.cover(bg, 138, 163);
        byte[] code = { 0, 0, 1, 1, 0, 0, (byte) 0xF0, 0x0F };
        return compile(code, w, h, els, opaque(pv));
    }

    // ------------------------------------------------------------------ art

    private static String[] digits() {
        String[] d = new String[10];
        for (int i = 0; i < 10; i++) d[i] = String.valueOf(i);
        return d;
    }

    /** Vertical strip of glyph frames on a transparent background. */
    private static byte[] strip(String[] frames, Typeface tf, float size, int fw, int fh, boolean gold) {
        Bitmap b = Bitmap.createBitmap(fw, fh * frames.length, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF2EEE4);
        Paint.FontMetrics fm = p.getFontMetrics();
        float baseline = fh / 2f - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < frames.length; i++) {
            if (gold)
                p.setShader(new LinearGradient(0, i * fh, 0, (i + 1) * fh, FOIL, null, Shader.TileMode.CLAMP));
            c.drawText(frames[i], fw / 2f, i * fh + baseline, p);
        }
        return alpha(b, MATTE);
    }

    /** Approximate background colour behind the digits, for blending their soft edges. */
    private static final int MATTE = 0xFF082C22;

    private static Shader foil(float x0, float y0, float x1, float y1) {
        return new LinearGradient(x0, y0, x1, y1, FOIL, null, Shader.TileMode.MIRROR);
    }

    private static void heartIcon(Canvas c, float x, float y, Shader s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(s);
        Path h = new Path();
        h.moveTo(x + 6, y + 11);
        h.cubicTo(x - 2, y + 5, x + 1, y - 1, x + 6, y + 3);
        h.cubicTo(x + 11, y - 1, x + 14, y + 5, x + 6, y + 11);
        c.drawPath(h, p);
    }

    private static void stepsIcon(Canvas c, float x, float y, Shader s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(s);
        c.drawOval(x, y + 4, x + 5, y + 12, p);
        c.drawOval(x + 7, y, x + 12, y + 8, p);
    }

    private static void batteryIcon(Canvas c, float x, float y, Shader s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        c.drawRoundRect(x, y, x + 16, y + 9, 2, 2, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x + 16.5f, y + 3, x + 18, y + 6, p);
        c.drawRect(x + 2.5f, y + 2.5f, x + 11, y + 6.5f, p);
    }
}
