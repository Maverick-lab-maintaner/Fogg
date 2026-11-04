package com.example.dialsender.ble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * One short line a day for faces with empty space, Arabic above English.
 * Quran lines are whole verses taken verbatim from assets/quran_reminders.tsv
 * (never typed here, never cut). The one maxim carries no attribution because
 * its source could not be verified.
 */
final class DailyWisdom {
    private DailyWisdom() {
    }

    /** Short whole verses that fit one line. */
    private static final String[] REFS = { "94:5", "55:60", "87:14", "94:8", "20:26", "1:6", "1:2" };
    private static final String MAXIM_AR = "إياك والتسويف";
    private static final String MAXIM_EN = "Beware of \"I will, later\"";

    private static Typeface amiri;

    private static List<String[]> lines(Context c) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] { MAXIM_AR, MAXIM_EN });
        for (String ref : REFS)
            for (QuranReminders.Verse v : QuranReminders.all(c))
                if (ref.equals(v.ref)) {
                    String en = v.english.replaceAll("[,;:]+$", "");
                    out.add(new String[] { v.arabic, en + "  (" + v.ref + ")" });
                }
        return out;
    }

    /** Today's line on a dark gold-edged plate, for faces with a busy background. */
    static void plate(Context c, Canvas canvas, int w, float top, long now) {
        android.graphics.RectF r = new android.graphics.RectF(16, top, w - 16, top + 50);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xD8061518);
        canvas.drawRoundRect(r, 14, 14, fill);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f);
        edge.setShader(HryFaces.foil(r.left, 0, r.right, 0));
        canvas.drawRoundRect(r, 14, 14, edge);
        draw(c, canvas, w, top + 22, now);
    }

    /** Draws today's line centred, Arabic baseline at {@code y}. It changes at midnight with the face. */
    static void draw(Context c, Canvas canvas, int w, float y, long now) {
        if (amiri == null) {
            try {
                amiri = Typeface.createFromAsset(c.getAssets(), "fonts/hry/Amiri-Bold.ttf");
            } catch (RuntimeException e) {
                amiri = Typeface.SERIF;
            }
        }
        List<String[]> all = lines(c);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        String[] line = all.get(cal.get(Calendar.DAY_OF_YEAR) % all.size());

        Paint ar = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        ar.setTypeface(amiri);
        ar.setTextSize(18);
        ar.setColor(0xFFF0D48A);
        ar.setTextAlign(Paint.Align.CENTER);
        ar.setShadowLayer(3, 0, 1, 0xB0000000);
        canvas.drawText(line[0], w / 2f, y, ar);

        Paint en = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        en.setTextSize(9.5f);
        en.setColor(0xFFDDE5EE);
        en.setTextAlign(Paint.Align.CENTER);
        en.setShadowLayer(2, 0, 1, 0xB0000000);
        while (en.measureText(line[1]) > w - 30 && en.getTextSize() > 7.5f)
            en.setTextSize(en.getTextSize() - 0.5f);
        canvas.drawText(line[1], w / 2f, y + 15, en);
    }
}
