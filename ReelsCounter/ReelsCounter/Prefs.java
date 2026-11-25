package com.reelscounter;

import android.content.SharedPreferences;

/** Settings shared by the app screen and the counter service. */
final class Prefs {
    static final String NAME = "settings";

    static int threshold(SharedPreferences p) { return p.getInt("threshold", 3); }

    static int limit(SharedPreferences p) { return p.getInt("limit", 0); }

    static boolean bubbleOn(SharedPreferences p) { return p.getBoolean("bubbleOn", true); }

    static boolean onlyInInstagram(SharedPreferences p) { return p.getBoolean("onlyIg", true); }

    static boolean breakScreen(SharedPreferences p) { return p.getBoolean("breakScreen", true); }

    static String hourName(int h) {
        if (h == 0) return "12 AM";
        if (h < 12) return h + " AM";
        if (h == 12) return "12 PM";
        return (h - 12) + " PM";
    }

    static String hourRange(int h) {
        return hourName(h) + " – " + hourName((h + 1) % 24);
    }

    /** "↑ 70 more reels than at this point last week (+28%)" and similar. */
    static String weekDiff(int now, int before) {
        if (now == 0 && before == 0) return "No reels this week or at this point last week.";
        if (before == 0) return "↑ " + now + " reels this week. Last week you had none by this point.";
        int diff = now - before;
        int pct = Math.round(Math.abs(diff) * 100f / before);
        if (diff > 0) return "↑ " + diff + " more reels than at this point last week (+" + pct + "%)";
        if (diff < 0) return "↓ " + (-diff) + " fewer reels than at this point last week (−" + pct + "%)";
        return "Exactly the same as at this point last week";
    }

    static String time(int seconds) {
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
