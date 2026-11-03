package com.example.dialsender;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.dialsender.ble.BleManager;
import com.example.dialsender.theme.ThemeManager;

import java.util.Locale;

/**
 * HryFine (H19) watch settings that Fogg had no screen for. Everything is kept
 * on the phone (prefs "hrw_*") and re-sent on every connect, because the watch
 * cannot report these back.
 */
public class HryWatchSettingsActivity extends AppCompatActivity {
    private static final String H = BleManager.HRW;
    private ThemeManager.AppTheme theme;
    private SharedPreferences prefs;
    private BleManager ble;
    private LinearLayout root;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        theme = ThemeManager.getTheme(this);
        prefs = getSharedPreferences("dial_sender_prefs", MODE_PRIVATE);
        ble = BleManager.getInstance(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(theme.bgPrimary);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(40));
        scroll.addView(root);
        setContentView(scroll);

        TextView back = label("←  Watch settings", 24, theme.textPrimary, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(label(ble.isHryFine() ? "Changes are sent to the watch straight away"
                : "Connect the watch — changes are sent when it connects", 13, theme.textSecondary, false));

        LinearLayout raise = panel("Raise to wake", "Lift your wrist to light up the screen");
        raise.addView(toggle("Raise to wake", "raise_on", true));
        raise.addView(toggle("All day", "raise_allday", true));
        raise.addView(timeRow("From", "raise_start", 7 * 60, "To", "raise_end", 23 * 60));

        LinearLayout dnd = panel("Do not disturb", "No buzzing during these hours");
        dnd.addView(toggle("Do not disturb", "dnd_on", false));
        dnd.addView(toggle("All day", "dnd_allday", false));
        dnd.addView(timeRow("From", "dnd_start", 22 * 60, "To", "dnd_end", 7 * 60));

        LinearLayout wear = panel("Watch", "How the watch is worn and shows time");
        wear.addView(choice("Wrist", "left_wrist", true, "Left wrist", "Right wrist"));
        wear.addView(choice("Clock", "h24", android.text.format.DateFormat.is24HourFormat(this), "24-hour", "12-hour"));
        wear.addView(choice("Units", "metric", true, "Metric (km, kg)", "Imperial (mi, lb)"));

        LinearLayout sit = panel("Move reminder", "A buzz when you have been sitting too long");
        sit.addView(toggle("Remind me to move", "sit_on", false));
        sit.addView(minutes("Every", "sit_every", 60, new int[] { 30, 45, 60, 90, 120 }));
        sit.addView(hourRow("From", "sit_start", 9, "To", "sit_end", 21));

        LinearLayout drink = panel("Drink water", "Regular reminders to drink");
        drink.addView(toggle("Remind me to drink", "drink_on", false));
        drink.addView(minutes("Every", "drink_every", 120, new int[] { 30, 60, 90, 120, 180 }));
        drink.addView(hourRow("From", "drink_start", 9, "To", "drink_end", 21));

        LinearLayout med = panel("Medicine", "Up to three reminders a day");
        for (int i = 0; i < 3; i++) {
            med.addView(toggle("Reminder " + (i + 1), "med" + i + "_on", false));
            med.addView(timeRow("At", "med" + i + "_time", 8 * 60 + i * 6 * 60, null, null, 0));
        }

        LinearLayout call = panel("Calls on the watch", "Pair the watch's calling Bluetooth so it stays on");
        call.addView(label("Turn on Bluetooth calling on the watch first, then tap below.", 12, theme.textMuted, false));
        call.addView(button("Pair calling Bluetooth", v ->
                Toast.makeText(this, ble.pairHryCallBluetooth(), Toast.LENGTH_LONG).show()));

        LinearLayout cam = panel("Camera", "Use the watch as a shutter button");
        cam.addView(button("Open camera", v -> startActivity(new Intent(this, CameraActivity.class))));
    }

    // ------------------------------------------------------------- push

    private void changed() {
        if (ble.isSessionReady()) {
            ble.pushHryWatchSettings();
        } else {
            Toast.makeText(this, "Saved — sent when the watch connects", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------- rows

    private View toggle(String text, String key, boolean def) {
        SwitchCompat s = new SwitchCompat(this);
        s.setText(text);
        s.setTextColor(theme.textPrimary);
        s.setTextSize(14);
        s.setPadding(0, dp(12), 0, dp(2));
        s.setChecked(prefs.getBoolean(H + key, def));
        s.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(H + key, on).apply();
            changed();
        });
        return s;
    }

    private View choice(String title, String key, boolean def, String yes, String no) {
        TextView b = button("", null);
        Runnable show = () -> b.setText(title + ": " + (prefs.getBoolean(H + key, def) ? yes : no));
        show.run();
        b.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(new String[] { yes, no }, prefs.getBoolean(H + key, def) ? 0 : 1, (d, w) -> {
                    prefs.edit().putBoolean(H + key, w == 0).apply();
                    show.run();
                    d.dismiss();
                    changed();
                }).show());
        return b;
    }

    private View minutes(String title, String key, int def, int[] options) {
        TextView b = button("", null);
        Runnable show = () -> b.setText(title + " " + prefs.getInt(H + key, def) + " minutes");
        show.run();
        String[] names = new String[options.length];
        int sel = 0;
        for (int i = 0; i < options.length; i++) {
            names[i] = options[i] + " minutes";
            if (options[i] == prefs.getInt(H + key, def))
                sel = i;
        }
        int selected = sel;
        b.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(names, selected, (d, w) -> {
                    prefs.edit().putInt(H + key, options[w]).apply();
                    show.run();
                    d.dismiss();
                    changed();
                }).show());
        return b;
    }

    /** One or two time buttons (minutes after midnight). */
    private View timeRow(String t1, String k1, int d1, @Nullable String t2, @Nullable String k2, int d2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(timeButton(t1, k1, d1, false), weight(false));
        if (k2 != null)
            row.addView(timeButton(t2, k2, d2, false), weight(true));
        return row;
    }

    /** Two whole-hour buttons (the watch's reminders work in hours). */
    private View hourRow(String t1, String k1, int d1, String t2, String k2, int d2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(timeButton(t1, k1, d1, true), weight(false));
        row.addView(timeButton(t2, k2, d2, true), weight(true));
        return row;
    }

    private TextView timeButton(String title, String key, int def, boolean hoursOnly) {
        TextView b = button("", null);
        Runnable show = () -> {
            int v = prefs.getInt(H + key, def);
            int m = hoursOnly ? v * 60 : v;
            b.setText(title + "  " + String.format(Locale.ENGLISH, "%02d:%02d", m / 60, m % 60));
        };
        show.run();
        b.setOnClickListener(v -> {
            int cur = prefs.getInt(H + key, def);
            int m = hoursOnly ? cur * 60 : cur;
            new TimePickerDialog(this, (tp, h, min) -> {
                prefs.edit().putInt(H + key, hoursOnly ? h : h * 60 + min).apply();
                show.run();
                changed();
            }, m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
        });
        return b;
    }

    private LinearLayout.LayoutParams weight(boolean second) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.topMargin = dp(12);
        if (second)
            lp.setMarginStart(dp(10));
        return lp;
    }

    // ------------------------------------------------------------- look

    private LinearLayout panel(String title, String subtitle) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(16), dp(16), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.bgSurface);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), theme.cardBorder);
        p.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        root.addView(p, lp);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        GradientDrawable acc = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { theme.accentPrimary, theme.accentSecondary });
        acc.setCornerRadius(dp(3));
        bar.setBackground(acc);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(22));
        blp.setMarginEnd(dp(10));
        head.addView(bar, blp);
        head.addView(label(title, 18, theme.textPrimary, true));
        p.addView(head);
        TextView st = label(subtitle, 12, theme.textMuted, false);
        st.setPadding(dp(14), dp(2), 0, 0);
        p.addView(st);
        return p;
    }

    private TextView button(String text, @Nullable View.OnClickListener click) {
        TextView b = label(text, 14, theme.accentPrimary, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor((theme.accentPrimary & 0x00FFFFFF) | 0x14000000);
        bg.setStroke(dp(1), theme.accentPrimary);
        b.setBackground(bg);
        if (click != null)
            b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        if (bold)
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
