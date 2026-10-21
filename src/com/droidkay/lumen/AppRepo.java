package com.droidkay.lumen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Finds every launchable app on the Fire TV and loads its banner, icon and card colour. */
final class AppRepo {
    private AppRepo() {}

    static final String AMAZON_HOME = "com.amazon.tv.launcher";

    static final class App {
        final String pkg;
        final String label;
        final Drawable icon;
        final Drawable banner;
        final int tint;
        final boolean system;

        App(String pkg, String label, Drawable icon, Drawable banner, int tint, boolean system) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.banner = banner;
            this.tint = tint;
            this.system = system;
        }
    }

    static List<App> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String self = ctx.getPackageName();
        LinkedHashMap<String, ResolveInfo> found = new LinkedHashMap<String, ResolveInfo>();

        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> res;
            try {
                res = pm.queryIntentActivities(q, 0);
            } catch (Exception e) {
                continue;
            }
            if (res == null) continue;
            for (ResolveInfo ri : res) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(self) || pkg.equals(AMAZON_HOME)) continue;
                if (!found.containsKey(pkg)) found.put(pkg, ri);
            }
        }

        ArrayList<App> apps = new ArrayList<App>();
        for (ResolveInfo ri : found.values()) {
            try {
                apps.add(build(pm, ri));
            } catch (Exception ignored) {
                // A single broken app should never break the home screen.
            }
        }

        final Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return col.compare(a.label, b.label);
            }
        });
        return apps;
    }

    private static App build(PackageManager pm, ResolveInfo ri) {
        ActivityInfo ai = ri.activityInfo;
        String label = String.valueOf(ri.loadLabel(pm)).trim();
        if (label.length() == 0) label = ai.packageName;

        Drawable banner = null;
        try {
            banner = ai.loadBanner(pm);
            if (banner == null && ai.applicationInfo != null) banner = ai.applicationInfo.loadBanner(pm);
        } catch (Exception ignored) {
        }

        Drawable icon = null;
        try {
            icon = ri.loadIcon(pm);
        } catch (Exception ignored) {
        }

        boolean system = ai.applicationInfo != null
                && (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (ai.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;

        return new App(ai.packageName, label, icon, banner, tintFor(icon), system);
    }

    /** Deep, rich card colour taken from the app icon's main colour. */
    private static int tintFor(Drawable icon) {
        int fallback = 0xFF1E2230;
        if (icon == null) return fallback;
        try {
            int n = 24;
            Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            icon.setBounds(0, 0, n, n);
            icon.draw(cv);
            double r = 0, g = 0, b = 0, wsum = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int px = bmp.getPixel(x, y);
                    int a = Color.alpha(px);
                    if (a < 128) continue;
                    Color.colorToHSV(px, hsv);
                    double w = 0.15 + hsv[1] * hsv[2];
                    r += Color.red(px) * w;
                    g += Color.green(px) * w;
                    b += Color.blue(px) * w;
                    wsum += w;
                }
            }
            bmp.recycle();
            if (wsum == 0) return fallback;
            int avg = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (b / wsum));
            Color.colorToHSV(avg, hsv);
            hsv[1] = Math.min(hsv[1], 0.65f);
            hsv[2] = 0.30f;
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Popular streaming apps, used only to pre-fill favourites on first run. */
    static final String[] DEFAULT_FAVS = {
        "com.netflix.ninja",
        "com.amazon.firetv.youtube",
        "com.google.android.youtube.tv",
        "com.amazon.avod",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.showmax.app",
        "com.dstv.now.androidtv",
        "com.apple.atve.amazon.appletv",
        "com.plexapp.android",
        "org.xbmc.kodi",
        "com.esaba.downloader",
    };
}
