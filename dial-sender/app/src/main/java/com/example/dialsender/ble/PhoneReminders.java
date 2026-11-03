package com.example.dialsender.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/**
 * Hand-washing and period-tracker reminders for HryFine watches, whose firmware
 * has neither. Fogg's existing screens keep working: their values are stored
 * here and, at each due time, the phone sends a short message the watch shows.
 */
public class PhoneReminders extends BroadcastReceiver {
    private static final String ACTION = "com.example.dialsender.PHONE_REMINDER";
    static final String PKG = "com.example.dialsender.reminder";
    private static final String P = "phrem_";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dial_sender_prefs", Context.MODE_PRIVATE);
    }

    static void saveWash(Context c, boolean on, int repeat, int startMin, int endMin, int every) {
        prefs(c).edit().putBoolean(P + "wash_on", on).putInt(P + "wash_repeat", repeat == 0 ? 127 : repeat)
                .putInt(P + "wash_start", startMin).putInt(P + "wash_end", endMin)
                .putInt(P + "wash_every", Math.max(every, 15)).apply();
        schedule(c);
    }

    static void saveCare(Context c, boolean on, boolean remind, int remindMin, int periodAdvance, int ovulationAdvance,
                         int y, int m, int d, int cycle) {
        prefs(c).edit().putBoolean(P + "care_on", on && remind).putInt(P + "care_time", remindMin)
                .putInt(P + "care_padv", periodAdvance).putInt(P + "care_oadv", ovulationAdvance)
                .putInt(P + "care_y", y).putInt(P + "care_m", m).putInt(P + "care_d", d)
                .putInt(P + "care_cycle", cycle <= 0 ? 28 : cycle).apply();
        schedule(c);
    }

    // ---------------------------------------------------------------- timing

    private static long nextWash(SharedPreferences p, long now) {
        if (!p.getBoolean(P + "wash_on", false))
            return Long.MAX_VALUE;
        int repeat = p.getInt(P + "wash_repeat", 127), start = p.getInt(P + "wash_start", 9 * 60),
                end = p.getInt(P + "wash_end", 21 * 60), every = p.getInt(P + "wash_every", 60);
        for (int day = 0; day < 8; day++) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, day);
            int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
            if ((repeat & (1 << dow)) == 0)
                continue;
            for (int t = start; t <= end; t += every) {
                c.set(Calendar.HOUR_OF_DAY, t / 60);
                c.set(Calendar.MINUTE, t % 60);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() > now + 1000)
                    return c.getTimeInMillis();
            }
        }
        return Long.MAX_VALUE;
    }

    /** {when, kind}: kind 1 = period coming, 2 = ovulation coming. */
    private static long[] nextCare(SharedPreferences p, long now) {
        if (!p.getBoolean(P + "care_on", false) || p.getInt(P + "care_y", 0) < 2000)
            return new long[] { Long.MAX_VALUE, 0 };
        int cycle = p.getInt(P + "care_cycle", 28), t = p.getInt(P + "care_time", 8 * 60);
        Calendar last = Calendar.getInstance();
        last.set(p.getInt(P + "care_y", 2000), p.getInt(P + "care_m", 1) - 1, p.getInt(P + "care_d", 1), t / 60, t % 60, 0);
        last.set(Calendar.MILLISECOND, 0);
        long best = Long.MAX_VALUE, kind = 0;
        for (int k = 0; k < 24; k++) {
            Calendar period = (Calendar) last.clone();
            period.add(Calendar.DAY_OF_YEAR, cycle * (k + 1));
            Calendar pr = (Calendar) period.clone();
            pr.add(Calendar.DAY_OF_YEAR, -Math.max(p.getInt(P + "care_padv", 1), 0));
            Calendar ov = (Calendar) period.clone();
            ov.add(Calendar.DAY_OF_YEAR, -14 - Math.max(p.getInt(P + "care_oadv", 1), 0));
            if (ov.getTimeInMillis() > now + 1000 && ov.getTimeInMillis() < best) {
                best = ov.getTimeInMillis();
                kind = 2;
            }
            if (pr.getTimeInMillis() > now + 1000 && pr.getTimeInMillis() < best) {
                best = pr.getTimeInMillis();
                kind = 1;
            }
            if (best != Long.MAX_VALUE)
                break;
        }
        return new long[] { best, kind };
    }

    public static void schedule(Context context) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        long wash = nextWash(p, now);
        long[] care = nextCare(p, now);
        long when = Math.min(wash, care[0]);
        int kind = when == wash ? 0 : (int) care[1];
        Intent i = new Intent(c, PhoneReminders.class).setAction(ACTION).putExtra("kind", kind);
        PendingIntent pi = PendingIntent.getBroadcast(c, 4713, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (when == Long.MAX_VALUE)
            return;
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            BleManager ble = BleManager.getInstance(c);
            if (ble.isSessionReady() && ble.isHryFine()) {
                int kind = intent.getIntExtra("kind", 0);
                SharedPreferences p = prefs(c);
                if (kind == 0)
                    ble.sendNotification(127, "Hand washing", "Time to wash your hands", PKG);
                else if (kind == 1)
                    ble.sendNotification(127, "Period tracker", "Your period may start in "
                            + p.getInt(P + "care_padv", 1) + " day(s)", PKG);
                else
                    ble.sendNotification(127, "Period tracker", "Ovulation expected in "
                            + p.getInt(P + "care_oadv", 1) + " day(s)", PKG);
            }
        }
        schedule(c);
    }
}
