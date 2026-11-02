package com.example.dialsender;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.dialsender.ble.BleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * "Share -> Send to watch" for notes, lists and any text. The watch has no
 * notes app, so a note arrives as messages in its message list; long notes are
 * split into numbered parts that fit the watch's ~190-byte message limit.
 */
public class ShareToWatchActivity extends Activity {
    private static final int PART_CHARS = 150;
    private static final long PART_GAP_MS = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String subject = in.getStringExtra(Intent.EXTRA_SUBJECT);
        finish();

        BleManager ble = BleManager.getInstance(getApplicationContext());
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Nothing to send", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ble.isSessionReady()) {
            Toast.makeText(this, "Watch not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = subject != null && !subject.trim().isEmpty() ? subject.trim() : "Note";
        List<String> parts = split(text.toString().trim());
        Handler h = new Handler(Looper.getMainLooper());
        for (int i = 0; i < parts.size(); i++) {
            String t = parts.size() > 1 ? title + " " + (i + 1) + "/" + parts.size() : title;
            String body = parts.get(i);
            h.postDelayed(() -> ble.sendNotification(127, t, body, "com.example.dialsender.note"), i * PART_GAP_MS);
        }
        Toast.makeText(this, parts.size() > 1 ? "Sending " + parts.size() + " parts to the watch" : "Sent to watch",
                Toast.LENGTH_SHORT).show();
    }

    /** Break on line or word boundaries so parts read naturally. */
    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        while (s.length() > PART_CHARS) {
            int cut = s.lastIndexOf('\n', PART_CHARS);
            if (cut < PART_CHARS / 2)
                cut = s.lastIndexOf(' ', PART_CHARS);
            if (cut < PART_CHARS / 2)
                cut = PART_CHARS;
            out.add(s.substring(0, cut).trim());
            s = s.substring(cut).trim();
        }
        if (!s.isEmpty())
            out.add(s);
        return out;
    }
}
