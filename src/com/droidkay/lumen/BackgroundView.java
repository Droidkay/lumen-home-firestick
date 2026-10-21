package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

/**
 * Full-screen wallpaper: either a picture or a colour gradient, then a dark overlay and soft
 * top/bottom shading so text and tiles always stay readable. Drawn once; costs nothing while browsing.
 */
final class BackgroundView extends View {
    static final int[] DIM_ALPHA = {0x14, 0x52, 0x80, 0xB0};
    static final String[] DIM_NAMES = {"Off", "Light", "Medium", "Dark"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Palette pal;
    private Bitmap image;
    private int dim = 2;
    private Shader base, glow1, glow2, glow3, topShade, bottomShade;

    BackgroundView(Context c) {
        super(c);
    }

    void setPalette(Palette p) {
        pal = p;
        build(getWidth(), getHeight());
        invalidate();
    }

    void setImage(Bitmap b) {
        image = b;
        invalidate();
    }

    Bitmap getImage() {
        return image;
    }

    void setDim(int d) {
        dim = d;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        build(w, h);
    }

    private static RadialGradient glow(float cx, float cy, float r, int color) {
        int mid = (((color >>> 24) / 3) << 24) | (color & 0x00FFFFFF);
        return new RadialGradient(cx, cy, r,
                new int[] {color, mid, color & 0x00FFFFFF},
                new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
    }

    private void build(int w, int h) {
        if (w <= 0 || h <= 0) return;
        topShade = new LinearGradient(0, 0, 0, h * 0.35f, 0x73000000, 0x00000000, Shader.TileMode.CLAMP);
        bottomShade = new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP);
        if (pal == null) return;
        base = new LinearGradient(0, 0, w * 0.35f, h, pal.top, pal.bottom, Shader.TileMode.CLAMP);
        glow1 = glow(w * 0.10f, h * 0.02f, w * 0.55f, pal.blob1);
        glow2 = glow(w * 0.95f, h * 0.30f, w * 0.45f, pal.blob2);
        glow3 = glow(w * 0.55f, h * 1.10f, w * 0.55f, pal.blob3);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (image != null) {
            c.drawBitmap(image, null, new Rect(0, 0, w, h), paint);
            c.drawColor((DIM_ALPHA[dim] << 24));
        } else if (base != null) {
            for (Shader s : new Shader[] {base, glow1, glow2, glow3}) {
                paint.setShader(s);
                c.drawRect(0, 0, w, h, paint);
            }
            paint.setShader(null);
        } else {
            c.drawColor(0xFF05060B);
        }
        if (topShade != null) {
            paint.setShader(topShade);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(bottomShade);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }
}
