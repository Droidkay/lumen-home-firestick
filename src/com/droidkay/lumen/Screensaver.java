package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Apple TV-style screensaver: full-screen pictures that drift and slowly zoom (the "Ken Burns" look),
 * cross-fading every 40 seconds, with a big thin clock, the date, weather and battery.
 * The clock moves between corners every few minutes so nothing burns into the TV.
 */
final class Screensaver extends View {
    static final int[] MINUTES = {0, 2, 3, 5, 10, 15};
    static final int DEFAULT_MINUTES = 3;

    private static final long SLIDE_MS = 40_000, FADE_MS = 2_500, CORNER_MS = 4 * 60_000;

    private final List<Object> sources = new ArrayList<Object>(); // Integer drawable ids or File
    private final List<String> names = new ArrayList<String>();
    private Bitmap cur, next;
    private int index;
    private long slideStart, startedAt;
    private boolean loadingNext;
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint timeP = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG),
            tiny = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint();
    private final Matrix m = new Matrix();
    private final float density;
    private boolean running;

    Screensaver(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF000000);
        timeP.setColor(0xFFFFFFFF);
        timeP.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        timeP.setShadowLayer(18 * density, 0, 2 * density, 0x66000000);
        small.setColor(0xF2FFFFFF);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setShadowLayer(10 * density, 0, 1 * density, 0x80000000);
        tiny.setColor(0xB3FFFFFF);
        tiny.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tiny.setShadowLayer(8 * density, 0, 1 * density, 0x80000000);
        tiny.setLetterSpacing(0.08f);

        // your own wallpaper first, then the built-in scenes
        File custom = Wallpaper.customFile(c);
        if (custom.exists()) {
            sources.add(custom);
            names.add("My picture");
        }
        for (int i = 0; i < Wallpaper.SCENES.length; i++) {
            int res = c.getResources().getIdentifier(Wallpaper.SCENES[i], "drawable", c.getPackageName());
            if (res != 0) {
                sources.add(res);
                names.add(Wallpaper.SCENE_NAMES[i]);
            }
        }
    }

    void start() {
        running = true;
        startedAt = SystemClock.uptimeMillis();
        slideStart = startedAt;
        index = (int) ((System.currentTimeMillis() / SLIDE_MS) % Math.max(1, sources.size()));
        if (cur == null) cur = load(index);
        next = null;
        invalidate();
    }

    void stop() {
        running = false;
        if (cur != null) cur.recycle();
        if (next != null) next.recycle();
        cur = null;
        next = null;
    }

    private Bitmap load(int i) {
        if (sources.isEmpty()) return null;
        Object s = sources.get(((i % sources.size()) + sources.size()) % sources.size());
        int w = Math.max(1920, getWidth()), h = Math.max(1080, getHeight());
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory; fine for photos on a Firestick
            Bitmap b = s instanceof File ? BitmapFactory.decodeFile(((File) s).getAbsolutePath(), o)
                    : BitmapFactory.decodeResource(getResources(), (Integer) s, o);
            if (b == null) return null;
            // keep it no bigger than ~1.25× the screen so zooming has room without wasting memory
            float sc = Math.max(w * 1.25f / b.getWidth(), h * 1.25f / b.getHeight());
            if (sc < 1f) {
                Bitmap r = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * sc), Math.round(b.getHeight() * sc), true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private void preloadNext() {
        if (loadingNext || next != null) return;
        loadingNext = true;
        final int want = index + 1;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = load(want);
                post(new Runnable() {
                    @Override
                    public void run() {
                        loadingNext = false;
                        if (running) next = b;
                        else if (b != null) b.recycle();
                    }
                });
            }
        }, "lumen-saver").start();
    }

    /** Draws one picture filling the screen, zoomed and panned by t (0…1) in a direction picked per slide. */
    private void drawKenBurns(Canvas c, Bitmap b, float t, int slide, int alpha) {
        if (b == null) return;
        float w = getWidth(), h = getHeight();
        float base = Math.max(w / b.getWidth(), h / b.getHeight());
        boolean zoomIn = slide % 2 == 0;
        float z = zoomIn ? 1.04f + 0.10f * t : 1.14f - 0.10f * t;
        float s = base * z;
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        float spareX = bw - w, spareY = bh - h;
        // pan direction rotates through four diagonals
        int d = slide % 4;
        float px = (d == 0 || d == 3) ? t : 1 - t;
        float py = (d < 2) ? t : 1 - t;
        m.reset();
        m.postScale(s, s);
        m.postTranslate(-spareX * (0.2f + 0.6f * px), -spareY * (0.2f + 0.6f * py));
        img.setAlpha(alpha);
        c.drawBitmap(b, m, img);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        long into = now - slideStart;
        if (cur == null && !sources.isEmpty()) cur = load(index);

        if (into > SLIDE_MS - FADE_MS - 4000) preloadNext();
        float t = Math.min(1f, into / (float) (SLIDE_MS + FADE_MS));
        drawKenBurns(c, cur, t, index, 255);
        if (into > SLIDE_MS - FADE_MS && next != null) {
            float f = Math.min(1f, (into - (SLIDE_MS - FADE_MS)) / (float) FADE_MS);
            float tn = (into - (SLIDE_MS - FADE_MS)) / (float) (SLIDE_MS + FADE_MS);
            drawKenBurns(c, next, tn, index + 1, Math.round(255 * f));
            if (f >= 1f) {
                if (cur != null) cur.recycle();
                cur = next;
                next = null;
                index++;
                slideStart = now - FADE_MS;
            }
        } else if (into > SLIDE_MS + FADE_MS) {
            // couldn't load the next one: keep this picture and start its drift again
            slideStart = now;
        }

        drawInfo(c, now);
        // gentle fade in at the start
        long since = now - startedAt;
        if (since < 1200) {
            shade.setColor(0xFF000000);
            shade.setAlpha(Math.round(255 * (1 - since / 1200f)));
            c.drawRect(0, 0, getWidth(), getHeight(), shade);
        }
        postInvalidateOnAnimation();
    }

    private void drawInfo(Canvas c, long now) {
        float w = getWidth(), h = getHeight();
        float pad = 56 * density;
        int corner = (int) (((now - startedAt) / CORNER_MS) % 4);
        boolean right = corner == 1 || corner == 2;
        boolean top = corner == 2 || corner == 3;

        // soft dark wash behind the text so it reads on any picture
        float washH = 260 * density;
        LinearGradient lg = top ? new LinearGradient(0, 0, 0, washH, 0x8C000000, 0x00000000, Shader.TileMode.CLAMP)
                : new LinearGradient(0, h - washH, 0, h, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP);
        shade.setShader(lg);
        shade.setAlpha(255);
        c.drawRect(0, top ? 0 : h - washH, w, top ? washH : h, shade);
        shade.setShader(null);

        Date d = new Date();
        Locale loc = Locale.getDefault();
        int mode = Prefs.clockMode(getContext());
        boolean h24 = mode == -1 ? DateFormat.is24HourFormat(getContext()) : mode == 1;
        String time = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", loc).format(d);
        String date = new SimpleDateFormat("EEEE, d MMMM", loc).format(d);

        String extra = "";
        Weather.Now wn = Weather.cached(getContext());
        if (wn != null && wn.city == Prefs.city(getContext())) {
            extra = Math.round(wn.temp) + "°  " + Weather.describe(wn.code) + "  ·  " + Weather.CITIES[wn.city].name;
        }
        String power = PowerCache.line();
        if (power != null) extra = extra.isEmpty() ? power : extra + "     " + power;

        timeP.setTextSize(112 * density);
        small.setTextSize(24 * density);
        tiny.setTextSize(15 * density);
        Paint.Align a = right ? Paint.Align.RIGHT : Paint.Align.LEFT;
        timeP.setTextAlign(a);
        small.setTextAlign(a);
        tiny.setTextAlign(a);
        float x = right ? w - pad : pad;

        float timeH = -timeP.ascent();
        float yTime, yDate, yExtra;
        if (top) {
            yTime = pad + timeH * 0.82f;
            yDate = yTime + 40 * density;
            yExtra = yDate + 30 * density;
        } else {
            yExtra = h - pad;
            yDate = extra.isEmpty() ? yExtra : yExtra - 30 * density;
            yTime = yDate - 40 * density;
        }
        c.drawText(time, x, yTime, timeP);
        c.drawText(date, x, yDate, small);
        if (!extra.isEmpty()) c.drawText(extra, x, yExtra, tiny);

        // picture name in the opposite bottom corner, like Apple's aerial locations
        if (!names.isEmpty()) {
            String n = names.get(((index % names.size()) + names.size()) % names.size()).toUpperCase(loc);
            tiny.setTextAlign(right ? Paint.Align.LEFT : Paint.Align.RIGHT);
            c.drawText(n, right ? pad : w - pad, top ? h - pad : pad + 15 * density, tiny);
        }
    }
}
