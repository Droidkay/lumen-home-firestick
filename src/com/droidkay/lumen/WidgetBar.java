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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The widgets in the top shelf. Row one: analog clock, date, weather and power (your Felicity inverter).
 * Row two: storage, network and the sleep timer. Each one can be switched off in Customise;
 * the clock and date keep their size, so switching others off never stretches them.
 */
final class WidgetBar extends LinearLayout {
    static final String CLOCK = "clock", DATE = "date", POWER = "power",
            WEATHER = "weather", STORAGE = "storage", NETWORK = "network", SLEEP = "sleep";

    /** The widget you can press OK on. */
    interface Listener {
        void onSleepTimer();

        void onPower();
    }

    private static final int ACCENT = AnalogClockView.ACCENT;
    private static final int ROW1_H = 132, ROW2_H = 104;

    private final float density;
    private Listener listener;
    private Glass lastGlass;

    private AnalogClockView analog;
    private TextView dateDay, dateNum, dateMonth;
    private TextView sleepSub;
    private IconView weatherIcon, netIcon;
    private TextView weatherTemp, weatherDesc, weatherPlace;
    private TextView storageMain, storageSub;
    private View storageBar;
    private TextView netMain, netSub;
    private TextView powerPill, powerPct, powerState, powerSub;
    private View powerBar;
    private GradientDrawable powerFill;
    private IconView powerIcon;

    WidgetBar(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
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

    private LinearLayout newRow(boolean first) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        if (!first) lp.topMargin = dp(12);
        addView(row, lp);
        return row;
    }

    /** weight > 0 shares the row; fixedWidthDp > 0 pins the width instead. */
    private LinearLayout card(LinearLayout row, float weight, int fixedWidthDp, int heightDp, int tint) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        lastGlass = Glass.apply(card, dp(22), density, tint);
        LayoutParams lp = fixedWidthDp > 0
                ? new LayoutParams(dp(fixedWidthDp), dp(heightDp))
                : new LayoutParams(0, dp(heightDp), weight);
        if (row.getChildCount() > 0) lp.leftMargin = dp(12);
        row.addView(card, lp);
        return card;
    }

    private LayoutParams gap(int dpTop) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpTop);
        return lp;
    }

    /** Makes a card something you can focus and press OK on. */
    private void pressable(final LinearLayout card, final Glass glass, final int rest, final int focused,
                           final Runnable action) {
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                glass.setGlassTint(f ? focused : rest);
                float s = f ? 1.06f : 1f;
                card.animate().scaleX(s).scaleY(s).setDuration(140).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
    }

    /** Rebuilds the bar with the widgets switched on in Customise. */
    void build(Context ctx) {
        removeAllViews();
        analog = null;
        dateNum = null;
        sleepSub = null;
        weatherIcon = null;
        storageMain = null;
        netMain = null;
        powerPct = null;

        LinearLayout row1 = newRow(true);

        if (Prefs.widget(ctx, CLOCK)) {
            LinearLayout c = card(row1, 0, ROW1_H, ROW1_H, Glass.TINT_DARK);
            c.setPadding(dp(12), dp(12), dp(12), dp(12));
            analog = new AnalogClockView(getContext());
            c.addView(analog, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        if (Prefs.widget(ctx, DATE)) {
            LinearLayout c = card(row1, 0, 170, ROW1_H, Glass.TINT_DARK);
            dateDay = caption("");
            dateDay.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            c.addView(dateDay);
            dateNum = text("", 52, 0xFFFFFFFF, "sans-serif-light");
            c.addView(dateNum, gap(6));
            dateMonth = text("", 11, ACCENT, "monospace");
            dateMonth.setLetterSpacing(0.08f);
            c.addView(dateMonth, gap(8));
        }

        if (Prefs.widget(ctx, WEATHER)) {
            LinearLayout c = card(row1, 0, 236, ROW1_H, Glass.TINT_DARK);
            c.addView(caption("WEATHER"));
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            weatherIcon = new IconView(getContext(), IconView.CLOUD, dp(1.8f));
            weatherIcon.setColor(0xF2FFFFFF);
            row.addView(weatherIcon, new LayoutParams(dp(40), dp(40)));
            weatherTemp = text("--°", 40, 0xFFFFFFFF, "sans-serif-light");
            LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tl.leftMargin = dp(12);
            row.addView(weatherTemp, tl);
            c.addView(row, gap(6));
            weatherDesc = text("Loading…", 13, 0xE6FFFFFF, "sans-serif-medium");
            c.addView(weatherDesc, gap(5));
            weatherPlace = text(Weather.CITIES[Prefs.city(ctx)].name, 12, 0x99FFFFFF, "sans-serif");
            c.addView(weatherPlace, gap(3));
            setWeather(Weather.cached(ctx), ctx);
        }

        if (Prefs.widget(ctx, POWER)) {
            LinearLayout c = card(row1, 1.0f, 0, ROW1_H, Glass.TINT_DARK);
            pressable(c, lastGlass, Glass.TINT_DARK, 0x4DFFFFFF, new Runnable() {
                @Override
                public void run() {
                    if (listener != null) listener.onPower();
                }
            });
            c.setContentDescription("Power");
            LinearLayout head = new LinearLayout(getContext());
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(caption("POWER"), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            powerPill = text("", 9, 0xFFFFFFFF, "sans-serif-medium");
            powerPill.setLetterSpacing(0.1f);
            powerPill.setPadding(dp(9), dp(4), dp(9), dp(4));
            head.addView(powerPill, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            c.addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout mid = new LinearLayout(getContext());
            mid.setOrientation(HORIZONTAL);
            mid.setGravity(Gravity.CENTER_VERTICAL);
            powerIcon = new IconView(getContext(), IconView.BOLT, dp(1.8f));
            powerIcon.setColor(0xFF34D17A);
            mid.addView(powerIcon, new LayoutParams(dp(30), dp(30)));
            powerPct = text("--%", 34, 0xFFFFFFFF, "sans-serif-light");
            LayoutParams pl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            pl.leftMargin = dp(8);
            mid.addView(powerPct, pl);
            powerState = text("", 13, 0xE6FFFFFF, "sans-serif-medium");
            LayoutParams sl = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            sl.leftMargin = dp(14);
            mid.addView(powerState, sl);
            c.addView(mid, gap(4));

            GradientDrawable track = new GradientDrawable();
            track.setColor(0x33FFFFFF);
            track.setCornerRadius(dp(3));
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(0xFF34D17A);
            fill.setCornerRadius(dp(3));
            powerFill = fill;
            ClipDrawable clip = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
            powerBar = new View(getContext());
            powerBar.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {track, clip}));
            LayoutParams bl = new LayoutParams(LayoutParams.MATCH_PARENT, dp(5));
            bl.topMargin = dp(8);
            c.addView(powerBar, bl);
            powerSub = text("Looking for your inverter…", 12, 0x99FFFFFF, "sans-serif");
            c.addView(powerSub, gap(8));
            setPower(lastPower, lastPowerError);
        }

        LinearLayout row2 = newRow(false);

        if (Prefs.widget(ctx, STORAGE)) {
            LinearLayout c = card(row2, 1.0f, 0, ROW2_H, Glass.TINT_DARK);
            c.addView(caption("STORAGE"));
            storageMain = text("", 20, 0xFFFFFFFF, "sans-serif-light");
            c.addView(storageMain, gap(8));
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
            bl.topMargin = dp(9);
            c.addView(storageBar, bl);
            storageSub = text("", 12, 0x99FFFFFF, "sans-serif");
            c.addView(storageSub, gap(7));
        }

        if (Prefs.widget(ctx, NETWORK)) {
            LinearLayout c = card(row2, 1.0f, 0, ROW2_H, Glass.TINT_DARK);
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
            c.addView(row, gap(10));
            netSub = text("", 12, 0x99FFFFFF, "sans-serif");
            c.addView(netSub, gap(8));
        }

        if (Prefs.widget(ctx, SLEEP)) {
            LinearLayout c = card(row2, 1.0f, 0, ROW2_H, 0xF2F2553F);
            pressable(c, lastGlass, 0xF2F2553F, 0xFFFF6A52, new Runnable() {
                @Override
                public void run() {
                    if (listener != null) listener.onSleepTimer();
                }
            });
            IconView moon = new IconView(getContext(), IconView.MOON, dp(1.8f));
            moon.setColor(0xFFFFFFFF);
            c.addView(moon, new LayoutParams(dp(26), dp(26)));
            c.addView(text("Sleep timer", 16, 0xFFFFFFFF, "sans-serif-medium"), gap(10));
            sleepSub = text("SET", 12, 0xE6FFFFFF, "sans-serif-medium");
            sleepSub.setLetterSpacing(0.1f);
            c.addView(sleepSub, gap(3));
            c.setContentDescription("Sleep timer");
        }

        row1.setVisibility(row1.getChildCount() == 0 ? GONE : VISIBLE);
        row2.setVisibility(row2.getChildCount() == 0 ? GONE : VISIBLE);
        setVisibility(row1.getChildCount() + row2.getChildCount() == 0 ? GONE : VISIBLE);
        refresh(ctx);
    }

    /** Cheap local refresh: clock face, date, sleep timer, storage, network. */
    void refresh(Context ctx) {
        if (analog != null) analog.invalidate();
        if (dateNum != null) {
            Date d = new Date();
            Locale loc = Locale.getDefault();
            dateDay.setText(new SimpleDateFormat("EEEE", loc).format(d).toUpperCase(loc));
            dateNum.setText(new SimpleDateFormat("d", loc).format(d));
            dateMonth.setText(new SimpleDateFormat("MMM / yyyy", loc).format(d).toUpperCase(loc));
        }
        if (sleepSub != null) {
            int left = SleepTimer.remainingMinutes(ctx);
            sleepSub.setText(left < 0 ? "SET" : left + " MIN LEFT");
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
            netMain.setText(online ? "Connected" : "Offline");
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
                sub = "Online";
            }
            netSub.setText(sub);
        }
    }

    private Reading lastPower;
    private String lastPowerError;

    /** Latest inverter reading (null while none yet), or an error to show. */
    void setPower(Reading r, String error) {
        lastPower = r;
        lastPowerError = error;
        if (powerPct == null) return;
        if (r == null) {
            powerPct.setText("--%");
            powerState.setText("");
            powerPill.setVisibility(INVISIBLE);
            powerSub.setText(error != null ? error : "Looking for your inverter…");
            ((LayerDrawable) powerBar.getBackground()).getDrawable(1).setLevel(0);
            return;
        }
        boolean grid = r.gridOn();
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(grid ? 0xFF22A35A : 0x66FFFFFF);
        pb.setCornerRadius(dp(10));
        powerPill.setBackground(pb);
        powerPill.setText(grid ? "NEPA ON" : "NO NEPA");
        powerPill.setVisibility(VISIBLE);
        int soc = Double.isNaN(r.soc) ? -1 : (int) Math.round(r.soc);
        powerPct.setText(soc < 0 ? "--%" : soc + "%");
        int color = soc < 0 ? 0xFFFFFFFF : soc <= 20 ? 0xFFFF5A4A : soc <= 40 ? 0xFFFFC23D : 0xFF34D17A;
        powerIcon.setColor(color);
        LayerDrawable ld = (LayerDrawable) powerBar.getBackground();
        if (powerFill != null) powerFill.setColor(color);
        ld.getDrawable(1).setLevel(Math.max(0, soc) * 100);
        double b = r.battery();
        powerState.setText(b > 30 ? "Charging " + watts(b) : b < -30 ? "Using " + watts(-b) : "Idle");
        String sub = "Solar " + watts(r.solar()) + "  ·  Home " + watts(r.load());
        if (error != null) sub = error + "  ·  last: " + sub;
        powerSub.setText(sub);
    }

    private static String watts(double w) {
        w = Math.abs(w);
        return w >= 1000 ? String.format(Locale.US, "%.1f kW", w / 1000) : Math.round(w) + " W";
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
