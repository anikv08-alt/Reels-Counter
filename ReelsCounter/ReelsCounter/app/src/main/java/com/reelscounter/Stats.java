package com.reelscounter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Calendar;
import java.util.Locale;

/** Stores one row per day: reels scrolled, reels watched, seconds spent in Reels. */
public class Stats extends SQLiteOpenHelper {

    private static Stats instance;

    static synchronized Stats get(Context context) {
        if (instance == null) instance = new Stats(context.getApplicationContext());
        return instance;
    }

    private Stats(Context context) {
        super(context, "reels.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE days (day TEXT PRIMARY KEY, scrolled INTEGER NOT NULL DEFAULT 0, "
                + "watched INTEGER NOT NULL DEFAULT 0, seconds INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    synchronized void add(String day, int scrolled, int watched, int seconds) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO days(day) VALUES(?)", new Object[]{day});
        db.execSQL("UPDATE days SET scrolled = scrolled + ?, watched = watched + ?, seconds = seconds + ? WHERE day = ?",
                new Object[]{scrolled, watched, seconds, day});
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
