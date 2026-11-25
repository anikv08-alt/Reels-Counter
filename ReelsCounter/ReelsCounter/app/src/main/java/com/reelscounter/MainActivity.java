package com.reelscounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The app screen: on/off status, today / week / month / all-time stats, charts and settings. */
public class MainActivity extends Activity {

    // Theme: charcoal grey + burgundy + champagne gold
    private static final int BG = 0xFF141416;      // charcoal background
    private static final int CARD = 0xFF232227;    // grey cards
    private static final int MUTED = 0xFF9E9CA6;   // soft grey text
    private static final int GREEN = 0xFF8FCB9B;   // sage "ON"
    private static final int RED = 0xFFE5737F;     // rose warnings
    private static final int ACCENT = 0xFF8C2341;  // burgundy buttons
    private static final int GOLD = 0xFFD4AF7A;    // champagne titles

    private SharedPreferences prefs;
    private Stats stats;
    private LinearLayout content;
    private static final int PICK_PHOTOS = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        stats = Stats.get(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(40));
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        content.removeAllViews();
        content.addView(text("Reels Counter", 28, Color.WHITE, true));
        content.addView(text("An honest count of your scrolling.", 14, MUTED, false));

        // ---- on / off ----
        boolean on = isServiceOn();
        LinearLayout status = card();
        status.addView(text(on ? "●  Counter is ON" : "●  Counter is OFF", 17, on ? GREEN : RED, true));
        if (on) {
            status.addView(text("Open Instagram Reels and the bubble will count every reel. "
                    + "Drag the bubble to move it. Tap it to make it smaller.", 14, MUTED, false));
        } else {
            status.addView(text("1. Tap \"Turn on counter\"\n"
                    + "2. Go to Installed apps → Reels Counter\n"
                    + "3. Switch it on and tap Allow\n\n"
                    + "Switch greyed out or says \"Restricted setting\"? Tap the second button, "
                    + "then the ⋮ menu (top right) → Allow restricted settings, and try again.", 14, MUTED, false));
            status.addView(button("Turn on counter", v ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
            status.addView(button("Open app info (for restricted setting)", v -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }));
        }
        content.addView(status);

        // ---- today ----
        String today = Stats.today();
        int[] t = stats.day(today);
        int limit = Prefs.limit(prefs);
        LinearLayout todayCard = card();
        todayCard.addView(label("TODAY"));
        todayCard.addView(text(String.valueOf(t[0]), 54, Color.WHITE, true));
        todayCard.addView(text("reels scrolled", 14, MUTED, false));
        todayCard.addView(grid(t[0], t[1], t[2]));
        if (limit > 0) {
            boolean over = t[0] >= limit;
            todayCard.addView(text(over
                    ? "You're " + (t[0] - limit) + " over your daily limit of " + limit + "."
                    : (limit - t[0]) + " reels left before your daily limit of " + limit + ".",
                    14, over ? RED : MUTED, false));
        }
        content.addView(todayCard);

        // ---- when do you scroll most ----
        LinearLayout hoursCard = card();
        hoursCard.addView(label("WHEN YOU SCROLL MOST"));
        int[] hours7 = stats.hourly(Stats.daysAgo(6), today);
        int[] hoursToday = stats.hourly(today, today);
        int peak7 = Stats.peak(hours7);
        int peakToday = Stats.peak(hoursToday);
        if (peak7 < 0) {
            hoursCard.addView(text("Not enough data yet. This fills in as you scroll (it started tracking hours with this update).", 14, MUTED, false));
        } else {
            int total7 = 0;
            for (int v : hours7) total7 += v;
            hoursCard.addView(text(Prefs.hourRange(peak7), 26, Color.WHITE, true));
            hoursCard.addView(text("your busiest hour over the last 7 days ("
                    + Math.round(hours7[peak7] * 100f / Math.max(1, total7)) + "% of your reels)", 13, MUTED, false));
            if (peakToday >= 0) {
                hoursCard.addView(text("Today so far: most around " + Prefs.hourRange(peakToday), 13, MUTED, false));
            }
            ChartView hourChart = new ChartView(this);
            String[] hourLabels = new String[24];
            for (int h = 0; h < 24; h++) {
                hourLabels[h] = h == 0 ? "12a" : h == 6 ? "6a" : h == 12 ? "12p" : h == 18 ? "6p" : "";
            }
            hourChart.setData(hours7, hourLabels, false, peak7);
            hoursCard.addView(hourChart);
        }
        content.addView(hoursCard);

        // ---- last 7 days chart ----
        LinearLayout weekChart = card();
        weekChart.addView(label("LAST 7 DAYS"));
        ChartView chart7 = new ChartView(this);
        String[] labels7 = new String[7];
        SimpleDateFormat dayName = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -(6 - i));
            labels7[i] = i == 6 ? "Today" : dayName.format(c.getTime());
        }
        chart7.setData(stats.lastDays(7), labels7, true, 6);
        weekChart.addView(chart7);
        content.addView(weekChart);

        // ---- week / month / all time ----
        LinearLayout weekCard = period("THIS WEEK (MON–SUN)", Stats.weekStart(), today, Stats.dayOfWeek());
        int[] w = stats.weekComparison();
        weekCard.addView(text(Prefs.weekDiff(w[0], w[1]), 14, w[0] > w[1] ? RED : w[0] < w[1] ? GREEN : MUTED, false));
        weekCard.addView(text("Last week in total: " + w[2] + " reels", 13, MUTED, false));
        content.addView(weekCard);

        LinearLayout monthCard = period("THIS MONTH", Stats.monthStart(), today, Stats.dayOfMonth());
        monthCard.addView(text("Last 30 days", 13, MUTED, true));
        ChartView chart30 = new ChartView(this);
        String[] labels30 = new String[30];
        for (int i = 0; i < 30; i++) {
            int ago = 29 - i;
            if (ago % 5 == 0) {
                Calendar c = Calendar.getInstance();
                c.add(Calendar.DAY_OF_YEAR, -ago);
                labels30[i] = ago == 0 ? "Today" : String.valueOf(c.get(Calendar.DAY_OF_MONTH));
            } else {
                labels30[i] = "";
            }
        }
        chart30.setData(stats.lastDays(30), labels30, false, 29);
        monthCard.addView(chart30);
        content.addView(monthCard);

        int[] all = stats.sum("0000-00-00", "9999-99-99");
        content.addView(period("ALL TIME", "0000-00-00", "9999-99-99", Math.max(1, all[3])));

        // ---- settings ----
        LinearLayout settings = card();
        settings.addView(label("SETTINGS"));
        settings.addView(stepper("Count a reel as \"watched\" if I stay on it for at least",
                "threshold", Prefs.threshold(prefs), 1, 1, 30, " sec"));
        settings.addView(stepper("Daily limit (bubble turns orange, then red, and your phone buzzes)",
                "limit", limit, 10, 0, 2000, " reels"));
        settings.addView(toggle("Show the floating bubble", "bubbleOn", Prefs.bubbleOn(prefs)));
        settings.addView(toggle("Show the bubble only inside Instagram", "onlyIg", Prefs.onlyInInstagram(prefs)));
        settings.addView(button("Reset bubble position", v -> {
            prefs.edit().putLong("resetPosition", System.currentTimeMillis()).apply();
            Toast.makeText(this, "Bubble moved back to the top left", Toast.LENGTH_SHORT).show();
        }));
        content.addView(settings);

        // ---- break screen ----
        LinearLayout breakCard = card();
        breakCard.addView(label("BREAK SCREEN"));
        breakCard.addView(text("When you hit your daily limit, Reels Counter opens over Instagram with your photos, "
                + "a reminder and your week-vs-last-week numbers, and asks if you really want to keep going. "
                + "It comes back every 10 reels after that.", 14, MUTED, false));
        if (limit == 0) breakCard.addView(text("Set a daily limit above to turn this on.", 13, GOLD, false));
        breakCard.addView(toggle("Show the break screen at my limit", "breakScreen", Prefs.breakScreen(prefs)));
        List<Uri> photos = Images.list(prefs);
        breakCard.addView(text(photos.isEmpty() ? "No photos yet. Add things you care about: your goals, people, places."
                : "Your photos (" + photos.size() + ") · tap one to remove it", 13, Color.WHITE, false));
        if (!photos.isEmpty()) {
            HorizontalScrollView strip = new HorizontalScrollView(this);
            strip.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(4));
            for (Uri uri : photos) row.addView(thumbnail(uri));
            strip.addView(row);
            breakCard.addView(strip);
        }
        breakCard.addView(button(photos.size() >= Images.MAX ? "Photo limit reached (" + Images.MAX + ")" : "Add photos", v -> pickPhotos()));
        breakCard.addView(button("Preview the break screen", v -> {
            Intent i = new Intent(this, LimitActivity.class);
            i.putExtra(LimitActivity.EXTRA_PREVIEW, true);
            startActivity(i);
        }));
        content.addView(breakCard);

        // ---- help ----
        LinearLayout help = card();
        help.addView(label("HOW IT COUNTS"));
        help.addView(text("• Scrolled: every reel that comes onto your screen in Instagram "
                + "(Reels tab, Explore, reels opened from chats).\n"
                + "• Watched: reels you stayed on for at least the seconds set above, instead of skipping.\n"
                + "• Time: how long you spent inside the Reels viewer.\n"
                + "• Everything stays on this phone. Nothing is uploaded anywhere.", 14, MUTED, false));
        help.addView(button("Count looks wrong? Open the detection log", v -> showLog()));
        content.addView(help);
    }

    // ---------- pieces ----------

    private LinearLayout period(String title, String from, String to, int days) {
        int[] s = stats.sum(from, to);
        LinearLayout card = card();
        card.addView(label(title));
        card.addView(grid(s[0], s[1], s[2]));
        if (s[0] > 0) {
            card.addView(text("About " + Math.round(s[0] / (float) days) + " reels a day on average", 13, MUTED, false));
        }
        return card;
    }

    private LinearLayout grid(int scrolled, int watched, int seconds) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(4));
        row.addView(cell(String.valueOf(scrolled), "scrolled"));
        row.addView(cell(String.valueOf(watched), "watched"));
        row.addView(cell(Prefs.time(seconds), "in Reels"));
        return row;
    }

    private LinearLayout cell(String value, String caption) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(text(value, 22, Color.WHITE, true));
        c.addView(text(caption, 12, MUTED, false));
        return c;
    }

    private LinearLayout stepper(String title, String key, int value, int step, int min, int max, String unit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(4));
        box.addView(text(title, 14, Color.WHITE, false));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView shown = text(stepLabel(key, value, unit), 18, Color.WHITE, true);
        shown.setGravity(Gravity.CENTER);
        shown.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final int[] current = {value};
        Button minus = smallButton("−");
        Button plus = smallButton("+");
        minus.setOnClickListener(v -> {
            current[0] = Math.max(min, current[0] - step);
            prefs.edit().putInt(key, current[0]).apply();
            shown.setText(stepLabel(key, current[0], unit));
        });
        plus.setOnClickListener(v -> {
            current[0] = Math.min(max, current[0] + step);
            prefs.edit().putInt(key, current[0]).apply();
            shown.setText(stepLabel(key, current[0], unit));
        });
        row.addView(minus);
        row.addView(shown);
        row.addView(plus);
        box.addView(row);
        return box;
    }

    private String stepLabel(String key, int value, String unit) {
        if (key.equals("limit") && value == 0) return "Off";
        return value + unit;
    }

    private Switch toggle(String title, String key, boolean checked) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextColor(Color.WHITE);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        s.setChecked(checked);
        s.setPadding(0, dp(12), 0, dp(4));
        s.setOnCheckedChangeListener((b, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply());
        return s;
    }

    private void showLog() {
        String log = ReelsAccessibilityService.getLog();
        new AlertDialog.Builder(this)
                .setTitle("Detection log (newest first)")
                .setMessage(log)
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Reels Counter log", log));
                    Toast.makeText(this, "Copied. Paste it to whoever is helping you fix the count.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private ImageView thumbnail(Uri uri) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF34323A);
        bg.setCornerRadius(dp(12));
        iv.setBackground(bg);
        iv.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), dp(84));
        lp.rightMargin = dp(8);
        iv.setLayoutParams(lp);
        int px = dp(160);
        new Thread(() -> {
            Bitmap bmp = Images.load(this, uri, px);
            runOnUiThread(() -> {
                if (bmp != null) iv.setImageBitmap(bmp);
            });
        }).start();
        iv.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove this photo?")
                .setMessage("It will be removed from the break screen. The photo itself stays in your gallery.")
                .setPositiveButton("Remove", (d, which) -> {
                    List<Uri> list = Images.list(prefs);
                    list.remove(uri);
                    Images.save(prefs, list);
                    try {
                        getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                    build();
                })
                .setNegativeButton("Keep", null)
                .show());
        return iv;
    }

    private void pickPhotos() {
        if (Images.list(prefs).size() >= Images.MAX) {
            Toast.makeText(this, "You can have up to " + Images.MAX + " photos. Remove one first.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, PICK_PHOTOS);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open your photos", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PHOTOS || resultCode != RESULT_OK || data == null) return;
        List<Uri> list = Images.list(prefs);
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) addPhoto(list, clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            addPhoto(list, data.getData());
        }
        Images.save(prefs, list);
        build();
    }

    private void addPhoto(List<Uri> list, Uri uri) {
        if (uri == null || list.contains(uri) || list.size() >= Images.MAX) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        list.add(uri);
    }

    private boolean isServiceOn() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.toLowerCase(Locale.US).contains(getPackageName().toLowerCase(Locale.US) + "/");
    }

    // ---------- styling helpers ----------

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 12, GOLD, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        l.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        l.setLayoutParams(lp);
        return l;
    }

    private Button button(String s, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(ACCENT);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private Button smallButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF34323A);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(44)));
        return b;
    }
}
