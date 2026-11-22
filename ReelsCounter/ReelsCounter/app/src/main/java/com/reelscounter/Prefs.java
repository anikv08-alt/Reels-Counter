package com.reelscounter;

import android.content.SharedPreferences;

/** Settings shared by the app screen and the counter service. */
final class Prefs {
    static final String NAME = "settings";

    static int threshold(SharedPreferences p) { return p.getInt("threshold", 3); }

    static int limit(SharedPreferences p) { return p.getInt("limit", 0); }

    static boolean bubbleOn(SharedPreferences p) { return p.getBoolean("bubbleOn", true); }

    static boolean onlyInInstagram(SharedPreferences p) { return p.getBoolean("onlyIg", true); }

    static String time(int seconds) {
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
