package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn in code, so they look the same on every Fire OS version. */
final class IconView extends View {
    static final int SETTINGS = 0, HOME = 1, SLIDERS = 2, SUN = 3, MOON = 4, PARTLY = 5, CLOUD = 6,
            RAIN = 7, STORM = 8, FOG = 9, WIFI = 10, WIFI_OFF = 11, DISK = 12, BLUETOOTH = 13, RECENT = 14, POWER = 15, BOLT = 16;

    private int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    IconView(Context c, int type, float strokePx) {
        super(c);
        this.type = type;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokePx);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFFFFFFFF);
    }

    void setColor(int color) {
        p.setColor(color);
        invalidate();
    }

    void setType(int t) {
        if (t != type) {
            type = t;
            invalidate();
        }
    }

    private void cloud(Canvas c, float cx, float cy, float s) {
        // flat-bottomed cloud outline
        path.reset();
        float l = cx - s * 0.40f, r = cx + s * 0.40f, b = cy + s * 0.18f;
        path.moveTo(l + s * 0.10f, b);
        path.lineTo(r - s * 0.08f, b);
        rf.set(r - s * 0.28f, cy - s * 0.10f, r, b);
        path.arcTo(rf, 90, -180, false);
        rf.set(cx - s * 0.22f, cy - s * 0.34f, cx + s * 0.20f, cy + s * 0.08f);
        path.arcTo(rf, -10, -180, false);
        rf.set(l, cy - s * 0.10f, l + s * 0.28f, b);
        path.arcTo(rf, -60, -150, false);
        path.close();
        c.drawPath(path, p);
    }

    private void sun(Canvas c, float cx, float cy, float s, float r) {
        c.drawCircle(cx, cy, r, p);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            c.drawLine(cx + (float) Math.cos(a) * (r + s * 0.08f), cy + (float) Math.sin(a) * (r + s * 0.08f),
                    cx + (float) Math.cos(a) * (r + s * 0.18f), cy + (float) Math.sin(a) * (r + s * 0.18f), p);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        switch (type) {
            case SETTINGS:
                c.drawCircle(cx, cy, s * 0.16f, p);
                c.drawCircle(cx, cy, s * 0.30f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(cx + (float) Math.cos(a) * s * 0.30f, cy + (float) Math.sin(a) * s * 0.30f,
                            cx + (float) Math.cos(a) * s * 0.44f, cy + (float) Math.sin(a) * s * 0.44f, p);
                }
                break;
            case HOME:
                path.reset();
                path.moveTo(cx - s * 0.40f, cy - s * 0.02f);
                path.lineTo(cx, cy - s * 0.40f);
                path.lineTo(cx + s * 0.40f, cy - s * 0.02f);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(cx - s * 0.28f, cy - s * 0.12f);
                path.lineTo(cx - s * 0.28f, cy + s * 0.38f);
                path.lineTo(cx + s * 0.28f, cy + s * 0.38f);
                path.lineTo(cx + s * 0.28f, cy - s * 0.12f);
                c.drawPath(path, p);
                break;
            case SLIDERS: {
                float[] ys = {-0.28f, 0f, 0.28f};
                float[] knobs = {0.15f, -0.18f, 0.05f};
                for (int i = 0; i < 3; i++) {
                    float y = cy + ys[i] * s;
                    c.drawLine(cx - s * 0.40f, y, cx + s * 0.40f, y, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx + knobs[i] * s, y, s * 0.09f, p);
                    p.setStyle(Paint.Style.STROKE);
                }
                break;
            }
            case SUN:
                sun(c, cx, cy, s, s * 0.20f);
                break;
            case MOON:
                path.reset();
                rf.set(cx - s * 0.32f, cy - s * 0.32f, cx + s * 0.32f, cy + s * 0.32f);
                path.addArc(rf, 70, 250);
                rf.set(cx - s * 0.08f, cy - s * 0.36f, cx + s * 0.40f, cy + s * 0.12f);
                path.arcTo(rf, 200, -160, false);
                c.drawPath(path, p);
                break;
            case PARTLY:
                sun(c, cx - s * 0.14f, cy - s * 0.14f, s * 0.7f, s * 0.13f);
                cloud(c, cx + s * 0.06f, cy + s * 0.10f, s * 0.85f);
                break;
            case CLOUD:
                cloud(c, cx, cy, s);
                break;
            case RAIN:
                cloud(c, cx, cy - s * 0.12f, s);
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * s * 0.18f;
                    c.drawLine(x, cy + s * 0.16f, x - s * 0.06f, cy + s * 0.34f, p);
                }
                break;
            case STORM:
                cloud(c, cx, cy - s * 0.12f, s);
                path.reset();
                path.moveTo(cx + s * 0.04f, cy + s * 0.10f);
                path.lineTo(cx - s * 0.08f, cy + s * 0.26f);
                path.lineTo(cx + s * 0.04f, cy + s * 0.26f);
                path.lineTo(cx - s * 0.06f, cy + s * 0.42f);
                c.drawPath(path, p);
                break;
            case FOG:
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * s * 0.18f;
                    c.drawLine(cx - s * 0.38f + (i == 0 ? s * 0.1f : 0), y, cx + s * 0.38f, y, p);
                }
                break;
            case WIFI:
            case WIFI_OFF: {
                float by = cy + s * 0.28f;
                for (int i = 1; i <= 3; i++) {
                    float r = s * 0.15f * i;
                    rf.set(cx - r, by - r, cx + r, by + r);
                    c.drawArc(rf, 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, by - s * 0.02f, s * 0.05f, p);
                p.setStyle(Paint.Style.STROKE);
                if (type == WIFI_OFF) c.drawLine(cx - s * 0.36f, cy - s * 0.36f, cx + s * 0.36f, cy + s * 0.36f, p);
                break;
            }
            case DISK:
                rf.set(cx - s * 0.34f, cy - s * 0.26f, cx + s * 0.34f, cy + s * 0.26f);
                c.drawRoundRect(rf, s * 0.08f, s * 0.08f, p);
                c.drawLine(cx - s * 0.34f, cy + s * 0.06f, cx + s * 0.34f, cy + s * 0.06f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx + s * 0.20f, cy + s * 0.16f, s * 0.035f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case BLUETOOTH:
                path.reset();
                path.moveTo(cx - s * 0.24f, cy - s * 0.20f);
                path.lineTo(cx + s * 0.22f, cy + s * 0.20f);
                path.lineTo(cx, cy + s * 0.42f);
                path.lineTo(cx, cy - s * 0.42f);
                path.lineTo(cx + s * 0.22f, cy - s * 0.20f);
                path.lineTo(cx - s * 0.24f, cy + s * 0.20f);
                c.drawPath(path, p);
                break;
            case RECENT:
                rf.set(cx - s * 0.20f, cy - s * 0.30f, cx + s * 0.36f, cy + s * 0.18f);
                c.drawRoundRect(rf, s * 0.06f, s * 0.06f, p);
                c.drawLine(cx - s * 0.36f, cy - s * 0.12f, cx - s * 0.36f, cy + s * 0.34f, p);
                c.drawLine(cx - s * 0.36f, cy + s * 0.34f, cx + s * 0.18f, cy + s * 0.34f, p);
                break;
            case BOLT:
                path.reset();
                path.moveTo(cx + s * 0.08f, cy - s * 0.42f);
                path.lineTo(cx - s * 0.24f, cy + s * 0.06f);
                path.lineTo(cx - s * 0.02f, cy + s * 0.06f);
                path.lineTo(cx - s * 0.08f, cy + s * 0.42f);
                path.lineTo(cx + s * 0.24f, cy - s * 0.08f);
                path.lineTo(cx + s * 0.02f, cy - s * 0.08f);
                path.close();
                c.drawPath(path, p);
                break;
            case POWER:
                rf.set(cx - s * 0.32f, cy - s * 0.30f, cx + s * 0.32f, cy + s * 0.34f);
                c.drawArc(rf, -60, 300, false, p);
                c.drawLine(cx, cy - s * 0.42f, cx, cy - s * 0.04f, p);
                break;
            default:
                break;
        }
    }
}
