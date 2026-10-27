package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Calendar;

/** Minimal analog clock face for the top shelf: tick marks, two capsule hands and an accent dot. */
final class AnalogClockView extends View {
    static final int ACCENT = 0xFFF2553F;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    AnalogClockView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        tick.setStrokeCap(Paint.Cap.ROUND);
        hand.setStrokeCap(Paint.Cap.ROUND);
        hand.setColor(0xFFFFFFFF);
        dot.setColor(ACCENT);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = s / 2f;

        tick.setStrokeWidth(Math.max(1f, 1.6f * density));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI / 6 * i;
            boolean major = i % 3 == 0;
            tick.setColor(major ? 0xCCFFFFFF : 0x66FFFFFF);
            float inner = r * (major ? 0.74f : 0.80f), outer = r * 0.88f;
            c.drawLine(cx + (float) Math.sin(a) * inner, cy - (float) Math.cos(a) * inner,
                    cx + (float) Math.sin(a) * outer, cy - (float) Math.cos(a) * outer, tick);
        }

        Calendar now = Calendar.getInstance();
        int m = now.get(Calendar.MINUTE);
        float h = (now.get(Calendar.HOUR) % 12) + m / 60f;
        double ha = Math.PI * 2 * h / 12.0, ma = Math.PI * 2 * m / 60.0;

        hand.setStrokeWidth(Math.max(2f, 4.2f * density));
        c.drawLine(cx, cy, cx + (float) Math.sin(ha) * r * 0.46f, cy - (float) Math.cos(ha) * r * 0.46f, hand);
        hand.setStrokeWidth(Math.max(2f, 3f * density));
        c.drawLine(cx, cy, cx + (float) Math.sin(ma) * r * 0.68f, cy - (float) Math.cos(ma) * r * 0.68f, hand);
        c.drawCircle(cx, cy, 3.4f * density, dot);
    }
}
