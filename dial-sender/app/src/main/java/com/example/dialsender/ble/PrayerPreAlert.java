package com.example.dialsender.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A message on the watch 15 minutes before each of the five prayers. */
public class PrayerPreAlert extends BroadcastReceiver {
    public static final String PREF = "prayer_pre_alert";
    private static final long LEAD_MS = 15 * 60_000L;
    private static final String ACTION = "com.example.dialsender.PRAYER_PRE";
    private static final String EXTRA_PRAYER = "prayer", EXTRA_AT = "at";
    private static final int[] PRAYERS = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR,
            PrayerTimes.MAGHRIB, PrayerTimes.ISHA };

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dial_sender_prefs", Context.MODE_PRIVATE);
    }

    /** Arm the next reminder, or cancel if switched off. On by default. */
    public static void schedule(Context context) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Intent base = new Intent(c, PrayerPreAlert.class).setAction(ACTION);
        am.cancel(PendingIntent.getBroadcast(c, 4714, base, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (!prefs(c).getBoolean(PREF, true))
            return;
        long now = System.currentTimeMillis(), when = -1, at = -1;
        int which = -1;
        long[] today = PrayerScheduler.today(c);
        for (int p : PRAYERS)
            if (today[p] - LEAD_MS > now + 1000) {
                which = p;
                at = today[p];
                when = at - LEAD_MS;
                break;
            }
        if (which < 0) {
            which = PrayerTimes.FAJR;
            at = PrayerScheduler.tomorrowFajr(c);
            when = at - LEAD_MS;
        }
        Intent i = new Intent(c, PrayerPreAlert.class).setAction(ACTION).putExtra(EXTRA_PRAYER, which).putExtra(EXTRA_AT, at);
        PendingIntent pi = PendingIntent.getBroadcast(c, 4714, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        int prayer = intent.getIntExtra(EXTRA_PRAYER, -1);
        long at = intent.getLongExtra(EXTRA_AT, 0);
        BleManager ble = BleManager.getInstance(c);
        if (prayer >= 0 && ble.isSessionReady() && prefs(c).getBoolean(PREF, true)) {
            String name = IslamicDay.name(prayer, at);
            String time = new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(new Date(at));
            ble.sendNotification(127, name, "In 15 minutes · " + time, "com.example.dialsender.prayer");
        }
        schedule(c);
    }
}
