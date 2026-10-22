package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** App folders, saved on the device. A folder tile shows mini previews of up to four of its apps. */
final class Folders {
    private Folders() {}

    static final String PREFIX = "folder:";
    static final String[] SUGGESTED = {"Streaming", "Music", "Utilities", "Games", "Kids", "Sports", "News", "Tools"};

    /** name -> package list, in the order folders were made. */
    static LinkedHashMap<String, List<String>> load(Context c) {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        String raw = Prefs.getStr(c, "folders", "");
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            int bar = line.indexOf('|');
            if (bar <= 0) continue;
            String name = line.substring(0, bar);
            ArrayList<String> pkgs = new ArrayList<String>();
            for (String p : line.substring(bar + 1).split(";")) if (!p.isEmpty() && !pkgs.contains(p)) pkgs.add(p);
            out.put(name, pkgs);
        }
        return out;
    }

    static void save(Context c, Map<String, List<String>> f) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<String>> e : f.entrySet()) {
            if (b.length() > 0) b.append('\n');
            b.append(clean(e.getKey())).append('|');
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) b.append(';');
                b.append(e.getValue().get(i));
            }
        }
        Prefs.setStr(c, "folders", b.toString());
    }

    static String clean(String name) {
        String n = name.replace('|', ' ').replace('\n', ' ').trim();
        return n.length() > 24 ? n.substring(0, 24) : n;
    }

    /** Package -> folder name for every app that sits in a folder. */
    static HashMap<String, String> owners(Context c) {
        HashMap<String, String> m = new HashMap<String, String>();
        for (Map.Entry<String, List<String>> e : load(c).entrySet()) for (String p : e.getValue()) m.put(p, e.getKey());
        return m;
    }

    static void add(Context c, String folder, String pkg) {
        LinkedHashMap<String, List<String>> f = load(c);
        for (List<String> l : f.values()) l.remove(pkg); // an app lives in one folder
        String name = clean(folder);
        List<String> l = f.get(name);
        if (l == null) {
            l = new ArrayList<String>();
            f.put(name, l);
        }
        l.add(pkg);
        save(c, f);
    }

    static void remove(Context c, String pkg) {
        LinkedHashMap<String, List<String>> f = load(c);
        for (List<String> l : f.values()) l.remove(pkg);
        // drop folders that became empty
        ArrayList<String> empty = new ArrayList<String>();
        for (Map.Entry<String, List<String>> e : f.entrySet()) if (e.getValue().isEmpty()) empty.add(e.getKey());
        for (String k : empty) f.remove(k);
        save(c, f);
    }

    static void delete(Context c, String folder) {
        LinkedHashMap<String, List<String>> f = load(c);
        f.remove(folder);
        save(c, f);
    }

    static void rename(Context c, String from, String to) {
        LinkedHashMap<String, List<String>> f = load(c);
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        String n = clean(to);
        for (Map.Entry<String, List<String>> e : f.entrySet()) {
            if (e.getKey().equals(from)) {
                List<String> merged = out.containsKey(n) ? out.get(n) : new ArrayList<String>();
                merged.addAll(e.getValue());
                out.put(n, merged);
            } else if (!out.containsKey(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            } else {
                out.get(e.getKey()).addAll(e.getValue());
            }
        }
        save(c, out);
    }

    /** A pseudo-app so a folder can be shown with the normal tile. */
    static AppRepo.App tile(Context c, String name, List<AppRepo.App> members, int wPx, int hPx) {
        Bitmap b = Bitmap.createBitmap(Math.max(8, wPx), Math.max(8, hPx), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, hPx, 0xFF3A3F55, 0xFF1C1F2C, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, wPx, hPx, p);
        p.setShader(null);

        // 2 x 2 previews like iPhone/tvOS folders
        float pad = hPx * 0.10f, gap = hPx * 0.06f;
        float cellW = (wPx - pad * 2 - gap) / 2f, cellH = (hPx - pad * 2 - gap) / 2f;
        float r = cellH * 0.18f;
        RectF rf = new RectF();
        for (int i = 0; i < 4; i++) {
            float x = pad + (i % 2) * (cellW + gap), y = pad + (i / 2) * (cellH + gap);
            rf.set(x, y, x + cellW, y + cellH);
            if (i < members.size()) {
                AppRepo.App a = members.get(i);
                p.setColor(a.tint);
                cv.drawRoundRect(rf, r, r, p);
                int save = cv.save();
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(rf, r, r, android.graphics.Path.Direction.CW);
                cv.clipPath(clip);
                Drawable src = a.banner != null ? a.banner : a.icon;
                // draw a copy so the app's own tile keeps its drawable bounds
                Drawable d = src == null ? null
                        : src.getConstantState() != null ? src.getConstantState().newDrawable(c.getResources()).mutate() : src;
                if (d != null) {
                    if (a.banner != null) {
                        d.setBounds((int) x, (int) y, (int) (x + cellW), (int) (y + cellH));
                    } else {
                        float s = cellH * 0.7f;
                        d.setBounds((int) (x + (cellW - s) / 2), (int) (y + (cellH - s) / 2),
                                (int) (x + (cellW + s) / 2), (int) (y + (cellH + s) / 2));
                    }
                    d.draw(cv);
                }
                cv.restoreToCount(save);
            } else {
                p.setColor(0x1FFFFFFF);
                cv.drawRoundRect(rf, r, r, p);
            }
        }
        Drawable banner = new BitmapDrawable(c.getResources(), b);
        Drawable icon = members.isEmpty() ? null : members.get(0).icon;
        return new AppRepo.App(PREFIX + name, name, icon, banner, 0xFF262A3A, true);
    }

    static boolean isFolder(AppRepo.App a) {
        return a.pkg.startsWith(PREFIX);
    }

    static String nameOf(AppRepo.App a) {
        return a.pkg.substring(PREFIX.length());
    }
}
