package com.example.dialsender.ble;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Quranic reminders sent to the watch as ordinary text messages — the one
 * channel this watch handles reliably. Verses come from
 * assets/quran_reminders.tsv. Every entry was chosen so that "Quran s:a:" plus
 * the text fits one 196-byte HryFine message, so nothing is ever cut off
 * (the Tanzil Arabic text must be shown verbatim).
 */
public final class QuranReminders {
    public static final String PREF_AFTER_PRAYER = "quran_after_prayer";
    /** 0 = Arabic then English, 1 = English only, 2 = Arabic only. */
    public static final String PREF_LANG = "quran_lang";
    public static final String[] LANG_NAMES = { "Arabic + English", "English only", "Arabic only" };
    /** English by default; Arabic works on the H19 once converted (ArabicVisual). */
    public static final int DEFAULT_LANG = 1;
    private static final String PREF_INDEX = "quran_index";
    private static final String PKG = "com.example.dialsender.quran";

    public static final class Verse {
        public final String ref, arabic, english;

        Verse(String ref, String arabic, String english) {
            this.ref = ref;
            this.arabic = arabic;
            this.english = english;
        }
    }

    private static List<Verse> cache;

    private QuranReminders() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dial_sender_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized List<Verse> all(Context c) {
        if (cache != null)
            return cache;
        List<Verse> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("quran_reminders.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null;) {
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                String[] f = line.split("\t", 3);
                if (f.length == 3)
                    out.add(new Verse(f[0], f[1], f[2]));
            }
        } catch (Exception ignored) {
        }
        cache = out;
        return out;
    }

    /** The next verse in rotation (no repeats until the whole list has been shown). */
    public static Verse next(Context c) {
        List<Verse> v = all(c);
        if (v.isEmpty())
            return null;
        SharedPreferences p = prefs(c);
        int i = Math.floorMod(p.getInt(PREF_INDEX, 0), v.size());
        p.edit().putInt(PREF_INDEX, i + 1).apply();
        return v.get(i);
    }

    /**
     * Send the next verse after {@code delayMs}. Arabic and English go as two
     * messages a few seconds apart so each one fits the watch whole.
     * Returns false if the watch is not connected.
     */
    public static boolean send(Context context, long delayMs) {
        Context c = context.getApplicationContext();
        BleManager ble = BleManager.getInstance(c);
        if (!ble.isSessionReady())
            return false;
        Verse v = next(c);
        if (v == null)
            return false;
        int lang = prefs(c).getInt(PREF_LANG, DEFAULT_LANG);
        // The watch treats the first colon as the end of the title, so "6:162" would be cut to "6".
        String title = "Quran " + v.ref.replace(':', '.');
        Handler h = new Handler(Looper.getMainLooper());
        long t = delayMs;
        if (lang != 1) {
            // Joined letters take 3 bytes each, so a long verse goes as whole-word parts.
            java.util.List<String> parts = ArabicVisual.split(v.arabic, 196 - (title + " (1/2):").length() - 1);
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                String head = parts.size() == 1 ? title : title + " (" + (i + 1) + "/" + parts.size() + ")";
                h.postDelayed(() -> ble.sendNotification(127, head, part, PKG), t);
                t += 5000;
            }
        }
        if (lang != 2)
            h.postDelayed(() -> ble.sendNotification(127, title, v.english, PKG), t);
        return true;
    }

    /** How long {@link #send} keeps the link busy, so a face transfer can wait for it. */
    public static long busyMs(Context c) {
        return prefs(c).getInt(PREF_LANG, DEFAULT_LANG) == 1 ? 3000 : 14000;
    }
}
