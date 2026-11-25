package com.reelscounter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Full-screen break screen shown when you hit your daily limit. */
public class LimitActivity extends Activity {

    static final String EXTRA_PREVIEW = "preview";

    private static final int BG = 0xFF0E0D10;
    private static final int CARD = 0xFF1E1D22;
    private static final int MUTED = 0xFF9E9CA6;
    private static final int GOLD = 0xFFD4AF7A;
    private static final int BURGUNDY = 0xFF8C2341;
    private static final int ROSE = 0xFFE5737F;
    private static final int SAGE = 0xFF8FCB9B;

    private static final String QUOTE = "Imagine losing your dreams because you couldn't put your phone down.";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView photo;
    private List<Uri> photos;
    private int photoIndex = 0;
    private Button continueButton;
    private int secondsLeft = 5;

    private final Runnable slideshow = new Runnable() {
        @Override
        public void run() {
            if (photos == null || photos.size() < 2) return;
            photoIndex = (photoIndex + 1) % photos.size();
            showPhoto(photoIndex, true);
            handler.postDelayed(this, 4000);
        }
    };

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            secondsLeft--;
            if (secondsLeft <= 0) {
                continueButton.setEnabled(true);
                continueButton.setAlpha(1f);
                continueButton.setText("Yes, continue");
            } else {
                continueButton.setText("Yes, continue (" + secondsLeft + ")");
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        Stats stats = Stats.get(this);
        boolean preview = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        photos = Images.list(prefs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(22), dp(20), dp(22), dp(32));
        scroll.addView(col);

        // ---- header ----
        int[] today = stats.day(Stats.today());
        int limit = Prefs.limit(prefs);
        TextView header = text(preview ? "PREVIEW · DAILY LIMIT REACHED" : "DAILY LIMIT REACHED", 12, GOLD, false, false);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setLetterSpacing(0.18f);
        col.addView(header);
        col.addView(text(today[0] + (limit > 0 ? " of " + limit : "") + " reels today  ·  " + Prefs.time(today[2]) + " in Reels",
                13, MUTED, false, false));

        // ---- your photos ----
        if (!photos.isEmpty()) {
            photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(CARD);
            frame.setCornerRadius(dp(24));
            photo.setBackground(frame);
            photo.setClipToOutline(true);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.40f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            lp.topMargin = dp(18);
            photo.setLayoutParams(lp);
            col.addView(photo);
            showPhoto(0, false);
        }

        // ---- the quote ----
        TextView openMark = text("“", 64, GOLD, true, false);
        openMark.setPadding(0, dp(14), 0, 0);
        openMark.setIncludeFontPadding(false);
        col.addView(openMark);
        TextView quote = text(QUOTE, 25, Color.WHITE, true, true);
        quote.setLineSpacing(dp(4), 1f);
        quote.setPadding(dp(6), 0, dp(6), 0);
        col.addView(quote);
        View rule = new View(this);
        rule.setBackgroundColor(GOLD);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(dp(56), dp(2));
        ruleLp.topMargin = dp(18);
        ruleLp.bottomMargin = dp(6);
        rule.setLayoutParams(ruleLp);
        col.addView(rule);

        // ---- this week vs last week ----
        int[] w = stats.weekComparison();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(CARD);
        cardBg.setCornerRadius(dp(18));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(16);
        card.setLayoutParams(cardLp);
        TextView cardTitle = text("THIS WEEK VS LAST WEEK", 12, GOLD, false, false);
        cardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cardTitle.setLetterSpacing(0.08f);
        cardTitle.setGravity(Gravity.START);
        card.addView(cardTitle);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(6));
        row.addView(cell(String.valueOf(w[0]), "this week"));
        row.addView(cell(String.valueOf(w[1]), "last week, same days"));
        row.addView(cell(String.valueOf(w[2]), "all of last week"));
        card.addView(row);
        TextView diff = text(Prefs.weekDiff(w[0], w[1]), 14, w[0] > w[1] ? ROSE : w[0] < w[1] ? SAGE : MUTED, false, false);
        diff.setGravity(Gravity.START);
        card.addView(diff);
        col.addView(card);

        // ---- the question ----
        TextView ask = text("Are you sure you want to continue?", 22, Color.WHITE, true, false);
        ask.setPadding(0, dp(26), 0, dp(6));
        col.addView(ask);

        Button done = new Button(this);
        done.setText("No, I'm done for now");
        done.setAllCaps(false);
        done.setTextColor(Color.WHITE);
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setColor(BURGUNDY);
        doneBg.setCornerRadius(dp(14));
        done.setBackground(doneBg);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        doneLp.topMargin = dp(12);
        done.setLayoutParams(doneLp);
        done.setOnClickListener(v -> stopScrolling());
        col.addView(done);

        continueButton = new Button(this);
        continueButton.setAllCaps(false);
        continueButton.setTextColor(MUTED);
        continueButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable contBg = new GradientDrawable();
        contBg.setColor(0x00000000);
        contBg.setStroke(dp(1), 0xFF4A4850);
        contBg.setCornerRadius(dp(14));
        continueButton.setBackground(contBg);
        LinearLayout.LayoutParams contLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        contLp.topMargin = dp(10);
        continueButton.setLayoutParams(contLp);
        continueButton.setText("Yes, continue (" + secondsLeft + ")");
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.5f);
        continueButton.setOnClickListener(v -> finish());
        col.addView(continueButton);

        if (photos.isEmpty()) {
            TextView hint = text("Tip: add your own photos for this screen in Reels Counter → Break screen.", 12, MUTED, false, false);
            hint.setPadding(0, dp(18), 0, 0);
            col.addView(hint);
        }

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(slideshow);
        handler.postDelayed(slideshow, 4000);
        if (secondsLeft > 0) {
            handler.removeCallbacks(countdown);
            handler.postDelayed(countdown, 1000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(slideshow);
        handler.removeCallbacks(countdown);
    }

    @Override
    public void onBackPressed() {
        stopScrolling();
    }

    /** "No, I'm done": go to the home screen. */
    private void stopScrolling() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (Exception ignored) {
        }
        finish();
    }

    private void showPhoto(int index, boolean fade) {
        if (photo == null || photos.isEmpty()) return;
        Uri uri = photos.get(index % photos.size());
        int maxPx = getResources().getDisplayMetrics().widthPixels;
        new Thread(() -> {
            Bitmap bmp = Images.load(this, uri, maxPx);
            runOnUiThread(() -> {
                if (bmp == null || isFinishing()) return;
                photo.setImageBitmap(bmp);
                if (fade) {
                    photo.setAlpha(0f);
                    photo.animate().alpha(1f).setDuration(700);
                }
            });
        }).start();
    }

    private LinearLayout cell(String value, String caption) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value, 22, Color.WHITE, false, false);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.START);
        c.addView(v);
        TextView cap = text(caption, 11, MUTED, false, false);
        cap.setGravity(Gravity.START);
        c.addView(cap);
        return c;
    }

    private TextView text(String s, float sp, int color, boolean serif, boolean italic) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (serif) t.setTypeface(Typeface.create("serif", italic ? Typeface.ITALIC : Typeface.NORMAL));
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
