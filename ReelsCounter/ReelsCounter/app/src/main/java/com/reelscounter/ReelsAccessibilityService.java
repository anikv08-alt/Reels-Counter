package com.reelscounter;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Watches Instagram, notices when the Reels viewer is open and when you swipe to a new reel,
 * and shows a floating counter bubble.
 */
public class ReelsAccessibilityService extends AccessibilityService {

    static final String INSTAGRAM = "com.instagram.android";

    // ---------- detection log (shown in the app, helps fix counting if Instagram changes) ----------

    private static final ArrayList<String> LOG = new ArrayList<>();

    static synchronized void log(String line) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        LOG.add(t + "  " + line);
        while (LOG.size() > 150) LOG.remove(0);
    }

    static synchronized String getLog() {
        if (LOG.isEmpty()) {
            return "Nothing yet.\n\nMake sure the counter is ON, open Instagram Reels, scroll through 5 reels, then come back here.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = LOG.size() - 1; i >= 0; i--) sb.append(LOG.get(i)).append('\n');
        return sb.toString();
    }

    // ---------- state ----------

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Stats stats;
    private SharedPreferences prefs;
    private WindowManager windowManager;
    private PowerManager powerManager;

    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private boolean bubbleAdded = false;
    private boolean bubbleExpanded = true;
    private int bubbleColor = 0;

    private boolean inInstagram = false;
    private boolean inReels = false;
    private boolean screenDetectionWorks = false;
    private long lastReelsSignalAt = 0;

    private String sourceKey = "";
    private int maxIndex = -1;
    private int deltaSum = 0;
    private String countMethod = "";
    private long lastScrollEventAt = 0;
    private long lastCountAt = 0;
    private long lastIgnoredLogAt = 0;

    private long reelStartedAt = 0;
    private boolean reelCreditedAsWatched = false;
    private int pendingSeconds = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                onTick();
            } catch (Exception e) {
                log("tick error: " + e);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                    if ("resetPosition".equals(key) && bubbleParams != null) {
                        bubbleParams.x = dp(16);
                        bubbleParams.y = dp(120);
                        savePosition();
                        if (bubbleAdded) {
                            try {
                                windowManager.updateViewLayout(bubble, bubbleParams);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    refreshBubble();
                }
            };

    // ---------- lifecycle ----------

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        stats = Stats.get(this);
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        createBubble();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        handler.removeCallbacks(tick);
        handler.post(tick);
        log("Counter started");
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (stats != null) flushSeconds();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        removeBubble();
        super.onDestroy();
    }

    // ---------- events ----------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (stats == null || event == null) return;
        try {
            handleEvent(event);
        } catch (Exception e) {
            log("event error: " + e);
        }
    }

    private void handleEvent(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (INSTAGRAM.equals(pkg)) setInInstagram(true);
            else if (!isIgnorable(pkg)) setInInstagram(false);
            return;
        }
        if (type != AccessibilityEvent.TYPE_VIEW_SCROLLED || !INSTAGRAM.equals(pkg)) return;
        if (!inInstagram) setInInstagram(true);
        handleScroll(event);
    }

    private void handleScroll(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        int screenH = getResources().getDisplayMetrics().heightPixels;

        AccessibilityNodeInfo src = event.getSource();
        String id = null;
        Rect r = new Rect();
        if (src != null) {
            id = src.getViewIdResourceName();
            src.getBoundsInScreen(r);
        }
        int h = r.height();
        boolean reelsId = id != null && id.toLowerCase(Locale.US).contains("clips") && h >= screenH * 0.6f;
        boolean fullScreen = h >= screenH * 0.75f && r.top <= screenH * 0.12f;
        boolean qualifies = reelsId || (inReels && screenDetectionWorks && fullScreen);

        if (!qualifies) {
            if (fullScreen && now - lastIgnoredLogAt > 2000) {
                lastIgnoredLogAt = now;
                log("· not a reel  " + describe(event, id, r, screenH));
            }
            return;
        }

        lastReelsSignalAt = now;
        String key = shortId(id) + "|" + shortName(event.getClassName());

        if (!inReels) {
            enterReels(now, "scroll on " + shortId(id)); // counts the reel now on screen
            checkNextReel(event, key, h, now);           // just sets the starting point
            deltaSum = -h;                               // the rest of this swipe is the same reel
            log("✅ +1 (reels opened)  " + describe(event, id, r, screenH));
            return;
        }

        boolean next = checkNextReel(event, key, h, now);
        if (next) onNewReel(now);
        log((next ? "✅ +1 " : "· ") + countMethod + "  " + describe(event, id, r, screenH));
    }

    /** Decides whether this scroll event means a new reel came onto the screen. */
    private boolean checkNextReel(AccessibilityEvent event, String key, int h, long now) {
        if (!key.equals(sourceKey)) {
            sourceKey = key;
            maxIndex = -1;
            deltaSum = 0;
        }
        int from = event.getFromIndex();
        int dy = Build.VERSION.SDK_INT >= 28 ? event.getScrollDeltaY() : -1;
        boolean next = false;

        if (from >= 0 && event.getItemCount() > 0) {
            // Best case: the list tells us which reel number is showing.
            countMethod = "index";
            if (maxIndex < 0 || from < maxIndex - 50) {
                maxIndex = from;
            } else if (from > maxIndex) {
                maxIndex = from;
                next = true;
            }
        } else if (dy > 0) {
            // Next best: add up how far the screen moved; one screen height = one reel.
            countMethod = "distance";
            deltaSum += dy;
            if (deltaSum >= h / 2) {
                deltaSum -= h;
                next = true;
            }
        } else if (dy < -1) {
            countMethod = "back";
            deltaSum = 0;
        } else {
            // Last resort: one burst of scrolling = one swipe.
            countMethod = "burst";
            next = now - lastScrollEventAt > 700;
        }
        lastScrollEventAt = now;
        if (next && now - lastCountAt < 300) next = false;
        return next;
    }

    // ---------- every second ----------

    private void onTick() {
        long now = System.currentTimeMillis();
        boolean screenOn = powerManager == null || powerManager.isInteractive();

        if (!screenOn) {
            setInInstagram(false);
        } else {
            AccessibilityNodeInfo root = null;
            try {
                root = getRootInActiveWindow();
            } catch (Exception ignored) {
            }
            if (root != null && root.getPackageName() != null) {
                String p = root.getPackageName().toString();
                if (INSTAGRAM.equals(p)) setInInstagram(true);
                else if (!isIgnorable(p)) setInInstagram(false);
            }
        }

        if (inInstagram) {
            String found = findReelsScreen();
            if (found != null) {
                if (!screenDetectionWorks) log("Reels screen recognised by: " + found);
                screenDetectionWorks = true;
                lastReelsSignalAt = now;
                if (!inReels) enterReels(now, "screen " + found);
            } else if (inReels) {
                long grace = screenDetectionWorks ? 3000 : 60000;
                if (now - lastReelsSignalAt > grace) leaveReels();
            }
        }

        if (inReels) {
            pendingSeconds++;
            if (reelStartedAt > 0 && !reelCreditedAsWatched
                    && now - reelStartedAt >= Prefs.threshold(prefs) * 1000L) {
                reelCreditedAsWatched = true;
                stats.add(Stats.today(), 0, 1, 0);
            }
            if (pendingSeconds >= 10) flushSeconds();
        }
        refreshBubble();
    }

    private void setInInstagram(boolean value) {
        if (inInstagram == value) return;
        inInstagram = value;
        if (!value && inReels) leaveReels();
        refreshBubble();
    }

    private boolean isIgnorable(String pkg) {
        return pkg.equals(getPackageName())
                || pkg.equals("com.android.systemui")
                || pkg.contains("inputmethod")
                || pkg.contains("honeyboard")
                || pkg.contains("keyboard")
                || pkg.contains("cocktailbar");
    }

    private void enterReels(long now, String why) {
        inReels = true;
        sourceKey = "";
        maxIndex = -1;
        deltaSum = 0;
        lastReelsSignalAt = now;
        log("▶ Reels opened (" + why + ")");
        onNewReel(now);
    }

    private void leaveReels() {
        inReels = false;
        reelStartedAt = 0;
        sourceKey = "";
        maxIndex = -1;
        deltaSum = 0;
        flushSeconds();
        log("■ Reels closed");
        refreshBubble();
    }

    private void onNewReel(long now) {
        lastCountAt = now;
        reelStartedAt = now;
        reelCreditedAsWatched = false;
        String today = Stats.today();
        stats.add(today, 1, 0, 0);
        int scrolled = stats.day(today)[0];
        int limit = Prefs.limit(prefs);
        if (limit > 0 && scrolled >= limit && (scrolled - limit) % 10 == 0) warnLimit(scrolled, limit);
        refreshBubble();
    }

    private void flushSeconds() {
        if (pendingSeconds > 0) {
            stats.add(Stats.today(), 0, 0, pendingSeconds);
            pendingSeconds = 0;
        }
    }

    private void warnLimit(int scrolled, int limit) {
        String msg = scrolled == limit
                ? "You just hit your daily limit of " + limit + " reels. Time for a break?"
                : scrolled + " reels today. That's " + (scrolled - limit) + " over your limit.";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    // ---------- finding the Reels viewer on screen ----------

    private String findReelsScreen() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (Exception e) {
            return null;
        }
        if (windows == null) return null;
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || root.getPackageName() == null) continue;
            if (!INSTAGRAM.equals(root.getPackageName().toString())) continue;
            String hit = searchReelsNode(root);
            if (hit != null) return hit;
        }
        return null;
    }

    private static String searchReelsNode(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < 800) {
            AccessibilityNodeInfo n = queue.poll();
            visited++;
            String id = n.getViewIdResourceName();
            if (id != null && isReelsScreenId(id) && n.isVisibleToUser()) return shortId(id);
            int count = n.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    static boolean isReelsScreenId(String id) {
        String s = id.toLowerCase(Locale.US);
        return s.contains("clips_viewer")
                || s.contains("root_clips")
                || s.contains("clips_video")
                || s.contains("clips_ufi")
                || s.contains("reel_viewer");
    }

    // ---------- floating bubble ----------

    private void createBubble() {
        bubble = new TextView(this);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bubble.setPadding(dp(12), dp(7), dp(12), dp(7));

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = prefs.getInt("x", dp(16));
        bubbleParams.y = prefs.getInt("y", dp(120));

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        if (moved) {
                            bubbleParams.x = startX + dx;
                            bubbleParams.y = startY + dy;
                            try {
                                windowManager.updateViewLayout(bubble, bubbleParams);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            savePosition();
                        } else {
                            bubbleExpanded = !bubbleExpanded;
                            refreshBubble();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void refreshBubble() {
        if (bubble == null || stats == null || prefs == null) return;
        boolean screenOn = powerManager == null || powerManager.isInteractive();
        boolean show = Prefs.bubbleOn(prefs) && screenOn && (!Prefs.onlyInInstagram(prefs) || inInstagram);

        if (show && !bubbleAdded) {
            try {
                windowManager.addView(bubble, bubbleParams);
                bubbleAdded = true;
            } catch (Exception e) {
                log("bubble error: " + e);
            }
        } else if (!show && bubbleAdded) {
            removeBubble();
        }
        if (!bubbleAdded) return;

        int[] d = stats.day(Stats.today());
        int scrolled = d[0];
        int watched = d[1];
        int seconds = d[2] + pendingSeconds;
        if (bubbleExpanded) {
            bubble.setText("🎞 " + scrolled + " reels today\n👀 " + watched + " watched · ⏱ " + Prefs.time(seconds));
        } else {
            bubble.setText("🎞 " + scrolled);
        }

        int limit = Prefs.limit(prefs);
        int color = 0xE62A292F;                                      // charcoal grey
        if (limit > 0 && scrolled >= limit) color = 0xF08C2341;      // burgundy: over limit
        else if (limit > 0 && scrolled >= limit * 0.8f) color = 0xF0A8823F; // gold: getting close
        if (color != bubbleColor) {
            bubbleColor = color;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(color);
            bg.setStroke(dp(2), 0xCCB23A5E); // burgundy outline
            bubble.setBackground(bg);
        }
    }

    private void removeBubble() {
        if (bubbleAdded) {
            try {
                windowManager.removeView(bubble);
            } catch (Exception ignored) {
            }
            bubbleAdded = false;
        }
    }

    private void savePosition() {
        prefs.edit().putInt("x", bubbleParams.x).putInt("y", bubbleParams.y).apply();
    }

    // ---------- helpers ----------

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    static String shortId(String id) {
        if (id == null) return "none";
        return id.substring(id.indexOf('/') + 1);
    }

    static String shortName(CharSequence cls) {
        if (cls == null) return "?";
        String s = cls.toString();
        return s.substring(s.lastIndexOf('.') + 1);
    }

    private static String describe(AccessibilityEvent e, String id, Rect r, int screenH) {
        int dy = Build.VERSION.SDK_INT >= 28 ? e.getScrollDeltaY() : -1;
        return "id=" + shortId(id) + " view=" + shortName(e.getClassName())
                + " idx=" + e.getFromIndex() + "-" + e.getToIndex() + "/" + e.getItemCount()
                + " dy=" + dy + " top=" + r.top + " h=" + r.height() + "/" + screenH;
    }
}
