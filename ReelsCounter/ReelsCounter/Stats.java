package com.reelscounter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Calendar;
import java.util.Locale;

/** Stores one row per day (reels scrolled, watched, seconds in Reels) and one row per hour of each day. */
public class Stats extends SQLiteOpenHelper {

    private static Stats instance;

    static synchronized Stats get(Context context) {
        if (instance == null) instance = new Stats(context.getApplicationContext());
        return instance;
    }

    private Stats(Context context) {
        super(context, "reels.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE days (day TEXT PRIMARY KEY, scrolled INTEGER NOT NULL DEFAULT 0, "
                + "watched INTEGER NOT NULL DEFAULT 0, seconds INTEGER NOT NULL DEFAULT 0)");
        createHours(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createHours(db);
    }

    private static void createHours(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hours (day TEXT NOT NULL, hour INTEGER NOT NULL, "
                + "scrolled INTEGER NOT NULL DEFAULT 0, seconds INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(day, hour))");
    }

    /** Adds to today's totals and to the current hour. */
    synchronized void record(int scrolled, int watched, int seconds) {
        Calendar now = Calendar.getInstance();
        String day = key(now);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO days(day) VALUES(?)", new Object[]{day});
        db.execSQL("UPDATE days SET scrolled = scrolled + ?, watched = watched + ?, seconds = seconds + ? WHERE day = ?",
                new Object[]{scrolled, watched, seconds, day});
        if (scrolled > 0 || seconds > 0) {
            db.execSQL("INSERT OR IGNORE INTO hours(day, hour) VALUES(?, ?)", new Object[]{day, hour});
            db.execSQL("UPDATE hours SET scrolled = scrolled + ?, seconds = seconds + ? WHERE day = ? AND hour = ?",
                    new Object[]{scrolled, seconds, day, hour});
        }
    }

    /** Reels scrolled in each hour of the day (0-23), summed over days from..to. */
    synchronized int[] hourly(String from, String to) {
        int[] result = new int[24];
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT hour, SUM(scrolled) FROM hours WHERE day >= ? AND day <= ? GROUP BY hour",
                new String[]{from, to});
        try {
            while (c.moveToNext()) {
                int h = c.getInt(0);
                if (h >= 0 && h < 24) result[h] = c.getInt(1);
            }
        } finally {
            c.close();
        }
        return result;
    }

    /** Index of the busiest hour, or -1 if there is no data. */
    static int peak(int[] hours) {
        int best = -1;
        for (int i = 0; i < hours.length; i++) {
            if (hours[i] > 0 && (best < 0 || hours[i] > hours[best])) best = i;
        }
        return best;
    }

    /** {this week so far, last week up to the same weekday, all of last week}. */
    int[] weekComparison() {
        int dow = dayOfWeek();
        int thisWeek = sum(weekStart(), today())[0];
        int lastSoFar = sum(daysAgo(dow - 1 + 7), daysAgo(7))[0];
        int lastFull = sum(daysAgo(dow - 1 + 7), daysAgo(dow))[0];
        return new int[]{thisWeek, lastSoFar, lastFull};
    }

    /** Returns {scrolled, watched, seconds, daysWithData} for days between from and to (inclusive). */
    synchronized int[] sum(String from, String to) {
        int[] result = new int[4];
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(scrolled),0), COALESCE(SUM(watched),0), COALESCE(SUM(seconds),0), COUNT(*) "
                        + "FROM days WHERE day >= ? AND day <= ?", new String[]{from, to});
        try {
            if (c.moveToFirst()) {
                for (int i = 0; i < 4; i++) result[i] = c.getInt(i);
            }
        } finally {
            c.close();
        }
        return result;
    }

    int[] day(String day) {
        return sum(day, day);
    }

    /** Reels scrolled on each of the last n days, oldest first (last item = today). */
    int[] lastDays(int n) {
        int[] values = new int[n];
        for (int i = 0; i < n; i++) values[i] = day(daysAgo(n - 1 - i))[0];
        return values;
    }

    // ---- dates ----

    static String key(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String today() {
        return key(Calendar.getInstance());
    }

    static String daysAgo(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -n);
        return key(c);
    }

    /** 1 on Monday ... 7 on Sunday. */
    static int dayOfWeek() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // Sunday = 1
        return (dow + 5) % 7 + 1;
    }

    static String weekStart() {
        return daysAgo(dayOfWeek() - 1);
    }

    static String monthStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        return key(c);
    }

    static int dayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }
}
