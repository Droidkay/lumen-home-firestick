package com.droidkay.lumen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * "Liquid Glass" surface. Each glass shape samples a blurred copy of the wallpaper that sits
 * exactly behind it on screen, adds a soft tint, a bright top sheen and a light-catching rim,
 * so it looks like thick frosted glass floating over the background.
 */
final class Glass extends Drawable {

    // ------------------------------------------------------------ shared backdrop

    private static Bitmap backdrop;
    private static int screenW = 1, screenH = 1;
    private static final ArrayList<WeakReference<View>> HOSTS = new ArrayList<WeakReference<View>>();

    /** Sets the blurred background every glass surface refracts. */
    static void setBackdrop(Bitmap b, int w, int h) {
        backdrop = b;
        screenW = Math.max(1, w);
        screenH = Math.max(1, h);
        invalidateAll();
    }

    /** Call when things move (scrolling) so glass re-samples what's behind it. */
    static void invalidateAll() {
        Iterator<WeakReference<View>> it = HOSTS.iterator();
        while (it.hasNext()) {
            View v = it.next().get();
            if (v == null) it.remove();
            else v.invalidate();
        }
    }

    /** Attaches a glass background to a view. */
    static Glass apply(View v, float radiusPx, float density, int tint) {
        Glass g = new Glass(v, radiusPx, density, tint);
        v.setBackground(g);
        HOSTS.add(new WeakReference<View>(v));
        return g;
    }

    // ------------------------------------------------------------ instance

    static final int TINT_DARK = 0x38141820;
    static final int TINT_LIGHT = 0x26FFFFFF;
    static final int TINT_PANEL = 0x8C161A22;

    private final View host;
    private final float radius;
    private final float density;
    private int tint;
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rf = new RectF();
    private final Rect src = new Rect();
    private final int[] loc = new int[2];
    private int lastW, lastH;

    private Glass(View host, float radius, float density, int tint) {
        this.host = host;
        this.radius = radius;
        this.density = density;
        this.tint = tint;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 1.2f * density));
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(Math.max(1f, 3f * density));
    }

    void setGlassTint(int t) {
        tint = t;
        invalidateSelf();
    }

    private void shaders(int w, int h) {
        if (w == lastW && h == lastH) return;
        lastW = w;
        lastH = h;
        // bright top band, like light pooling on the upper edge of a glass slab
        sheen.setShader(new LinearGradient(0, 0, 0, Math.min(h, 46 * density),
                0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        // rim catches light top-left and bottom-right, darker in between
        rim.setShader(new LinearGradient(0, 0, w, h,
                new int[] {0xB3FFFFFF, 0x1AFFFFFF, 0x0DFFFFFF, 0x59FFFFFF},
                new float[] {0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        // faint inner glow just inside the rim, gives the glass thickness
        inner.setShader(new LinearGradient(0, 0, 0, h,
                new int[] {0x1FFFFFFF, 0x00FFFFFF, 0x14000000},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        shaders(w, h);

        rf.set(b);
        clip.reset();
        clip.addRoundRect(rf, radius, radius, Path.Direction.CW);

        int save = c.save();
        c.clipPath(clip);

        Bitmap bd = backdrop;
        if (bd != null && !bd.isRecycled()) {
            host.getLocationOnScreen(loc);
            float sx = bd.getWidth() / (float) screenW, sy = bd.getHeight() / (float) screenH;
            // undo the host's own scale so the sampled backdrop stays put while tiles animate
            float scaleX = host.getScaleX() == 0 ? 1 : host.getScaleX();
            float scaleY = host.getScaleY() == 0 ? 1 : host.getScaleY();
            float left = loc[0], top = loc[1];
            float right = left + w * scaleX, bottom = top + h * scaleY;
            src.set(Math.round(left * sx), Math.round(top * sy), Math.round(right * sx), Math.round(bottom * sy));
            c.drawBitmap(bd, src, b, bmp);
        }
        fill.setColor(tint);
        c.drawRect(b, fill);
        c.drawRect(b, sheen);
        float ii = inner.getStrokeWidth() / 2f;
        rf.set(b.left + ii, b.top + ii, b.right - ii, b.bottom - ii);
        c.drawRoundRect(rf, radius, radius, inner);
        c.restoreToCount(save);

        float hw = rim.getStrokeWidth() / 2f;
        rf.set(b.left + hw, b.top + hw, b.right - hw, b.bottom - hw);
        c.drawRoundRect(rf, radius, radius, rim);
    }

    @Override
    public void getOutline(android.graphics.Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(0.5f);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
