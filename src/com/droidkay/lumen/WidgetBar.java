package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** The row of glass widgets in the top shelf: weather, verse of the day, storage and network. */
final class WidgetBar extends LinearLayout {
    static final String WEATHER = "weather", VERSE = "verse", STORAGE = "storage", NETWORK = "network";

    private final float density;
    private IconView weatherIcon, netIcon;
    private TextView weatherTemp, weatherDesc, weatherPlace;
    private TextView verseText, verseRef;
    private TextView storageMain, storageSub;
    private View storageBar;
    private TextView netMain, netSub;

    WidgetBar(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.BOTTOM);
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    static GradientDrawable glass(float density, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x59101218);
        g.setCornerRadius(radiusDp * density);
        g.setStroke((int) Math.max(1, density), 0x26FFFFFF);
        return g;
    }

    private TextView text(String s, float sp, int color, String family) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private TextView caption(String s) {
        TextView t = text(s, 10, 0x99FFFFFF, "sans-serif-medium");
        t.setLetterSpacing(0.12f);
        return t;
    }

    private LinearLayout card(int widthDp) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(15), dp(18), dp(15));
        Glass.apply(card, dp(22), density, Glass.TINT_DARK);
        LayoutParams lp = new LayoutParams(dp(widthDp), dp(112));
        if (getChildCount() > 0) lp.leftMargin = dp(12);
        addView(card, lp);
        return card;
    }

    private LayoutParams gap(int dpTop) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpTop);
        return lp;
    }

    /** Rebuilds the bar with the widgets switched on in Customise. */
    void build(Context ctx) {
        removeAllViews();
        weatherIcon = null;
        verseText = null;
        storageMain = null;
        netMain = null;

        if (Prefs.widget(ctx, WEATHER)) {
            LinearLayout c = card(196);
            c.addView(caption("WEATHER"));
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            weatherIcon = new IconView(getContext(), IconView.CLOUD, dp(1.8f));
            weatherIcon.setColor(0xF2FFFFFF);
            row.addView(weatherIcon, new LayoutParams(dp(32), dp(32)));
            weatherTemp = text("--°", 28, 0xFFFFFFFF, "sans-serif-light");
            LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tl.leftMargin = dp(10);
            row.addView(weatherTemp, tl);
            c.addView(row, gap(8));
            weatherDesc = text("Loading…", 13, 0xE6FFFFFF, "sans-serif-medium");
            c.addView(weatherDesc, gap(6));
            weatherPlace = text(Weather.CITIES[Prefs.city(ctx)].name, 12, 0x99FFFFFF, "sans-serif");
            c.addView(weatherPlace, gap(4));
            setWeather(Weather.cached(ctx), ctx);
        }

        if (Prefs.widget(ctx, VERSE)) {
            LinearLayout c = card(330);
            c.addView(caption("VERSE OF THE DAY"));
            verseText = text("", 14, 0xF2FFFFFF, "serif");
            verseText.setSingleLine(false);
            verseText.setMaxLines(3);
            verseText.setLineSpacing(dp(2), 1f);
            c.addView(verseText, gap(8));
            verseRef = text("", 12, 0x99FFFFFF, "sans-serif-medium");
            c.addView(verseRef, gap(6));
        }

        if (Prefs.widget(ctx, STORAGE)) {
            LinearLayout c = card(170);
            c.addView(caption("STORAGE"));
            storageMain = text("", 20, 0xFFFFFFFF, "sans-serif-light");
            c.addView(storageMain, gap(10));
            GradientDrawable track = new GradientDrawable();
            track.setColor(0x33FFFFFF);
            track.setCornerRadius(dp(3));
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(0xE6FFFFFF);
            fill.setCornerRadius(dp(3));
            ClipDrawable clip = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
            storageBar = new View(getContext());
            storageBar.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {track, clip}));
            LayoutParams bl = new LayoutParams(LayoutParams.MATCH_PARENT, dp(5));
            bl.topMargin = dp(10);
            c.addView(storageBar, bl);
            storageSub = text("", 12, 0x99FFFFFF, "sans-serif");
            c.addView(storageSub, gap(8));
        }

        if (Prefs.widget(ctx, NETWORK)) {
            LinearLayout c = card(160);
            c.addView(caption("NETWORK"));
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            netIcon = new IconView(getContext(), IconView.WIFI, dp(1.8f));
            netIcon.setColor(0xF2FFFFFF);
            row.addView(netIcon, new LayoutParams(dp(26), dp(26)));
            netMain = text("", 18, 0xFFFFFFFF, "sans-serif-medium");
            LayoutParams nl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            nl.leftMargin = dp(10);
            row.addView(netMain, nl);
            c.addView(row, gap(12));
            netSub = text("", 12, 0x99FFFFFF, "sans-serif");
            c.addView(netSub, gap(10));
        }

        setVisibility(getChildCount() == 0 ? GONE : VISIBLE);
        refresh(ctx);
    }

    /** Cheap local refresh: verse, storage, network. */
    void refresh(Context ctx) {
        if (verseText != null) {
            String[] v = Verses.today();
            verseText.setText("“" + v[0] + "”");
            verseRef.setText(v[1] + "  ·  KJV");
        }
        if (storageMain != null) {
            try {
                StatFs st = new StatFs(Environment.getDataDirectory().getPath());
                long free = st.getAvailableBytes(), total = st.getTotalBytes();
                storageMain.setText(gb(free) + " free");
                storageSub.setText("of " + gb(total));
                int level = total > 0 ? (int) ((total - free) * 10000L / total) : 0;
                LayerDrawable ld = (LayerDrawable) storageBar.getBackground();
                ld.getDrawable(1).setLevel(level);
            } catch (Throwable t) {
                storageMain.setText("—");
            }
        }
        if (netMain != null) {
            boolean online = false, wifi = false, ethernet = false;
            try {
                ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
                online = ni != null && ni.isConnected();
                if (online) {
                    wifi = ni.getType() == ConnectivityManager.TYPE_WIFI;
                    ethernet = ni.getType() == ConnectivityManager.TYPE_ETHERNET;
                }
            } catch (Throwable ignored) {
            }
            netIcon.setType(online ? IconView.WIFI : IconView.WIFI_OFF);
            netMain.setText(online ? "Online" : "Offline");
            String sub = "No connection";
            if (online && ethernet) {
                sub = "Ethernet";
            } else if (online && wifi) {
                sub = "Wi-Fi";
                try {
                    WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                    if (wi != null) {
                        int lvl = WifiManager.calculateSignalLevel(wi.getRssi(), 4);
                        String[] names = {"Weak", "Fair", "Good", "Strong"};
                        sub = "Wi-Fi  ·  " + names[Math.max(0, Math.min(3, lvl))];
                    }
                } catch (Throwable ignored) {
                }
            } else if (online) {
                sub = "Connected";
            }
            netSub.setText(sub);
        }
    }

    void setWeather(Weather.Now now, Context ctx) {
        if (weatherIcon == null) return;
        int city = Prefs.city(ctx);
        weatherPlace.setText(Weather.CITIES[city].name);
        if (now == null || now.city != city) {
            weatherTemp.setText("--°");
            weatherDesc.setText(now == null ? "Loading…" : "Updating…");
            return;
        }
        weatherIcon.setType(Weather.icon(now.code, now.day));
        weatherTemp.setText(Math.round(now.temp) + "°");
        weatherDesc.setText(Weather.describe(now.code));
        if (!Double.isNaN(now.hi) && !Double.isNaN(now.lo)) {
            weatherPlace.setText(String.format(Locale.US, "%s  ·  H %d°  L %d°", Weather.CITIES[city].name,
                    Math.round(now.hi), Math.round(now.lo)));
        }
    }

    private static String gb(long bytes) {
        double g = bytes / 1_000_000_000.0;
        return g >= 10 ? String.format(Locale.US, "%.0f GB", g) : String.format(Locale.US, "%.1f GB", g);
    }
}
