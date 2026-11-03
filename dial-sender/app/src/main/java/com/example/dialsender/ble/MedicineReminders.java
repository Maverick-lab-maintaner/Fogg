package com.example.dialsender.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;
import java.util.Locale;

/**
 * Medicine reminders for watches whose firmware has no medicine feature (the
 * H19 reports it off in its feature bits). The phone keeps up to three daily
 * times and, at each one, sends a short message the watch buzzes and shows.
 * Watches that support it natively get the reminder list instead (setting key 32).
 */
public class MedicineReminders extends BroadcastReceiver {
    private static final String ACTION = "com.example.dialsender.MEDICINE_TICK";
    static final String PKG = "com.example.dialsender.medicine";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dial_sender_prefs", Context.MODE_PRIVATE);
    }

    /** Arm the next reminder (or cancel if none is on). Phone-side only when the watch lacks the feature. */
    public static void schedule(Context context) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 4712, new Intent(c, MedicineReminders.class).setAction(ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (BleManager.getInstance(c).isHryMedicineNative())
            return;
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (!p.getBoolean(BleManager.HRW + "med" + i + "_on", false))
                continue;
            int m = p.getInt(BleManager.HRW + "med" + i + "_time", 8 * 60 + i * 6 * 60);
            Calendar at = Calendar.getInstance();
            at.set(Calendar.HOUR_OF_DAY, m / 60);
            at.set(Calendar.MINUTE, m % 60);
            at.set(Calendar.SECOND, 0);
            at.set(Calendar.MILLISECOND, 0);
            if (at.getTimeInMillis() <= now + 1000)
                at.add(Calendar.DAY_OF_YEAR, 1);
            next = Math.min(next, at.getTimeInMillis());
        }
        if (next == Long.MAX_VALUE)
            return;
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            schedule(c);
            return;
        }
        BleManager ble = BleManager.getInstance(c);
        if (ble.isSessionReady()) {
            String at = new java.text.SimpleDateFormat("HH:mm", Locale.ENGLISH).format(new java.util.Date());
            ble.sendNotification(127, "Medicine", "Time to take your medicine · " + at, PKG);
        }
        schedule(c);
    }
}
