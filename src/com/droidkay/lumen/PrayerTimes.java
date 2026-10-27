package com.droidkay.lumen;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Daily prayer times from the Aladhan API (free, no key) for the weather city, cached per day so
 * the widget shows instantly and works offline after the first fetch.
 */
final class PrayerTimes {
    private PrayerTimes() {}

    static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    /** Muslim World League calculation. */
    private static final int METHOD = 3;

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** Today's six times as "HH:mm" for this city, or null if none are cached yet. */
    static String[] cached(Context c, int city) {
        String s = Prefs.getStr(c, "prayerCache", null);
        if (s == null) return null;
        try {
            String[] p = s.split("\\|");
            if (!p[0].equals(today()) || Integer.parseInt(p[1]) != city) return null;
            String[] t = p[2].split(",");
            return t.length == NAMES.length ? t : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Network call: run on a background thread. Returns null on failure. */
    static String[] fetch(Context c, int cityIdx) {
        Weather.City city = Weather.CITIES[cityIdx];
        HttpURLConnection con = null;
        try {
            String url = String.format(Locale.US,
                    "https://api.aladhan.com/v1/timings/%s?latitude=%.4f&longitude=%.4f&method=%d",
                    new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date()), city.lat, city.lon, METHOD);
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "LumenHome/1.4");
            if (con.getResponseCode() != 200) return null;
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject t = new JSONObject(bo.toString("UTF-8")).getJSONObject("data").getJSONObject("timings");
            String[] out = new String[NAMES.length];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < NAMES.length; i++) {
                String v = t.getString(NAMES[i]);
                out[i] = v.length() >= 5 ? v.substring(0, 5) : v;
                if (i > 0) sb.append(',');
                sb.append(out[i]);
            }
            Prefs.setStr(c, "prayerCache", today() + "|" + cityIdx + "|" + sb);
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static int minutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Index of the next prayer (Sunrise is skipped, it isn't a prayer). If today's are all past,
     * returns 0 (Fajr) and the caller shows it as tomorrow's.
     */
    static int next(String[] t, Calendar now) {
        int cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int i = 0; i < t.length; i++) {
            if (i == 1) continue;
            int m = minutes(t[i]);
            if (m > cur) return i;
        }
        return 0;
    }

    static boolean isTomorrow(String[] t, Calendar now) {
        int cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int idx = next(t, now);
        return minutes(t[idx]) <= cur;
    }
}
