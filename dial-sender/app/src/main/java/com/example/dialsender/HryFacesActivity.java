package com.example.dialsender;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.example.dialsender.ble.BleManager;
import com.example.dialsender.ble.HryFaces;
import com.example.dialsender.ble.PrayerScheduler;
import com.example.dialsender.ble.PrayerTimes;
import com.example.dialsender.ble.QuranReminders;
import com.example.dialsender.theme.ThemeManager;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Ready-made faces for HryFine watches, plus the prayer-time options. */
public class HryFacesActivity extends AppCompatActivity {

    private ThemeManager.AppTheme theme;
    private BleManager ble;
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView timesView;
    private ActivityResultLauncher<String> photoPicker;
    private ActivityResultLauncher<String> dialPicker;
    private ActivityResultLauncher<String[]> locationPermission;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        theme = ThemeManager.getTheme(this);
        ble = BleManager.getInstance(this);
        prefs = getSharedPreferences("dial_sender_prefs", MODE_PRIVATE);

        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoPicked);
        dialPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onDialPicked);
        locationPermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                r -> refreshLocationAsync());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(theme.bgPrimary);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(40));
        scroll.addView(root);
        setContentView(scroll);

        // ---- Header
        TextView back = label("←  Watch faces", 24, theme.textPrimary, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        TextView status = pill(ble.isHryFine() ? "●  Watch connected" : "●  Not connected — connect the watch first",
                ble.isHryFine() ? theme.success : theme.warning);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        root.addView(status, slp);

        int[] wh = ble.getHryScreenSize();

        // ---- Prayer times
        LinearLayout pt = panel("Prayer times", "Calculated on your phone · Muslim World League angles");
        timesGrid = new LinearLayout(this);
        timesGrid.setOrientation(LinearLayout.HORIZONTAL);
        timesGrid.setPadding(0, dp(14), 0, 0);
        pt.addView(timesGrid);
        timesView = label("", 12, theme.textMuted, false);
        timesView.setPadding(0, dp(10), 0, 0);
        pt.addView(timesView);
        pt.addView(divider());
        pt.addView(toggle("Prayer alerts on the watch", PrayerScheduler.PREF_ALERTS));
        pt.addView(toggle("Keep the prayer face up to date", PrayerScheduler.PREF_FACE_AUTO));
        pt.addView(toggle("Hanafi Asr (later Asr time)", PrayerScheduler.PREF_HANAFI));
        pt.addView(toggle("Clean layout (light panel)", PrayerScheduler.PREF_CLEAN));
        LinearLayout locRow = new LinearLayout(this);
        locRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l1.topMargin = dp(14);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l2.topMargin = dp(14);
        l2.setMarginStart(dp(10));
        locRow.addView(button("Use my location", false, v -> askLocation()), l1);
        locRow.addView(button("Choose city", false, v -> chooseCity()), l2);
        pt.addView(locRow);

        // ---- Quranic reminders
        LinearLayout qr = panel("Quranic reminders", QuranReminders.all(this).size()
                + " short verses, each shown whole on the watch, in turn");
        qr.addView(toggle("Send a verse after each prayer", QuranReminders.PREF_AFTER_PRAYER));
        LinearLayout qButtons = new LinearLayout(this);
        qButtons.setOrientation(LinearLayout.HORIZONTAL);
        TextView lang = button("", false, null);
        Runnable showLang = () -> lang.setText("Language: " + QuranReminders.LANG_NAMES[
                prefs.getInt(QuranReminders.PREF_LANG, QuranReminders.DEFAULT_LANG)]);
        showLang.run();
        lang.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Verse language")
                .setSingleChoiceItems(QuranReminders.LANG_NAMES,
                        prefs.getInt(QuranReminders.PREF_LANG, QuranReminders.DEFAULT_LANG), (d, w) -> {
                    prefs.edit().putInt(QuranReminders.PREF_LANG, w).apply();
                    showLang.run();
                    d.dismiss();
                }).show());
        TextView sendVerse = button("Send a verse now", true, v -> Toast.makeText(this,
                QuranReminders.send(this, 0) ? "Verse sent" : "Connect the watch first", Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        half.topMargin = dp(14);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        half2.topMargin = dp(14);
        half2.setMarginStart(dp(10));
        qButtons.addView(lang, half);
        qButtons.addView(sendVerse, half2);
        qr.addView(qButtons);
        qr.addView(note("Your watch cannot show Arabic letters, so English is the default. "
                + "Arabic: Tanzil Quran Text (CC BY 3.0). English: Pickthall (1930, public domain)."));

        // Self-compiled "live" dials are deliberately not offered: they crashed the
        // H19 into its bootloader four times (see WHAT_WAS_DONE_AND_NEXT.md, Phases 3–5).

        // ---- Photo galleries
        long[] times = PrayerScheduler.today(this);
        long now = System.currentTimeMillis();
        String[][] groups = {
                { "Mosques", "Prayer faces · Mosques", "Your next prayer and all five times over the photo" },
                { "Islamic art", "Prayer faces · Islamic art", "Domes, tiles and windows with the prayer times" },
                { "Premium", "Premium photos", "Plain photo faces with the watch's own clock" } };
        for (String[] g : groups) {
            java.util.List<HryFaces.Photo> list = new java.util.ArrayList<>();
            for (HryFaces.Photo p : HryFaces.photos(this))
                if (p.group.equals(g[0]))
                    list.add(p);
            if (list.isEmpty())
                continue;
            LinearLayout gp = panel(g[1], g[2] + " · " + list.size() + " faces");
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % 2 == 0) {
                    row = gridRow();
                    gp.addView(row);
                }
                HryFaces.Photo p = list.get(i);
                View card = card(p.label, null, v -> onPhotoFaceTapped(p));
                row.addView(card);
                ImageView img = (ImageView) ((LinearLayout) card).getChildAt(0);
                previews.execute(() -> {
                    Bitmap src = HryFaces.loadPhoto(this, p);
                    if (src == null) return;
                    Bitmap bm = p.prayer() ? PrayerScheduler.prayerPhotoFace(this, src, wh[0], wh[1], times, now).image
                            : HryFaces.photo(src, wh[0], wh[1]).image;
                    runOnUiThread(() -> {
                        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        img.setImageBitmap(bm);
                    });
                });
            }
            padRow(row);
        }

        // ---- Drawn designs (incl. the 24-hour Prayer Ring) and dial files
        LinearLayout dsn = panel("Drawn designs", "Prayer Ring and ornamental faces · or load a HryFine dial file");
        LinearLayout row = null;
        int count = PHOTO + 1;
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                row = gridRow();
                dsn.addView(row);
            }
            final int index = i;
            Bitmap preview = i == PHOTO ? null : render(i, wh[0], wh[1]).image;
            row.addView(card(i == PHOTO ? "Your photo" : HryFaces.NAMES[i], preview, v -> onFaceTapped(index)));
        }
        if (count % 2 == 1)
            row.addView(card("Load dial file", null, v -> dialPicker.launch("*/*")));
        else {
            LinearLayout r2 = gridRow();
            r2.addView(card("Load dial file", null, v -> dialPicker.launch("*/*")));
            padRow(r2);
            dsn.addView(r2);
        }
        dsn.addView(note("“Load dial file” installs HryFine-format dial files (.bin) from HryFine’s dial store."));

        // ---- Vector designs
        LinearLayout vp = panel("Vector designs", "Calligraphy, patterns and silhouettes drawn at the watch's exact size");
        LinearLayout vrow = null;
        for (int i = 0; i < com.example.dialsender.ble.HrySvgFaces.count(); i++) {
            if (i % 2 == 0) {
                vrow = gridRow();
                vp.addView(vrow);
            }
            final int vi = i;
            View card = card(com.example.dialsender.ble.HrySvgFaces.name(i), null, v -> onVectorTapped(vi));
            vrow.addView(card);
            ImageView vimg = (ImageView) ((LinearLayout) card).getChildAt(0);
            previews.execute(() -> {
                HryFaces.Face f = com.example.dialsender.ble.HrySvgFaces.render(this, vi, wh[0], wh[1]);
                if (f != null)
                    runOnUiThread(() -> {
                        vimg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        vimg.setImageBitmap(f.image);
                    });
            });
        }
        padRow(vrow);

        // ---- Credits
        LinearLayout cp = panel("Credits", "Photos and vector art from Wikimedia Commons · fonts Amiri and Cinzel (SIL OFL)");
        cp.addView(button("View all credits", false, v -> showCredits()));

        if (!PrayerScheduler.hasLocation(this))
            askLocation();
        showTimes();
    }

    private void showCredits() {
        StringBuilder sb = new StringBuilder();
        for (HryFaces.Photo p : HryFaces.photos(this))
            sb.append(p.label).append("\n").append(p.title).append("\nby ").append(p.author)
                    .append(" · ").append(p.license).append("\n").append(p.url).append("\n\n");
        sb.append("Faces built from these photos are adaptations (cropped, resized, overlaid) "
                + "and keep the photo's licence.\n\nVECTOR ARTWORK\n\n");
        try (java.io.InputStream in = getAssets().open("hryfaces/svg/credits.json")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
            org.json.JSONArray a = new org.json.JSONArray(bo.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                sb.append(o.optString("title")).append("\nby ").append(o.optString("author")).append(" · ")
                        .append(o.optString("license")).append("\n").append(o.optString("url")).append("\n\n");
            }
        } catch (Exception ignored) {
        }
        sb.append("Vector faces recolour these drawings onto new backgrounds (adaptations; same licences). "
                + "Rendered with AndroidSVG (Apache-2.0).");
        TextView t = label(sb.toString(), 12, theme.textPrimary, false);
        t.setTextIsSelectable(true);
        t.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView s = new ScrollView(this);
        s.addView(t);
        new AlertDialog.Builder(this).setTitle("Photo credits").setView(s).setPositiveButton("OK", null).show();
    }

    // -------------------------------------------------------------- faces

    private static final int PHOTO = HryFaces.NAMES.length;

    private HryFaces.Face render(int index, int w, int h) {
        double[] ll = PrayerScheduler.location(this);
        return HryFaces.render(this, index, w, h, PrayerScheduler.today(this), ll[0], ll[1]);
    }

    private void onVectorTapped(int index) {
        if (!ble.isHryFine()) {
            Toast.makeText(this, "Connect the watch first", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] wh = ble.getHryScreenSize();
        previews.execute(() -> {
            HryFaces.Face f = com.example.dialsender.ble.HrySvgFaces.render(this, index, wh[0], wh[1]);
            runOnUiThread(() -> {
                if (f == null)
                    Toast.makeText(this, "Could not draw this design", Toast.LENGTH_SHORT).show();
                else
                    send(f, false);
            });
        });
    }

    private void onFaceTapped(int index) {
        if (!ble.isHryFine()) {
            Toast.makeText(this, "Connect the watch first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (index == PHOTO) {
            photoPicker.launch("image/*");
            return;
        }
        if (index == 0)
            prefs.edit().remove(PrayerScheduler.PREF_PRAYER_PHOTO).apply();
        if (index == HryFaces.RING)
            prefs.edit().putString(PrayerScheduler.PREF_PRAYER_PHOTO, PrayerScheduler.RING).apply();
        int[] wh = ble.getHryScreenSize();
        send(render(index, wh[0], wh[1]), index == 0 || index == HryFaces.RING);
    }

    private final java.util.concurrent.ExecutorService previews = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onDestroy() {
        previews.shutdownNow();
        super.onDestroy();
    }

    /** Islamic photos become the auto-refreshed prayer face; premium photos are sent as they are. */
    private void onPhotoFaceTapped(HryFaces.Photo p) {
        if (!ble.isHryFine()) {
            Toast.makeText(this, "Connect the watch first", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] wh = ble.getHryScreenSize();
        if (p.prayer()) {
            prefs.edit().putString(PrayerScheduler.PREF_PRAYER_PHOTO, p.file).apply();
            send(PrayerScheduler.prayerFace(this, wh[0], wh[1]), true);
            return;
        }
        Bitmap src = HryFaces.loadPhoto(this, p);
        if (src != null)
            send(HryFaces.photo(src, wh[0], wh[1]), false);
    }

    private void onPhotoPicked(Uri uri) {
        if (uri == null)
            return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap src = BitmapFactory.decodeStream(in);
            if (src == null)
                throw new IllegalStateException();
            int[] wh = ble.getHryScreenSize();
            send(HryFaces.photo(src, wh[0], wh[1]), false);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open that picture", Toast.LENGTH_SHORT).show();
        }
    }

    private void onDialPicked(Uri uri) {
        if (uri == null)
            return;
        if (!ble.isHryFine()) {
            Toast.makeText(this, "Connect the watch first", Toast.LENGTH_SHORT).show();
            return;
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; )
                out.write(buf, 0, n);
            prefs.edit().putBoolean(PrayerScheduler.PREF_FACE_AUTO, false).apply();
            refreshToggles();
            runTransfer("Installing dial…", () -> ble.sendHryDialFile(out.toByteArray()));
        } catch (Exception e) {
            Toast.makeText(this, "Could not read that file", Toast.LENGTH_SHORT).show();
        }
    }

    /** Picking the prayer face turns auto-refresh on; any other face turns it off so it is not overwritten. */
    private void send(HryFaces.Face face, boolean isPrayer) {
        prefs.edit().putBoolean(PrayerScheduler.PREF_FACE_AUTO, isPrayer).apply();
        PrayerScheduler.schedule(this);
        refreshToggles();
        runTransfer("Sending face…", () -> ble.sendHryCustomFace(face.image, face.style));
    }

    private void runTransfer(String title, Runnable start) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(8));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        box.addView(bar);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setCancelable(false).create();
        dialog.show();

        BleManager.BleStateListener[] l = new BleManager.BleStateListener[1];
        l[0] = new BleManager.BleStateListener() {
            @Override public void onConnectionStateChange(boolean c, boolean s) { }
            @Override public void onHealthDataReceived(String k, byte[] p) { }
            @Override public void onHealthSyncComplete() { }
            @Override public void onLogUpdated() { }

            @Override
            public void onTransferProgress(int percent, long done, long total) {
                runOnUiThread(() -> bar.setProgress(percent));
            }

            @Override
            public void onTransferComplete() {
                runOnUiThread(() -> {
                    ble.removeListener(l[0]);
                    closeQuietly(dialog);
                    Toast.makeText(HryFacesActivity.this, "Face installed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onTransferFailed(String reason) {
                runOnUiThread(() -> {
                    ble.removeListener(l[0]);
                    closeQuietly(dialog);
                    Toast.makeText(HryFacesActivity.this, "Failed: " + reason, Toast.LENGTH_LONG).show();
                });
            }
        };
        ble.addListener(l[0]);
        start.run();
    }

    /** The screen may be gone by the time a long transfer ends. */
    private void closeQuietly(AlertDialog d) {
        if (isFinishing() || isDestroyed())
            return;
        try {
            d.dismiss();
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ------------------------------------------------------------ prayer

    /** Type a city: works with phone location switched off. */
    private void chooseCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("City, e.g. Birmingham");
        in.setSingleLine(true);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in);
        new AlertDialog.Builder(this).setTitle("Prayer times for which city?").setView(box)
                .setPositiveButton("Use this city", (d, w) -> {
                    String q = in.getText().toString();
                    Toast.makeText(this, "Looking up " + q + "…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        String place = PrayerScheduler.chooseCity(this, q);
                        runOnUiThread(() -> {
                            if (place == null) {
                                Toast.makeText(this, "City not found — check the spelling or your internet",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            showTimes();
                            PrayerScheduler.schedule(this);
                            PrayerScheduler.pushFace(this);
                            com.example.dialsender.ble.WeatherSync.syncIfPossible(this, ble);
                            Toast.makeText(this, "Using " + place, Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void askLocation() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (granted)
            refreshLocationAsync();
        else
            locationPermission.launch(new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION });
    }

    private void refreshLocationAsync() {
        Toast.makeText(this, "Finding your location…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            PrayerScheduler.refreshLocation(this);
            runOnUiThread(() -> {
                showTimes();
                PrayerScheduler.schedule(this);
                Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showTimes() {
        long[] t = PrayerScheduler.today(this);
        long now = System.currentTimeMillis();
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        int[] rows = { PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA };
        int next = -1;
        for (int r : rows)
            if (t[r] > now) { next = r; break; }
        timesGrid.removeAllViews();
        for (int i = 0; i < rows.length; i++) {
            int r = rows[i];
            boolean isNext = r == next, past = t[r] <= now;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(0, dp(10), 0, dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            if (isNext)
                bg.setColor(theme.accentPrimary);
            else {
                bg.setColor(theme.bgCard);
                bg.setStroke(dp(1), theme.cardBorder);
            }
            tile.setBackground(bg);
            int fg = isNext ? theme.onAccent : past ? theme.textMuted : theme.textPrimary;
            TextView name = label(PrayerTimes.NAMES[r], 11, fg, false);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, 0, 0, 0);
            TextView time = label(hm.format(new Date(t[r])), 15, fg, true);
            time.setGravity(Gravity.CENTER);
            time.setPadding(0, dp(2), 0, 0);
            tile.addView(name);
            tile.addView(time);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0)
                lp.setMarginStart(dp(6));
            timesGrid.addView(tile, lp);
        }
        double[] ll = PrayerScheduler.location(this);
        StringBuilder sb = new StringBuilder();
        String place = PrayerScheduler.placeName(this);
        if (!place.isEmpty())
            sb.append("Times for ").append(place)
                    .append(PrayerScheduler.isManual(this) ? " (chosen)" : " (phone location)").append("\n");
        sb.append("Sunrise ").append(hm.format(new Date(t[PrayerTimes.SUNRISE])))
                .append("  ·  Qibla ").append(PrayerTimes.qibla(ll[0], ll[1])).append("° from north");
        if (next < 0)
            sb.append("  ·  All prayers done for today");
        if (!PrayerScheduler.hasLocation(this))
            sb.append("\nUsing a default location until yours is found");
        timesView.setText(sb);
    }

    // ---------------------------------------------------------------- ui

    private final java.util.List<Runnable> toggleRefreshers = new java.util.ArrayList<>();
    private boolean syncingToggles = false;

    private void refreshToggles() {
        syncingToggles = true;
        for (Runnable r : toggleRefreshers) r.run();
        syncingToggles = false;
    }

    private View toggle(String text, String pref) {
        SwitchCompat s = new SwitchCompat(this);
        s.setText(text);
        s.setTextColor(theme.textPrimary);
        s.setTextSize(14);
        s.setPadding(0, dp(12), 0, dp(2));
        s.setChecked(prefs.getBoolean(pref, false));
        s.setOnCheckedChangeListener((b, on) -> {
            if (syncingToggles)
                return;
            prefs.edit().putBoolean(pref, on).apply();
            PrayerScheduler.schedule(this);
            if (PrayerScheduler.PREF_HANAFI.equals(pref))
                showTimes();
            if (on && PrayerScheduler.PREF_FACE_AUTO.equals(pref))
                PrayerScheduler.pushFaceNow(this);
            if (PrayerScheduler.PREF_CLEAN.equals(pref)) {
                PrayerScheduler.pushFace(this);
                recreate();
            }
        });
        toggleRefreshers.add(() -> s.setChecked(prefs.getBoolean(pref, false)));
        return s;
    }

    private View card(String name, @Nullable Bitmap preview, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(8), dp(8), dp(8), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.bgCard);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), theme.cardBorder);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(5), 0, dp(5), 0);
        c.setLayoutParams(lp);
        ImageView img = new ImageView(this);
        // Same aspect as the watch screen (240×286), corners rounded like the watch glass.
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        img.setLayoutParams(ip);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(0xFF0B0F14);
        frame.setCornerRadius(dp(14));
        img.setBackground(frame);
        img.setClipToOutline(true);
        if (preview != null) {
            img.setImageBitmap(preview);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            img.setImageResource(android.R.drawable.ic_menu_gallery);
            img.setScaleType(ImageView.ScaleType.CENTER);
        }
        c.addView(img);
        TextView t = label(name, 13, theme.textPrimary, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(2), dp(8), dp(2), 0);
        c.addView(t);
        c.setOnClickListener(click);
        c.setForeground(getDrawable(android.R.drawable.list_selector_background));
        return c;
    }

    private LinearLayout timesGrid;

    /** A framed section: accent strip, title, one-line description, then its content. */
    private LinearLayout panel(String title, String subtitle) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(16), dp(16), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.bgSurface);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), theme.cardBorder);
        p.setBackground(bg);
        p.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        root.addView(p, lp);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        GradientDrawable acc = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { theme.accentPrimary, theme.accentSecondary });
        acc.setCornerRadius(dp(3));
        bar.setBackground(acc);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(22));
        blp.setMarginEnd(dp(10));
        head.addView(bar, blp);
        TextView t = label(title, 18, theme.textPrimary, true);
        t.setPadding(0, 0, 0, 0);
        head.addView(t);
        p.addView(head);
        if (subtitle != null) {
            TextView st = label(subtitle, 12, theme.textMuted, false);
            st.setPadding(dp(14), dp(2), 0, 0);
            p.addView(st);
        }
        return p;
    }

    private TextView pill(String text, int color) {
        TextView t = label(text, 12, color, true);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor((color & 0x00FFFFFF) | 0x22000000);
        t.setBackground(bg);
        return t;
    }

    private TextView button(String text, boolean filled, @Nullable View.OnClickListener click) {
        TextView b = label(text, 14, filled ? theme.onAccent : theme.accentPrimary, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setMaxLines(1);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (filled)
            bg.setColor(theme.accentPrimary);
        else {
            bg.setColor((theme.accentPrimary & 0x00FFFFFF) | 0x14000000);
            bg.setStroke(dp(1), theme.accentPrimary);
        }
        b.setBackground(bg);
        if (click != null)
            b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView note(String text) {
        TextView t = label(text, 12, theme.textMuted, false);
        t.setPadding(0, dp(12), 0, 0);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(theme.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(2);
        d.setLayoutParams(lp);
        return d;
    }

    private LinearLayout gridRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(12), 0, 0);
        return r;
    }

    /** Keeps a lone last card at half width. */
    private void padRow(@Nullable LinearLayout r) {
        if (r != null && r.getChildCount() == 1) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
            lp.setMargins(dp(5), 0, dp(5), 0);
            r.addView(new View(this), lp);
        }
    }

    private TextView section(String text) {
        TextView t = label(text, 17, theme.textPrimary, true);
        t.setPadding(0, dp(24), 0, dp(6));
        return t;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
