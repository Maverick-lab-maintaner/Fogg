package com.example.dialsender.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Drives the prayer features: an exact alarm at the next prayer (or just after
 * midnight) that buzzes the watch with an alert and repaints the prayer face so
 * the highlighted "next prayer" moves on. Location is cached in prefs so alarms
 * never wait on a GPS fix.
 */
public class PrayerScheduler extends BroadcastReceiver {
    private static final String TAG = "Prayer";
    public static final String PREF_FACE_AUTO = "prayer_face_auto";
    public static final String PREF_ALERTS = "prayer_alerts";
    public static final String PREF_HANAFI = "prayer_hanafi";
    public static final String PREF_CLEAN = "prayer_clean_layout";
    private static final String PREF_LAT = "prayer_lat", PREF_LON = "prayer_lon";
    private static final String ACTION = "com.example.dialsender.PRAYER_TICK";
    private static final String EXTRA_PRAYER = "prayer";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dial_sender_prefs", Context.MODE_PRIVATE);
    }

    public static boolean hasLocation(Context c) {
        return prefs(c).contains(PREF_LAT);
    }

    /** Blocking (up to ~12 s): refresh the cached location. Call off the main thread. */
    public static void refreshLocation(Context c) {
        double[] ll = WeatherSync.currentLocation(c.getApplicationContext());
        prefs(c).edit().putFloat(PREF_LAT, (float) ll[0]).putFloat(PREF_LON, (float) ll[1]).apply();
    }

    public static double[] location(Context c) {
        SharedPreferences p = prefs(c);
        return new double[] { p.getFloat(PREF_LAT, 51.5074f), p.getFloat(PREF_LON, -0.1278f) };
    }

    public static long[] today(Context c) {
        double[] ll = location(c);
        return PrayerTimes.forDay(Calendar.getInstance(), ll[0], ll[1], prefs(c).getBoolean(PREF_HANAFI, false));
    }

    private static long[] tomorrow(Context c) {
        double[] ll = location(c);
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_YEAR, 1);
        return PrayerTimes.forDay(t, ll[0], ll[1], prefs(c).getBoolean(PREF_HANAFI, false));
    }

    /** Photo file (HryFaces catalogue) behind the prayer bar; absent = the drawn emerald design. */
    public static final String PREF_PRAYER_PHOTO = "prayer_face_photo_file";

    /** PREF_PRAYER_PHOTO value meaning "the 24-hour prayer ring" instead of a photo. */
    public static final String RING = "@ring";

    public static HryFaces.Face prayerFace(Context c, int w, int h) {
        if (RING.equals(prefs(c).getString(PREF_PRAYER_PHOTO, "")))
            return HryFaces.prayerRing(c, w, h, today(c), System.currentTimeMillis());
        HryFaces.Photo photo = HryFaces.photoByFile(c, prefs(c).getString(PREF_PRAYER_PHOTO, ""));
        if (photo != null) {
            android.graphics.Bitmap src = HryFaces.loadPhoto(c, photo);
            if (src != null)
                return prayerPhotoFace(c, src, w, h, today(c), System.currentTimeMillis());
        }
        double[] ll = location(c);
        return HryFaces.prayer(c, w, h, today(c), System.currentTimeMillis(), ll[0], ll[1]);
    }

    /** The chosen prayer layout over a photo: gold bar (default) or the clean light panel. */
    public static HryFaces.Face prayerPhotoFace(Context c, android.graphics.Bitmap src, int w, int h, long[] times, long now) {
        return prefs(c).getBoolean(PREF_CLEAN, false) ? HryFaces.prayerClean(c, src, w, h, times, now)
                : HryFaces.prayerOnPhoto(c, src, w, h, times, now);
    }

    /** Repaint the prayer face if the user chose it and the watch is reachable. */
    public static void pushFace(Context c) {
        if (!prefs(c).getBoolean(PREF_FACE_AUTO, false))
            return;
        BleManager ble = BleManager.getInstance(c.getApplicationContext());
        if (!ble.isHryFine())
            return;
        int[] wh = ble.getHryScreenSize();
        HryFaces.Face f = prayerFace(c, wh[0], wh[1]);
        ble.sendHryCustomFace(f.image, f.style);
    }

    /** (Re)arm the alarm for the next prayer, or 00:01 when today's are done. */
    public static void schedule(Context c) {
        SharedPreferences p = prefs(c);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent cancel = pending(c, -1);
        am.cancel(cancel);
        if (!p.getBoolean(PREF_FACE_AUTO, false) && !p.getBoolean(PREF_ALERTS, false)
                && !p.getBoolean(QuranReminders.PREF_AFTER_PRAYER, false))
            return;

        long now = System.currentTimeMillis();
        int[] alertable = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        long when = -1;
        int which = -1;
        for (long[] day : new long[][] { today(c), tomorrow(c) }) {
            for (int i : alertable)
                if (day[i] > now + 1000) { when = day[i]; which = i; break; }
            if (when > 0)
                break;
        }
        Calendar midnight = Calendar.getInstance();
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 1);
        midnight.set(Calendar.SECOND, 0);
        if (midnight.getTimeInMillis() < when) {
            when = midnight.getTimeInMillis();
            which = -1;
        }
        PendingIntent pi = pending(c, which);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        Log.d(TAG, "next tick " + new Date(when) + (which >= 0 ? " " + PrayerTimes.NAMES[which] : " (new day)")
                + (exact ? "" : " [inexact]"));
    }

    private static PendingIntent pending(Context c, int prayer) {
        Intent i = new Intent(c, PrayerScheduler.class).setAction(ACTION).putExtra(EXTRA_PRAYER, prayer);
        return PendingIntent.getBroadcast(c, 4711, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Called when the watch (re)connects, so a face missed while away gets painted. */
    public static void onWatchConnected(Context c) {
        pushFace(c);
        schedule(c);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            schedule(c);
            return;
        }
        int prayer = intent.getIntExtra(EXTRA_PRAYER, -1);
        long faceDelay = 3000;
        if (prayer >= 0 && prefs(c).getBoolean(PREF_ALERTS, false)) {
            BleManager ble = BleManager.getInstance(c);
            if (ble.isSessionReady()) {
                String at = new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(new Date());
                ble.sendNotification(127, PrayerTimes.NAMES[prayer], "It is time for " + PrayerTimes.NAMES[prayer]
                        + " · " + at, "com.example.dialsender.prayer");
            }
        }
        // A verse follows the prayer alert (if enabled), 10 s later so the alert can be read first.
        if (prayer >= 0 && prefs(c).getBoolean(QuranReminders.PREF_AFTER_PRAYER, false)
                && QuranReminders.send(c, 10000))
            faceDelay = 10000 + QuranReminders.busyMs(c) + 3000;
        // Let the messages land before the face transfer takes the link.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> pushFace(c), faceDelay);
        schedule(c);
    }
}
