package com.droidkay.lumen;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Small wrapper around SharedPreferences. Everything stays on the device. */
final class Prefs {
    private Prefs() {}

    static final String FAVS = "favs";
    static final String HIDDEN = "hidden";
    static final String RECENTS = "recents";

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("lumen", Context.MODE_PRIVATE);
    }

    static boolean has(Context c, String key) {
        return sp(c).contains(key);
    }

    static List<String> list(Context c, String key) {
        ArrayList<String> out = new ArrayList<String>();
        String s = sp(c).getString(key, null);
        if (s == null || s.length() == 0) return out;
        for (String p : s.split(",")) {
            if (p.length() > 0 && !out.contains(p)) out.add(p);
        }
        return out;
    }

    static void putList(Context c, String key, List<String> l) {
        sp(c).edit().putString(key, TextUtils.join(",", l)).apply();
    }

    static void remove(Context c, String key) {
        sp(c).edit().remove(key).apply();
    }

    static int getInt(Context c, String key, int def) {
        return sp(c).getInt(key, def);
    }

    static void setInt(Context c, String key, int v) {
        sp(c).edit().putInt(key, v).apply();
    }

    static boolean getBool(Context c, String key, boolean def) {
        return sp(c).getBoolean(key, def);
    }

    static void setBool(Context c, String key, boolean v) {
        sp(c).edit().putBoolean(key, v).apply();
    }

    static String getStr(Context c, String key, String def) {
        return sp(c).getString(key, def);
    }

    static void setStr(Context c, String key, String v) {
        sp(c).edit().putString(key, v).apply();
    }

    /** Accent / gradient theme. */
    static int theme(Context c) {
        int t = getInt(c, "theme", 0);
        return (t < 0 || t >= Palette.ALL.length) ? 0 : t;
    }

    static void setTheme(Context c, int t) {
        setInt(c, "theme", t);
    }

    /** Wallpaper id: "gradient", "scene_peaks", "scene_aurora", "scene_nebula", "scene_ocean" or "custom". */
    static String wallpaper(Context c) {
        return getStr(c, "wallpaper", "scene_peaks");
    }

    static void setWallpaper(Context c, String w) {
        setStr(c, "wallpaper", w);
    }

    /** Dark overlay strength 0..3 (Off, Light, Medium, Dark). */
    static int dim(Context c) {
        int d = getInt(c, "dim", 2);
        return d < 0 || d > 3 ? 2 : d;
    }

    /** -1 = follow the Fire TV setting, 0 = 12-hour, 1 = 24-hour. */
    static int clockMode(Context c) {
        return getInt(c, "clock", -1);
    }

    static void setClockMode(Context c, int m) {
        setInt(c, "clock", m);
    }

    static boolean showRecent(Context c) {
        return getBool(c, "recentRow", true);
    }

    static void setShowRecent(Context c, boolean b) {
        setBool(c, "recentRow", b);
    }

    /** App names under tiles: false = only on the selected app (Apple TV style), true = always. */
    static boolean alwaysLabels(Context c) {
        return getBool(c, "alwaysLabels", false);
    }

    static boolean widget(Context c, String name) {
        return getBool(c, "w_" + name, true);
    }

    static void setWidget(Context c, String name, boolean on) {
        setBool(c, "w_" + name, on);
    }

    static int city(Context c) {
        int i = getInt(c, "city", 0);
        return i < 0 || i >= Weather.CITIES.length ? 0 : i;
    }

    static boolean takeover(Context c) {
        return getBool(c, "takeover", true);
    }

    static void setTakeover(Context c, boolean b) {
        setBool(c, "takeover", b);
    }

    static long pauseUntil(Context c) {
        return sp(c).getLong("pauseUntil", 0L);
    }

    static void setPauseUntil(Context c, long t) {
        sp(c).edit().putLong("pauseUntil", t).apply();
    }
}
