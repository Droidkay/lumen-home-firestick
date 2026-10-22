package com.droidkay.lumen;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Glass status pill in the top-right corner. Three styles: Minimal (time), Weather (temperature)
 * or Full (time, weather, Wi-Fi, Bluetooth). Selecting it opens Control Center.
 */
final class StatusPill extends LinearLayout {
    static final int OFF = 0, MINIMAL = 1, WEATHER = 2, FULL = 3;
    static final String[] NAMES = {"Off", "Minimal", "Weather", "Full"};

    private final float density;
    private Glass glass;
    private TextView time, temp;
    private IconView wIcon, wifi, bt, gear;
    private boolean focused;

    StatusPill(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Control Center");
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                focused = f;
                if (glass != null) glass.setGlassTint(f ? 0xEBFFFFFF : Glass.TINT_DARK);
                colorize();
                float s = f ? 1.08f : 1f;
                animate().scaleX(s).scaleY(s).setDuration(150).start();
            }
        });
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    private TextView text(float sp) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private IconView icon(int type, int sizeDp, int leftDp) {
        IconView iv = new IconView(getContext(), type, dp(1.8f));
        LayoutParams lp = new LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.leftMargin = dp(leftDp);
        addView(iv, lp);
        return iv;
    }

    /** Rebuilds the pill for the chosen style. */
    void build(int style) {
        removeAllViews();
        time = temp = null;
        wIcon = wifi = bt = gear = null;
        setVisibility(style == OFF ? GONE : VISIBLE);
        if (style == OFF) return;
        setPadding(dp(16), dp(7), dp(7), dp(7));
        glass = Glass.apply(this, dp(22), density, focused ? 0xEBFFFFFF : Glass.TINT_DARK);

        if (style == MINIMAL || style == FULL) {
            time = text(15);
            addView(time);
        }
        if (style == WEATHER || style == FULL) {
            wIcon = icon(IconView.CLOUD, 18, style == FULL ? 16 : 0);
            temp = text(15);
            LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tl.leftMargin = dp(6);
            addView(temp, tl);
        }
        if (style == FULL) {
            wifi = icon(IconView.WIFI, 18, 18);
            bt = icon(IconView.BLUETOOTH, 18, 14);
        }
        // round "control" button at the end, like an avatar
        gear = new IconView(getContext(), IconView.SLIDERS, dp(1.6f));
        gear.setPadding(dp(6), dp(6), dp(6), dp(6));
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0xFF4A8DFF);
        gear.setBackground(circle);
        LayoutParams gl = new LayoutParams(dp(28), dp(28));
        gl.leftMargin = dp(12);
        addView(gear, gl);
        refresh(getContext());
    }

    void refresh(Context ctx) {
        if (time != null) {
            boolean h24 = Prefs.clockMode(ctx) == -1 ? DateFormat.is24HourFormat(ctx) : Prefs.clockMode(ctx) == 1;
            time.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date()).toLowerCase(Locale.getDefault()));
        }
        if (temp != null) {
            Weather.Now w = Weather.cached(ctx);
            if (w != null && w.city == Prefs.city(ctx)) {
                temp.setText(Math.round(w.temp) + "°");
                wIcon.setType(Weather.icon(w.code, w.day));
            } else {
                temp.setText("--°");
            }
        }
        if (wifi != null) {
            boolean online = false;
            try {
                ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
                online = ni != null && ni.isConnected();
            } catch (Throwable ignored) {
            }
            wifi.setType(online ? IconView.WIFI : IconView.WIFI_OFF);
        }
        if (bt != null) {
            boolean on = false;
            try {
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                on = a != null && a.isEnabled();
            } catch (Throwable ignored) {
            }
            bt.setVisibility(on ? VISIBLE : GONE);
        }
        colorize();
    }

    private void colorize() {
        int col = focused ? 0xFF0B0C10 : 0xF2FFFFFF;
        if (time != null) time.setTextColor(col);
        if (temp != null) temp.setTextColor(col);
        if (wIcon != null) wIcon.setColor(col);
        if (wifi != null) wifi.setColor(col);
        if (bt != null) bt.setColor(col);
        if (gear != null) gear.setColor(0xFFFFFFFF);
    }
}
