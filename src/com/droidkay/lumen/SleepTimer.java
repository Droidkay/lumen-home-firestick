package com.droidkay.lumen;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Sleep timer: an alarm that puts the TV to sleep through the Home-button service when it fires. */
final class SleepTimer {
    private SleepTimer() {}

    static final int[] MINUTES = {15, 30, 45, 60, 90, 120};
    private static final String KEY = "sleepAt";

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, SleepReceiver.class);
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void set(Context c, int minutes) {
        long at = System.currentTimeMillis() + minutes * 60_000L;
        Prefs.sp(c).edit().putLong(KEY, at).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC_WAKEUP, at, pending(c));
    }

    static void cancel(Context c) {
        Prefs.sp(c).edit().remove(KEY).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c));
    }

    /** Minutes left (rounded up), or -1 when no timer is running. */
    static int remainingMinutes(Context c) {
        long at = Prefs.sp(c).getLong(KEY, 0L);
        if (at <= 0) return -1;
        long left = at - System.currentTimeMillis();
        if (left <= 0) {
            Prefs.sp(c).edit().remove(KEY).apply();
            return -1;
        }
        return (int) ((left + 59_999L) / 60_000L);
    }

    static String label(int minutes) {
        if (minutes >= 60 && minutes % 60 == 0) return (minutes / 60) + (minutes == 60 ? " hour" : " hours");
        return minutes + " min";
    }
}
