package com.droidkay.lumen;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String EXTRA_FROM_HOME = "fromHome";

    private static final String SEC_FAV = "fav", SEC_RECENT = "recent", SEC_ALL = "all";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final long PAUSE_MS = 5 * 60 * 1000L;
    private static final long WEATHER_MAX_AGE = 30 * 60 * 1000L;
    private static final int REQ_PICTURES = 41;

    private float density;
    private int screenW, screenH;
    private BackgroundView bgView;
    private ScrollView scroll;
    private LinearLayout sections;
    private WidgetBar widgets;
    private FrameLayout overlay;
    private boolean overlayShown;
    private View focusBeforeOverlay;
    private Bitmap blurred;

    private TextView timeTv, ampmTv, greetTv, dateTv;

    private List<AppRepo.App> apps = new ArrayList<AppRepo.App>();
    private boolean loaded, loading, reloadPending, dirty, destroyed, firstRender = true;
    private boolean weatherLoading;
    private int wallpaperGen;
    private String wantSec, wantPkg;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
            if (widgets != null) widgets.refresh(MainActivity.this);
            maybeRefreshWeather(false);
        }
    };

    private static final class Opt {
        final String text;
        final Runnable run;

        Opt(String text, Runnable run) {
            this.text = text;
            this.run = run;
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        density = getResources().getDisplayMetrics().density;
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        if (screenW > 1920) {
            screenH = screenH * 1920 / screenW;
            screenW = 1920;
        }

        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF05060B));
        buildUi();
        bgView.setPalette(Palette.ALL[Prefs.theme(this)]);
        bgView.setDim(Prefs.dim(this));
        loadWallpaper();
        updateClock();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(pkgReceiver, pf);

        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tickReceiver, tf);

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.setPauseUntil(this, 0L);
        updateClock();
        widgets.refresh(this);
        maybeRefreshWeather(false);
        if (dirty && loaded) {
            dirty = false;
            render(wantSec, wantPkg);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (overlayShown) hidePanel(false);
        if (!dirty) goTop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(tickReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean up = e.getAction() == KeyEvent.ACTION_UP;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { AppTile.lastDx = -1; AppTile.lastDy = 0; }
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { AppTile.lastDx = 1; AppTile.lastDy = 0; }
            else if (k == KeyEvent.KEYCODE_DPAD_UP) { AppTile.lastDx = 0; AppTile.lastDy = -1; }
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { AppTile.lastDx = 0; AppTile.lastDy = 1; }
        }
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (up) {
                if (overlayShown) hidePanel(true);
                else goTop();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU) {
            if (up) {
                if (overlayShown) {
                    hidePanel(true);
                } else {
                    View f = getCurrentFocus();
                    if (f instanceof AppTile) showAppOptions((AppTile) f, null);
                    else showCustomise(0);
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- layout

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);

        bgView = new BackgroundView(this);
        root.addView(bgView, new FrameLayout.LayoutParams(MATCH, MATCH));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(48));
        scroll.setSmoothScrollingEnabled(true);
        scroll.getViewTreeObserver().addOnScrollChangedListener(new android.view.ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                Glass.invalidateAll();
            }
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(26), dp(56), dp(56));
        content.setClipChildren(false);
        content.setClipToPadding(false);

        content.addView(buildTopBar(), new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(buildHero(), new LinearLayout.LayoutParams(MATCH, WRAP));

        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setClipChildren(false);
        sections.setClipToPadding(false);
        TextView loadingTv = text("Loading your apps…", 15, 0x99FFFFFF, "sans-serif");
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
        llp.topMargin = dp(40);
        sections.addView(loadingTv, llp);
        content.addView(sections, new LinearLayout.LayoutParams(MATCH, WRAP));

        scroll.addView(content, new ScrollView.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        root.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));

        setContentView(root);
    }

    private TextView text(String s, float sp, int color, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private View buildTopBar() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);

        dateTv = text("", 14, 0xCCFFFFFF, "sans-serif-medium");
        dateTv.setLetterSpacing(0.06f);
        dateTv.setShadowLayer(dp(6), 0, 0, 0x80000000);
        h.addView(dateTv, new LinearLayout.LayoutParams(0, WRAP, 1f));

        View home = chip(IconView.HOME, "Fire TV Home", new Runnable() {
            @Override
            public void run() {
                openAmazonHome();
            }
        });
        h.addView(home, new LinearLayout.LayoutParams(WRAP, WRAP));
        View cust = chip(IconView.SLIDERS, "Customise", new Runnable() {
            @Override
            public void run() {
                showCustomise(0);
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(WRAP, WRAP);
        cp.leftMargin = dp(10);
        h.addView(cust, cp);
        return h;
    }

    /** The "top shelf": big clock, greeting and the widget row. */
    private View buildHero() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(0, dp(18), 0, dp(6));

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clock.setBaselineAligned(true);
        timeTv = text("", 78, 0xFFFFFFFF, "sans-serif-thin");
        timeTv.setShadowLayer(dp(12), 0, dp(2), 0x66000000);
        timeTv.setLetterSpacing(-0.02f);
        clock.addView(timeTv, new LinearLayout.LayoutParams(WRAP, WRAP));
        ampmTv = text("", 20, 0xCCFFFFFF, "sans-serif-light");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(WRAP, WRAP);
        alp.leftMargin = dp(8);
        clock.addView(ampmTv, alp);
        v.addView(clock, new LinearLayout.LayoutParams(WRAP, WRAP));

        greetTv = text("", 20, 0xE6FFFFFF, "sans-serif-light");
        greetTv.setShadowLayer(dp(8), 0, dp(1), 0x80000000);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(WRAP, WRAP);
        gl.topMargin = dp(6);
        v.addView(greetTv, gl);

        widgets = new WidgetBar(this);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(WRAP, WRAP);
        wl.topMargin = dp(20);
        v.addView(widgets, wl);
        widgets.build(this);
        return v;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private StateListDrawable pressable(int normal, int focused, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, rounded(focused, radiusDp));
        s.addState(new int[] {android.R.attr.state_pressed}, rounded(focused, radiusDp));
        GradientDrawable n = rounded(normal, radiusDp);
        n.setStroke(Math.max(1, dp(0.75f)), 0x26FFFFFF);
        s.addState(new int[] {}, n);
        return s;
    }

    private View chip(int icon, String label, final Runnable action) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(15), dp(10), dp(17), dp(10));
        final Glass glass = Glass.apply(c, dp(21), density, Glass.TINT_DARK);
        c.setContentDescription(label);

        final IconView iv = new IconView(this, icon, dp(1.8f));
        iv.setColor(0xE6FFFFFF);
        c.addView(iv, new LinearLayout.LayoutParams(dp(17), dp(17)));
        final TextView t = text(label, 13, 0xE6FFFFFF, "sans-serif-medium");
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
        tl.leftMargin = dp(8);
        c.addView(t, tl);

        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                int col = f ? 0xFF0B0C10 : 0xE6FFFFFF;
                t.setTextColor(col);
                iv.setColor(col);
                glass.setGlassTint(f ? 0xEBFFFFFF : Glass.TINT_DARK);
                float s = f ? 1.08f : 1f;
                c.animate().scaleX(s).scaleY(s).setDuration(140).start();
            }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return c;
    }

    // ---------------------------------------------------------------- clock & widgets

    private void updateClock() {
        if (timeTv == null) return;
        Calendar cal = Calendar.getInstance();
        Date d = cal.getTime();
        Locale loc = Locale.getDefault();
        int mode = Prefs.clockMode(this);
        boolean h24 = mode == -1 ? DateFormat.is24HourFormat(this) : mode == 1;
        if (h24) {
            timeTv.setText(new SimpleDateFormat("HH:mm", loc).format(d));
            ampmTv.setVisibility(View.GONE);
        } else {
            timeTv.setText(new SimpleDateFormat("h:mm", loc).format(d));
            ampmTv.setText(new SimpleDateFormat("a", loc).format(d));
            ampmTv.setVisibility(View.VISIBLE);
        }
        int hr = cal.get(Calendar.HOUR_OF_DAY);
        String g;
        if (hr < 5) g = "Good night";
        else if (hr < 12) g = "Good morning";
        else if (hr < 17) g = "Good afternoon";
        else if (hr < 22) g = "Good evening";
        else g = "Good night";
        greetTv.setText(g);
        dateTv.setText(new SimpleDateFormat("EEEE, d MMMM", loc).format(d).toUpperCase(loc));
    }

    private void maybeRefreshWeather(boolean force) {
        if (!Prefs.widget(this, WidgetBar.WEATHER) || weatherLoading) return;
        final int city = Prefs.city(this);
        Weather.Now c = Weather.cached(this);
        if (!force && c != null && c.city == city
                && System.currentTimeMillis() - c.fetched < WEATHER_MAX_AGE) {
            return;
        }
        weatherLoading = true;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Weather.Now now = Weather.fetch(app, city);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        weatherLoading = false;
                        if (destroyed) return;
                        Weather.Now show = now != null ? now : Weather.cached(MainActivity.this);
                        widgets.setWeather(show, MainActivity.this);
                    }
                });
            }
        }, "lumen-weather").start();
    }

    private void rebuildWidgets() {
        widgets.build(this);
        maybeRefreshWeather(false);
    }

    // ---------------------------------------------------------------- wallpaper

    private void loadWallpaper() {
        final int gen = ++wallpaperGen;
        final Context app = getApplicationContext();
        final int w = screenW, h = screenH;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap img = Wallpaper.load(app, w, h);
                final Bitmap blur = null;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || gen != wallpaperGen) return;
                        bgView.setImage(img);
                        if (blur != null) blur.recycle();
                        refreshBackdrop();
                    }
                });
            }
        }, "lumen-wallpaper").start();
    }

    /** Snapshots the visible background (wallpaper + overlay), blurs it and hands it to every glass surface. */
    private void refreshBackdrop() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                int w = bgView.getWidth(), h = bgView.getHeight();
                if (w <= 0 || h <= 0) {
                    ui.postDelayed(this, 50);
                    return;
                }
                int bw = Math.max(16, w / 10), bh = Math.max(9, h / 10);
                Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(small);
                cv.scale(bw / (float) w, bh / (float) h);
                bgView.draw(cv);
                Bitmap b = Wallpaper.blurInPlace(small);
                blurred = b;
                Glass.setBackdrop(b, w, h);
            }
        });
    }

    private void setWallpaperChoice(String id) {
        Prefs.setWallpaper(this, id);
        loadWallpaper();
    }

    // ---------------------------------------------------------------- data

    private void loadApps() {
        if (loading) {
            reloadPending = true;
            return;
        }
        loading = true;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<AppRepo.App> l;
                try {
                    l = AppRepo.load(app);
                } catch (Exception e) {
                    l = new ArrayList<AppRepo.App>();
                }
                final List<AppRepo.App> result = l;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (destroyed) return;
                        apps = result;
                        loaded = true;
                        ensureDefaultFavs();
                        render(null, null);
                        if (reloadPending) {
                            reloadPending = false;
                            loadApps();
                        }
                    }
                });
            }
        }, "lumen-apps").start();
    }

    private HashMap<String, AppRepo.App> appMap() {
        HashMap<String, AppRepo.App> m = new HashMap<String, AppRepo.App>();
        for (AppRepo.App a : apps) m.put(a.pkg, a);
        return m;
    }

    private void ensureDefaultFavs() {
        if (Prefs.has(this, Prefs.FAVS)) return;
        HashSet<String> installed = new HashSet<String>();
        for (AppRepo.App a : apps) installed.add(a.pkg);
        ArrayList<String> f = new ArrayList<String>();
        for (String p : AppRepo.DEFAULT_FAVS) {
            if (f.size() >= 6) break;
            if (installed.contains(p)) f.add(p);
        }
        for (AppRepo.App a : apps) {
            if (f.size() >= 5) break;
            if (!a.system && !f.contains(a.pkg)) f.add(a.pkg);
        }
        Prefs.putList(this, Prefs.FAVS, f);
    }

    private List<AppRepo.App> pick(List<String> pkgs, HashMap<String, AppRepo.App> map,
                                   List<String> hidden, int limit) {
        ArrayList<AppRepo.App> out = new ArrayList<AppRepo.App>();
        for (String p : pkgs) {
            if (out.size() >= limit) break;
            AppRepo.App a = map.get(p);
            if (a != null && !hidden.contains(p)) out.add(a);
        }
        return out;
    }

    private List<String> visibleFavPkgs() {
        HashMap<String, AppRepo.App> map = appMap();
        List<String> hidden = Prefs.list(this, Prefs.HIDDEN);
        ArrayList<String> out = new ArrayList<String>();
        for (AppRepo.App a : pick(Prefs.list(this, Prefs.FAVS), map, hidden, 999)) out.add(a.pkg);
        return out;
    }

    // ---------------------------------------------------------------- rendering

    private void render(String sec, String pkg) {
        if (!loaded) return;

        View cur = overlayShown ? focusBeforeOverlay : getCurrentFocus();
        boolean keepChip = false;
        if (sec == null && pkg == null && !firstRender) {
            if (cur instanceof AppTile) {
                sec = ((AppTile) cur).section;
                pkg = ((AppTile) cur).app.pkg;
            } else if (cur != null && cur.isAttachedToWindow()) {
                keepChip = true;
            }
        }

        boolean always = Prefs.alwaysLabels(this);
        HashMap<String, AppRepo.App> map = appMap();
        List<String> hidden = Prefs.list(this, Prefs.HIDDEN);
        List<AppRepo.App> favs = pick(Prefs.list(this, Prefs.FAVS), map, hidden, 999);
        List<AppRepo.App> recents = pick(Prefs.list(this, Prefs.RECENTS), map, hidden, 8);
        ArrayList<AppRepo.App> all = new ArrayList<AppRepo.App>();
        for (AppRepo.App a : apps) if (!hidden.contains(a.pkg)) all.add(a);

        sections.removeAllViews();

        int avail = getResources().getDisplayMetrics().widthPixels - dp(112);
        int gap = dp(22);
        int favW = (avail - 4 * gap) / 5;
        int favH = favW * 9 / 16;
        int cols = 6;
        int gw = (avail - (cols - 1) * gap) / cols;
        int gh = gw * 9 / 16;

        if (favs.isEmpty()) {
            TextView hint = text("Hold OK (or press the Menu button) on any app below and choose “Add to favourites”.",
                    14, 0xB3FFFFFF, "sans-serif");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.topMargin = dp(18);
            lp.bottomMargin = dp(6);
            sections.addView(hint, lp);
        } else {
            FrameLayout dock = new FrameLayout(this);
            dock.setClipChildren(false);
            dock.setClipToPadding(false);
            Glass.apply(dock, dp(32), density, Glass.TINT_DARK);
            int dockPad = dp(20);
            int dockFavW = (avail - 2 * dockPad - 4 * gap) / 5;
            View r = row(favs, SEC_FAV, dockFavW, dockFavW * 9 / 16, gap, always);
            r.setPadding(dockPad, 0, dockPad, 0);
            dock.addView(r, new FrameLayout.LayoutParams(MATCH, WRAP));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(MATCH, WRAP);
            dl.topMargin = dp(16);
            sections.addView(dock, dl);
        }

        if (Prefs.showRecent(this) && !recents.isEmpty()) {
            sections.addView(sectionTitle("Recently used"), titleLp());
            sections.addView(row(recents, SEC_RECENT, gw, gh, gap, always), rowLp());
        }

        sections.addView(sectionTitle("All apps"), titleLp());
        sections.addView(grid(all, cols, gw, gh, gap, always), new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView foot = text("Hold OK or press ☰ on an app to pin, reorder or hide it  ·  No ads, no tracking",
                12, 0x80FFFFFF, "sans-serif");
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(WRAP, WRAP);
        fl.topMargin = dp(10);
        sections.addView(foot, fl);

        View target = null;
        if (sec != null || pkg != null) target = findTile(sec, pkg);
        if (target == null && pkg != null) target = findTile(null, pkg);
        if (target == null && !keepChip) target = findTile(null, null);

        boolean wasFirst = firstRender;
        firstRender = false;
        if (overlayShown) {
            if (target != null) focusBeforeOverlay = target;
        } else if (target != null) {
            target.requestFocus();
            if (wasFirst) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        scroll.scrollTo(0, 0);
                    }
                });
            }
        }
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 16, 0xE6FFFFFF, "sans-serif-medium");
        t.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        return t;
    }

    private LinearLayout.LayoutParams titleLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(18);
        return lp;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.leftMargin = -dp(56);
        lp.rightMargin = -dp(56);
        return lp;
    }

    private View row(List<AppRepo.App> list, String section, int w, int h, int gap, boolean always) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setClipToPadding(false);
        hsv.setClipChildren(false);
        hsv.setPadding(dp(56), 0, dp(56), 0);
        hsv.setHorizontalFadingEdgeEnabled(true);
        hsv.setFadingEdgeLength(dp(40));

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(0, dp(20), 0, section.equals(SEC_FAV) ? dp(10) : dp(4));
        for (int i = 0; i < list.size(); i++) {
            AppTile t = new AppTile(this, list.get(i), section, w, h, always);
            wire(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            if (i > 0) lp.leftMargin = gap;
            r.addView(t, lp);
        }
        hsv.addView(r, new HorizontalScrollView.LayoutParams(WRAP, WRAP));
        return hsv;
    }

    private View grid(List<AppRepo.App> list, int cols, int w, int h, int gap, boolean always) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setClipChildren(false);
        g.setClipToPadding(false);
        g.setPadding(0, dp(18), 0, 0);
        LinearLayout rowL = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                rowL = new LinearLayout(this);
                rowL.setOrientation(LinearLayout.HORIZONTAL);
                rowL.setClipChildren(false);
                rowL.setClipToPadding(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
                rl.bottomMargin = dp(6);
                g.addView(rowL, rl);
            }
            AppTile t = new AppTile(this, list.get(i), SEC_ALL, w, h, always);
            wire(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            if (i % cols > 0) lp.leftMargin = gap;
            rowL.addView(t, lp);
        }
        return g;
    }

    private void wire(final AppTile t) {
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(t.app, t.section);
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showAppOptions(t, null);
                return true;
            }
        });
    }

    private void collectTiles(ViewGroup g, List<AppTile> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof AppTile) out.add((AppTile) v);
            else if (v instanceof ViewGroup) collectTiles((ViewGroup) v, out);
        }
    }

    /** Finds a tile by section and/or package; with both null returns the first tile. */
    private AppTile findTile(String sec, String pkg) {
        ArrayList<AppTile> tiles = new ArrayList<AppTile>();
        collectTiles(sections, tiles);
        for (AppTile t : tiles) {
            if (sec != null && !sec.equals(t.section)) continue;
            if (pkg != null && !pkg.equals(t.app.pkg)) continue;
            return t;
        }
        return null;
    }

    private void goTop() {
        if (overlayShown) return;
        AppTile first = findTile(null, null);
        if (first != null) first.requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    // ---------------------------------------------------------------- actions

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void launch(AppRepo.App a, String section) {
        PackageManager pm = getPackageManager();
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(a.pkg);
        } catch (Exception ignored) {
        }
        if (i == null) {
            try {
                i = pm.getLaunchIntentForPackage(a.pkg);
            } catch (Exception ignored) {
            }
        }
        if (i == null) {
            toast("Can't open " + a.label);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Can't open " + a.label);
            return;
        }
        List<String> rec = Prefs.list(this, Prefs.RECENTS);
        rec.remove(a.pkg);
        rec.add(0, a.pkg);
        while (rec.size() > 12) rec.remove(rec.size() - 1);
        Prefs.putList(this, Prefs.RECENTS, rec);
        wantSec = section;
        wantPkg = a.pkg;
        dirty = true;
    }

    /** Opens Fire TV's own Settings. Tries Amazon's settings screens first, then the standard one. */
    private void openSettings() {
        // Settings can live inside Amazon's home app; tell the Home-button watcher to leave it alone.
        Prefs.setStr(this, "settingsOpenedAt", String.valueOf(System.currentTimeMillis()));
        String[][] targets = {
            {"com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.TvSettingsActivity"},
            {"com.amazon.tv.settings", "com.amazon.tv.settings.SettingsActivity"},
        };
        for (String[] t : targets) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setClassName(t[0], t[1]);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(getPackageManager()) == null) continue;
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        String[] pkgs = {"com.amazon.tv.settings.v2", "com.amazon.tv.settings"};
        for (String p : pkgs) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(p);
                if (i == null) i = getPackageManager().getLeanbackLaunchIntentForPackage(p);
                if (i == null) continue;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("Settings aren't available");
        }
    }

    private void openAmazonHome() {
        Prefs.setPauseUntil(this, System.currentTimeMillis() + PAUSE_MS);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setPackage(AppRepo.AMAZON_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(AppRepo.AMAZON_HOME);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
        } catch (Exception ignored) {
        }
        Prefs.setPauseUntil(this, 0L);
        toast("Couldn't open the Fire TV home screen");
    }

    private boolean takeoverServiceEnabled() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(getPackageName()) && s.contains("HomeWatcherService");
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- panels

    private void showPanel(Drawable icon, String title, String subtitle, String message,
                           final List<Opt> opts, int focusIndex, int widthDp) {
        if (!overlayShown) focusBeforeOverlay = getCurrentFocus();
        overlay.removeAllViews();

        if (blurred != null && !blurred.isRecycled()) {
            ImageView bl = new ImageView(this);
            bl.setScaleType(ImageView.ScaleType.FIT_XY);
            bl.setImageBitmap(blurred);
            overlay.addView(bl, new FrameLayout.LayoutParams(MATCH, MATCH));
            View tint = new View(this);
            tint.setBackgroundColor(0x8C05060B);
            overlay.addView(tint, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            View scrim = new View(this);
            scrim.setBackgroundColor(0xC705060B);
            overlay.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(26), dp(24), dp(26), dp(22));
        Glass.apply(panel, dp(30), density, Glass.TINT_PANEL);
        panel.setElevation(dp(24));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(46), dp(46));
            il.rightMargin = dp(14);
            head.addView(iv, il);
        }
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.addView(text(title, 21, 0xFFFFFFFF, "sans-serif-medium"));
        if (subtitle != null) {
            TextView st = text(subtitle, 13, 0x8CFFFFFF, "sans-serif");
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
            sl.topMargin = dp(5);
            tcol.addView(st, sl);
        }
        head.addView(tcol, new LinearLayout.LayoutParams(0, WRAP, 1f));
        panel.addView(head);

        if (message != null) {
            TextView m = text(message, 14, 0xD9FFFFFF, "sans-serif");
            m.setLineSpacing(dp(3), 1f);
            m.setIncludeFontPadding(true);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(MATCH, WRAP);
            ml.topMargin = dp(16);
            panel.addView(m, ml);
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ColorStateList optColors = new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[] {0xFF0B0C10, 0xFF0B0C10, 0xE6FFFFFF});
        for (int i = 0; i < opts.size(); i++) {
            final Opt o = opts.get(i);
            TextView t = text(o.text, 16, 0xE6FFFFFF, "sans-serif-medium");
            t.setTextColor(optColors);
            t.setFocusable(true);
            t.setClickable(true);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            t.setIncludeFontPadding(true);
            t.setPadding(dp(18), dp(12), dp(18), dp(12));
            StateListDrawable sb = new StateListDrawable();
            sb.addState(new int[] {android.R.attr.state_focused}, rounded(0xFFFFFFFF, 14));
            sb.addState(new int[] {android.R.attr.state_pressed}, rounded(0xFFFFFFFF, 14));
            sb.addState(new int[] {}, rounded(0x00000000, 14));
            t.setBackground(sb);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hidePanel(true);
                    if (o.run != null) o.run.run();
                }
            });
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(MATCH, WRAP);
            if (i > 0) ol.topMargin = dp(4);
            list.addView(t, ol);
        }

        LinearLayout.LayoutParams holderLp;
        View holder;
        if (opts.size() > 7) {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setVerticalFadingEdgeEnabled(true);
            sv.setFadingEdgeLength(dp(24));
            sv.addView(list, new ScrollView.LayoutParams(MATCH, WRAP));
            holder = sv;
            holderLp = new LinearLayout.LayoutParams(MATCH, dp(7 * 48));
        } else {
            holder = list;
            holderLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        }
        holderLp.topMargin = dp(18);
        panel.addView(holder, holderLp);

        overlay.addView(panel, new FrameLayout.LayoutParams(dp(widthDp), WRAP, Gravity.CENTER));

        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        boolean wasShown = overlayShown;
        overlay.setVisibility(View.VISIBLE);
        overlayShown = true;

        if (!wasShown) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(180).start();
        }
        panel.setAlpha(0f);
        panel.setScaleX(0.95f);
        panel.setScaleY(0.95f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator()).start();

        if (list.getChildCount() > 0) {
            int idx = Math.max(0, Math.min(focusIndex, list.getChildCount() - 1));
            final View f = list.getChildAt(idx);
            f.requestFocus();
            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (overlayShown && f.isAttachedToWindow()) f.requestFocus();
                }
            });
        }
    }

    private void hidePanel(boolean restoreFocus) {
        if (!overlayShown) return;
        overlayShown = false;
        overlay.setVisibility(View.GONE);
        overlay.removeAllViews();
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View back = focusBeforeOverlay;
        focusBeforeOverlay = null;
        if (restoreFocus) {
            if (back != null && back.isAttachedToWindow()) back.requestFocus();
            else goTop();
        }
    }

    private void showAppOptions(final AppTile tile, String focusLabel) {
        final AppRepo.App a = tile.app;
        final String sec = tile.section;
        ArrayList<Opt> opts = new ArrayList<Opt>();

        opts.add(new Opt("Open", new Runnable() {
            @Override
            public void run() {
                launch(a, sec);
            }
        }));

        List<String> favs = Prefs.list(this, Prefs.FAVS);
        if (favs.contains(a.pkg)) {
            if (SEC_FAV.equals(sec)) {
                List<String> vis = visibleFavPkgs();
                int vi = vis.indexOf(a.pkg);
                if (vi > 0) {
                    opts.add(new Opt("Move left", new Runnable() {
                        @Override
                        public void run() {
                            moveFav(a.pkg, -1, "Move left");
                        }
                    }));
                }
                if (vi >= 0 && vi < vis.size() - 1) {
                    opts.add(new Opt("Move right", new Runnable() {
                        @Override
                        public void run() {
                            moveFav(a.pkg, 1, "Move right");
                        }
                    }));
                }
            }
            opts.add(new Opt("Remove from favourites", new Runnable() {
                @Override
                public void run() {
                    List<String> f = Prefs.list(MainActivity.this, Prefs.FAVS);
                    f.remove(a.pkg);
                    Prefs.putList(MainActivity.this, Prefs.FAVS, f);
                    render(SEC_FAV.equals(sec) ? SEC_FAV : sec, SEC_FAV.equals(sec) ? null : a.pkg);
                }
            }));
        } else {
            opts.add(new Opt("Add to favourites", new Runnable() {
                @Override
                public void run() {
                    List<String> f = Prefs.list(MainActivity.this, Prefs.FAVS);
                    if (!f.contains(a.pkg)) f.add(a.pkg);
                    Prefs.putList(MainActivity.this, Prefs.FAVS, f);
                    render(sec, a.pkg);
                    toast(a.label + " added to favourites");
                }
            }));
        }

        if (SEC_RECENT.equals(sec)) {
            opts.add(new Opt("Remove from Recently used", new Runnable() {
                @Override
                public void run() {
                    List<String> r = Prefs.list(MainActivity.this, Prefs.RECENTS);
                    r.remove(a.pkg);
                    Prefs.putList(MainActivity.this, Prefs.RECENTS, r);
                    render(SEC_RECENT, null);
                }
            }));
        }

        opts.add(new Opt("Hide from Lumen", new Runnable() {
            @Override
            public void run() {
                List<String> h = Prefs.list(MainActivity.this, Prefs.HIDDEN);
                if (!h.contains(a.pkg)) h.add(a.pkg);
                Prefs.putList(MainActivity.this, Prefs.HIDDEN, h);
                render(sec, null);
                toast(a.label + " hidden. Unhide it from Customise.");
            }
        }));

        opts.add(new Opt("App info", new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", a.pkg, null));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    toast("App info isn't available");
                }
            }
        }));

        if (!a.system) {
            opts.add(new Opt("Uninstall", new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.pkg, null));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        toast("Couldn't start uninstall");
                    }
                }
            }));
        }

        opts.add(new Opt("Cancel", null));

        int fi = 0;
        if (focusLabel != null) {
            for (int i = 0; i < opts.size(); i++) {
                if (opts.get(i).text.equals(focusLabel)) fi = i;
            }
        }
        showPanel(a.icon, a.label, a.pkg, null, opts, fi, 460);
    }

    private void moveFav(String pkg, int dir, String againLabel) {
        List<String> vis = visibleFavPkgs();
        int vi = vis.indexOf(pkg);
        int ni = vi + dir;
        if (vi < 0 || ni < 0 || ni >= vis.size()) return;
        String other = vis.get(ni);
        List<String> favs = Prefs.list(this, Prefs.FAVS);
        int p1 = favs.indexOf(pkg), p2 = favs.indexOf(other);
        if (p1 < 0 || p2 < 0) return;
        favs.set(p1, other);
        favs.set(p2, pkg);
        Prefs.putList(this, Prefs.FAVS, favs);
        render(SEC_FAV, pkg);
        AppTile t = findTile(SEC_FAV, pkg);
        if (t != null) {
            List<String> after = visibleFavPkgs();
            int ai = after.indexOf(pkg);
            boolean canAgain = dir < 0 ? ai > 0 : ai < after.size() - 1;
            showAppOptions(t, canAgain ? againLabel : (dir < 0 ? "Move right" : "Move left"));
        }
    }

    // ---------------------------------------------------------------- customise

    private static String onOff(boolean b) {
        return b ? "On" : "Off";
    }

    private void showCustomise(int focusIndex) {
        ArrayList<Opt> opts = new ArrayList<Opt>();

        opts.add(new Opt("Wallpaper  ·  " + Wallpaper.nameOf(Prefs.wallpaper(this)), new Runnable() {
            @Override
            public void run() {
                showWallpapers(0);
            }
        }));

        final int dim = Prefs.dim(this);
        opts.add(new Opt("Dark overlay  ·  " + BackgroundView.DIM_NAMES[dim], new Runnable() {
            @Override
            public void run() {
                int next = (dim + 1) % BackgroundView.DIM_NAMES.length;
                Prefs.setInt(MainActivity.this, "dim", next);
                bgView.setDim(next);
                refreshBackdrop();
                showCustomise(1);
            }
        }));

        opts.add(new Opt("Widgets", new Runnable() {
            @Override
            public void run() {
                showWidgets(0);
            }
        }));

        final boolean always = Prefs.alwaysLabels(this);
        opts.add(new Opt("App names  ·  " + (always ? "Always" : "Selected app only"), new Runnable() {
            @Override
            public void run() {
                Prefs.setBool(MainActivity.this, "alwaysLabels", !always);
                render(null, null);
                showCustomise(3);
            }
        }));

        final int mode = Prefs.clockMode(this);
        String clockLabel = mode == -1 ? "Automatic" : (mode == 1 ? "24-hour" : "12-hour");
        opts.add(new Opt("Clock  ·  " + clockLabel, new Runnable() {
            @Override
            public void run() {
                int next = mode == -1 ? 1 : (mode == 1 ? 0 : -1);
                Prefs.setClockMode(MainActivity.this, next);
                updateClock();
                showCustomise(4);
            }
        }));

        final boolean rec = Prefs.showRecent(this);
        opts.add(new Opt("Recently used row  ·  " + onOff(rec), new Runnable() {
            @Override
            public void run() {
                Prefs.setShowRecent(MainActivity.this, !rec);
                render(null, null);
                showCustomise(5);
            }
        }));

        int hiddenCount = hiddenInstalled().size();
        opts.add(new Opt("Hidden apps  ·  " + hiddenCount, new Runnable() {
            @Override
            public void run() {
                showHidden();
            }
        }));

        final boolean enabled = takeoverServiceEnabled();
        final boolean on = Prefs.takeover(this);
        String homeLabel = !enabled ? "Set up" : onOff(on);
        opts.add(new Opt("Home button opens Lumen  ·  " + homeLabel, new Runnable() {
            @Override
            public void run() {
                if (!enabled) {
                    showSetup();
                } else {
                    Prefs.setTakeover(MainActivity.this, !on);
                    showCustomise(7);
                }
            }
        }));

        opts.add(new Opt("Reset favourites", new Runnable() {
            @Override
            public void run() {
                Prefs.remove(MainActivity.this, Prefs.FAVS);
                ensureDefaultFavs();
                render(SEC_FAV, null);
                toast("Favourites reset");
            }
        }));

        opts.add(new Opt("Close", null));

        showPanel(null, "Customise", "Lumen Home 1.2.2  ·  No ads  ·  No tracking", null, opts, focusIndex, 500);
    }

    private void showWallpapers(int focusIndex) {
        final String cur = Prefs.wallpaper(this);
        ArrayList<Opt> opts = new ArrayList<Opt>();
        for (int i = 0; i < Wallpaper.SCENES.length; i++) {
            final String id = Wallpaper.SCENES[i];
            final int idx = opts.size();
            opts.add(new Opt(Wallpaper.SCENE_NAMES[i] + (id.equals(cur) ? "   ✓" : ""), new Runnable() {
                @Override
                public void run() {
                    setWallpaperChoice(id);
                    showWallpapers(idx);
                }
            }));
        }

        final boolean isGradient = Wallpaper.GRADIENT.equals(cur);
        final int gi = opts.size();
        String gName = "Colour gradient" + (isGradient ? "  ·  " + Palette.ALL[Prefs.theme(this)].name + "   ✓" : "");
        opts.add(new Opt(gName, new Runnable() {
            @Override
            public void run() {
                if (isGradient) {
                    Prefs.setTheme(MainActivity.this, (Prefs.theme(MainActivity.this) + 1) % Palette.ALL.length);
                    bgView.setPalette(Palette.ALL[Prefs.theme(MainActivity.this)]);
                }
                setWallpaperChoice(Wallpaper.GRADIENT);
                refreshBackdrop();
                showWallpapers(gi);
            }
        }));

        if (Wallpaper.customFile(this).exists()) {
            final int ci = opts.size();
            opts.add(new Opt("My picture" + (Wallpaper.CUSTOM.equals(cur) ? "   ✓" : ""), new Runnable() {
                @Override
                public void run() {
                    setWallpaperChoice(Wallpaper.CUSTOM);
                    showWallpapers(ci);
                }
            }));
        }

        opts.add(new Opt("Choose from my pictures…", new Runnable() {
            @Override
            public void run() {
                openPictures();
            }
        }));
        opts.add(new Opt("Back", new Runnable() {
            @Override
            public void run() {
                showCustomise(0);
            }
        }));
        String sub = isGradient ? "Select the gradient again to change its colours" : "Use “Dark overlay” in Customise to dim it";
        showPanel(null, "Wallpaper", sub, null, opts, focusIndex, 480);
    }

    private boolean canReadPictures() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void openPictures() {
        if (!canReadPictures()) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PICTURES);
            }
            return;
        }
        showPanel(null, "Looking for pictures…", null, null, new ArrayList<Opt>(), 0, 420);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<File> files = Wallpaper.findImages();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed) showPictures(files);
                    }
                });
            }
        }, "lumen-scan").start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PICTURES) return;
        if (canReadPictures()) {
            openPictures();
        } else {
            ArrayList<Opt> opts = new ArrayList<Opt>();
            opts.add(new Opt("Back", new Runnable() {
                @Override
                public void run() {
                    showWallpapers(0);
                }
            }));
            showPanel(null, "Permission needed", null,
                    "Lumen needs permission to see the pictures on your Fire TV. Choose “Allow” when it asks.",
                    opts, 0, 480);
        }
    }

    private void showPictures(List<File> files) {
        ArrayList<Opt> opts = new ArrayList<Opt>();
        String root = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        for (final File f : files) {
            String parent = f.getParentFile() == null ? "" : f.getParentFile().getAbsolutePath();
            if (parent.startsWith(root)) parent = parent.substring(root.length());
            if (parent.startsWith("/")) parent = parent.substring(1);
            String label = f.getName() + (parent.length() > 0 ? "   ·   " + parent : "");
            opts.add(new Opt(label, new Runnable() {
                @Override
                public void run() {
                    importPicture(f);
                }
            }));
        }
        opts.add(new Opt("Back", new Runnable() {
            @Override
            public void run() {
                showWallpapers(0);
            }
        }));
        String msg = files.isEmpty()
                ? "No pictures found yet.\n\nSend a photo to your Fire TV with the “Send Files to TV” app (or download one with "
                + "Downloader), then come back here. JPG, PNG and WEBP all work; landscape pictures look best."
                : null;
        showPanel(null, "My pictures", files.isEmpty() ? null : "Newest first", msg, opts, 0, 600);
    }

    private void importPicture(final File f) {
        showPanel(null, "Setting wallpaper…", f.getName(), null, new ArrayList<Opt>(), 0, 460);
        final Context app = getApplicationContext();
        final int w = screenW, h = screenH;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = Wallpaper.importImage(app, f, w, h);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        if (ok) {
                            setWallpaperChoice(Wallpaper.CUSTOM);
                            hidePanel(true);
                            toast("Wallpaper set");
                        } else {
                            ArrayList<Opt> opts = new ArrayList<Opt>();
                            opts.add(new Opt("Back", new Runnable() {
                                @Override
                                public void run() {
                                    openPictures();
                                }
                            }));
                            showPanel(null, "Couldn't use that picture", f.getName(),
                                    "Try a different picture (JPG or PNG).", opts, 0, 460);
                        }
                    }
                });
            }
        }, "lumen-import").start();
    }

    private void showWidgets(int focusIndex) {
        ArrayList<Opt> opts = new ArrayList<Opt>();
        String[][] items = {
            {WidgetBar.WEATHER, "Weather"},
            {WidgetBar.VERSE, "Verse of the day"},
            {WidgetBar.STORAGE, "Storage"},
            {WidgetBar.NETWORK, "Network"},
        };
        for (int i = 0; i < items.length; i++) {
            final String key = items[i][0];
            final int idx = i;
            final boolean on = Prefs.widget(this, key);
            opts.add(new Opt(items[i][1] + "  ·  " + onOff(on), new Runnable() {
                @Override
                public void run() {
                    Prefs.setWidget(MainActivity.this, key, !on);
                    rebuildWidgets();
                    showWidgets(idx);
                }
            }));
        }
        opts.add(new Opt("Weather city  ·  " + Weather.CITIES[Prefs.city(this)].name, new Runnable() {
            @Override
            public void run() {
                showCities();
            }
        }));
        opts.add(new Opt("Back", new Runnable() {
            @Override
            public void run() {
                showCustomise(2);
            }
        }));
        showPanel(null, "Widgets", "Shown above your favourites", null, opts, focusIndex, 460);
    }

    private void showCities() {
        ArrayList<Opt> opts = new ArrayList<Opt>();
        final int cur = Prefs.city(this);
        for (int i = 0; i < Weather.CITIES.length; i++) {
            final int idx = i;
            opts.add(new Opt(Weather.CITIES[i].name + (i == cur ? "   ✓" : ""), new Runnable() {
                @Override
                public void run() {
                    Prefs.setInt(MainActivity.this, "city", idx);
                    widgets.setWeather(Weather.cached(MainActivity.this), MainActivity.this);
                    maybeRefreshWeather(true);
                    showWidgets(4);
                }
            }));
        }
        showPanel(null, "Weather city", "Weather by Open-Meteo", null, opts, cur, 420);
    }

    private List<AppRepo.App> hiddenInstalled() {
        HashMap<String, AppRepo.App> map = appMap();
        ArrayList<AppRepo.App> out = new ArrayList<AppRepo.App>();
        for (String p : Prefs.list(this, Prefs.HIDDEN)) {
            AppRepo.App a = map.get(p);
            if (a != null) out.add(a);
        }
        return out;
    }

    private void showHidden() {
        List<AppRepo.App> hidden = hiddenInstalled();
        ArrayList<Opt> opts = new ArrayList<Opt>();
        for (final AppRepo.App a : hidden) {
            opts.add(new Opt("Show  " + a.label, new Runnable() {
                @Override
                public void run() {
                    List<String> h = Prefs.list(MainActivity.this, Prefs.HIDDEN);
                    h.remove(a.pkg);
                    Prefs.putList(MainActivity.this, Prefs.HIDDEN, h);
                    render(null, null);
                    if (hiddenInstalled().isEmpty()) showCustomise(6);
                    else showHidden();
                }
            }));
        }
        opts.add(new Opt("Back", new Runnable() {
            @Override
            public void run() {
                showCustomise(6);
            }
        }));
        String msg = hidden.isEmpty() ? "No hidden apps." : null;
        showPanel(null, "Hidden apps", "Select an app to show it again", msg, opts, 0, 460);
    }

    private void showSetup() {
        String msg = "Makes the Home button and start-up open Lumen instead of Amazon's home screen.\n\n"
                + "Fire TV hides this switch, so it's turned on once with ADB from a phone or computer:\n\n"
                + "adb shell settings put secure enabled_accessibility_services "
                + "com.droidkay.lumen/com.droidkay.lumen.HomeWatcherService\n\n"
                + "adb shell settings put secure accessibility_enabled 1\n\n"
                + "\"Fire TV Home\" at the top still opens Amazon's screen when you need it.";
        ArrayList<Opt> opts = new ArrayList<Opt>();
        opts.add(new Opt("Back", new Runnable() {
            @Override
            public void run() {
                showCustomise(7);
            }
        }));
        showPanel(null, "Home button setup", "One-time setup", msg, opts, 0, 640);
    }
}
