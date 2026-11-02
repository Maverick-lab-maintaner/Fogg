package com.example.dialsender.ble;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class WatchNotificationService extends NotificationListenerService {

    /** Follow the language chosen in the app, not the system one. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(com.example.dialsender.LocaleHelper.wrap(base));
    }


    private static final String TAG = "WatchNotif";
    private static final String PREF_NAME = "dial_sender_prefs";
    private static final String PREF_NOTIF_PACKAGES = "notif_enabled_packages";
    private static final int CATEGORY_CALL = 1;
    private static final int CATEGORY_MESSAGE = 127; // 0x7F = generic message category in watch firmware

    private SharedPreferences prefs;
    private Set<String> enabledPackages = new HashSet<>();
    private String activeCallKey = null;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        reloadPackages();
        prefListener = (p, key) -> {
            if (PREF_NOTIF_PACKAGES.equals(key)) reloadPackages();
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    private void reloadPackages() {
        String saved = prefs.getString(PREF_NOTIF_PACKAGES, "");
        enabledPackages = new HashSet<>(Arrays.asList(saved.split(",")));
        enabledPackages.remove("");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        Notification n = sbn.getNotification();
        android.util.Log.d(TAG, "posted pkg=" + pkg + " cat=" + n.category + " clearable=" + sbn.isClearable()
                + " flags=0x" + Integer.toHexString(n.flags));

        BleManager ble = BleManager.getInstance(getApplicationContext());
        if (!ble.isSessionReady()) { android.util.Log.d(TAG, "drop: watch session not ready"); return; }

        // Skip our own notifications
        if (pkg.equals(getPackageName())) return;

        // Check whitelist
        if (!enabledPackages.contains(pkg)) { android.util.Log.d(TAG, "drop: not in whitelist " + enabledPackages); return; }

        // Ringing calls (phone, WhatsApp, Telegram...) are ongoing, so they must
        // be handled before the non-clearable filter below.
        if (Notification.CATEGORY_CALL.equals(n.category)) {
            if (!isRingingCall(n) || sbn.getKey().equals(activeCallKey)) { android.util.Log.d(TAG, "drop: call not ringing / already sent"); return; }
            activeCallKey = sbn.getKey();
            ble.sendNotification(CATEGORY_CALL, text(n.extras, Notification.EXTRA_TITLE), "", pkg);
            return;
        }

        // Skip ongoing/non-clearable notifications (service, media, navigation)
        if (!sbn.isClearable()) { android.util.Log.d(TAG, "drop: not clearable"); return; }

        // Skip group summary notifications (no real content)
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) { android.util.Log.d(TAG, "drop: group summary"); return; }

        Notification notif = n;
        Bundle extras = notif.extras;

        // Apps like WhatsApp post styled CharSequences; getString() returns "" for those.
        String title = text(extras, NotificationCompat.EXTRA_TITLE);
        String bigText = text(extras, NotificationCompat.EXTRA_BIG_TEXT);
        String content = !bigText.isEmpty() ? bigText : text(extras, NotificationCompat.EXTRA_TEXT);

        int category = Notification.CATEGORY_CALL.equals(notif.category) ? CATEGORY_CALL : CATEGORY_MESSAGE;

        ble.sendNotification(category, title, content, pkg);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || activeCallKey == null || !activeCallKey.equals(sbn.getKey())) return;
        activeCallKey = null;
        BleManager ble = BleManager.getInstance(getApplicationContext());
        ble.sendCallState(BleManager.CALL_IDLE);
        ble.dismissCallNotification();
    }

    private static String text(Bundle extras, String key) {
        CharSequence cs = extras.getCharSequence(key);
        return cs == null ? "" : cs.toString();
    }

    /** Incoming, not an already-answered call (those show a running timer). */
    private static boolean isRingingCall(Notification n) {
        Bundle x = n.extras;
        if (android.os.Build.VERSION.SDK_INT >= 31 && x.containsKey(Notification.EXTRA_CALL_TYPE))
            return x.getInt(Notification.EXTRA_CALL_TYPE) == Notification.CallStyle.CALL_TYPE_INCOMING;
        return !x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
    }
}
