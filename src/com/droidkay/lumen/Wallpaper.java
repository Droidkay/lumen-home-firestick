package com.droidkay.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Loads, imports and blurs wallpapers. All heavy work is meant for a background thread. */
final class Wallpaper {
    private Wallpaper() {}

    static final String GRADIENT = "gradient";
    static final String CUSTOM = "custom";
    static final String[] SCENES = {"scene_peaks", "scene_aurora", "scene_nebula", "scene_ocean"};
    static final String[] SCENE_NAMES = {"Dusk Peaks", "Aurora", "Nebula", "Ocean Night"};

    static String nameOf(String id) {
        if (GRADIENT.equals(id)) return "Colour gradient";
        if (CUSTOM.equals(id)) return "My picture";
        for (int i = 0; i < SCENES.length; i++) if (SCENES[i].equals(id)) return SCENE_NAMES[i];
        return "Dusk Peaks";
    }

    static File customFile(Context c) {
        return new File(c.getFilesDir(), "wallpaper.jpg");
    }

    /** Returns the current wallpaper cropped to w x h, or null for the colour gradient. */
    static Bitmap load(Context c, int w, int h) {
        String id = Prefs.wallpaper(c);
        if (GRADIENT.equals(id)) return null;
        try {
            if (CUSTOM.equals(id)) {
                File f = customFile(c);
                if (!f.exists()) return null;
                return crop(decodeFile(f.getAbsolutePath(), w, h, 0), w, h);
            }
            int res = c.getResources().getIdentifier(id, "drawable", c.getPackageName());
            if (res == 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return crop(BitmapFactory.decodeResource(c.getResources(), res, o), w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decodeFile(String path, int w, int h, int rotation) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, b);
        if (b.outWidth <= 0 || b.outHeight <= 0) return null;
        int sample = 1;
        int sw = rotation % 180 == 0 ? b.outWidth : b.outHeight;
        int sh = rotation % 180 == 0 ? b.outHeight : b.outWidth;
        while (sw / (sample * 2) >= w && sh / (sample * 2) >= h) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(path, o);
        if (bmp != null && rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (r != bmp) bmp.recycle();
            bmp = r;
        }
        return bmp;
    }

    /** Centre-crops and scales to exactly w x h. */
    static Bitmap crop(Bitmap src, int w, int h) {
        if (src == null) return null;
        if (src.getWidth() == w && src.getHeight() == h) return src;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        cv.drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h), p);
        src.recycle();
        return out;
    }

    /** Small, heavily blurred copy for frosted-glass backdrops. Draw it scaled up with filtering. */
    static Bitmap blur(Bitmap src) {
        if (src == null) return null;
        int w = Math.max(16, src.getWidth() / 12), h = Math.max(9, src.getHeight() / 12);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            px = boxBlur(px, w, h, 2, true);
            px = boxBlur(px, w, h, 2, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (small != src) small.recycle();
        return out;
    }

    /** Blurs a small bitmap in place (3 box passes each way) and returns it. */
    static Bitmap blurInPlace(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            px = boxBlur(px, w, h, 2, true);
            px = boxBlur(px, w, h, 2, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private static int[] boxBlur(int[] in, int w, int h, int r, boolean horizontal) {
        int[] out = new int[in.length];
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = horizontal ? in[line * w + j] : in[j * w + line];
                    rs += (p >> 16) & 0xFF;
                    gs += (p >> 8) & 0xFF;
                    bs += p & 0xFF;
                    n++;
                }
                int v = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
                if (horizontal) out[line * w + i] = v;
                else out[i * w + line] = v;
            }
        }
        return out;
    }

    // ------------------------------------------------------------ own pictures

    /** Pictures on the Fire TV that can be used as wallpaper, newest first. */
    static List<File> findImages() {
        ArrayList<File> out = new ArrayList<File>();
        File root = Environment.getExternalStorageDirectory();
        scan(root, 0, out);
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        while (out.size() > 60) out.remove(out.size() - 1);
        return out;
    }

    private static void scan(File dir, int depth, List<File> out) {
        if (dir == null || depth > 3 || out.size() > 300) return;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            return;
        }
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth == 0 && (n.equals("Android") || n.equals("data") || n.equals("obb"))) continue;
                scan(f, depth + 1, out);
            } else {
                String l = n.toLowerCase(Locale.US);
                if ((l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp"))
                        && f.length() > 20 * 1024) {
                    out.add(f);
                }
            }
        }
    }

    /** Copies a picture into Lumen's own storage, sized for the TV. Returns true on success. */
    static boolean importImage(Context c, File f, int w, int h) {
        FileOutputStream os = null;
        try {
            int rot = 0;
            try {
                ExifInterface ex = new ExifInterface(f.getAbsolutePath());
                int o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Exception ignored) {
            }
            Bitmap b = crop(decodeFile(f.getAbsolutePath(), w, h, rot), w, h);
            if (b == null) return false;
            File tmp = new File(c.getFilesDir(), "wallpaper.tmp");
            os = new FileOutputStream(tmp);
            b.compress(Bitmap.CompressFormat.JPEG, 92, os);
            os.close();
            os = null;
            b.recycle();
            return tmp.renameTo(customFile(c));
        } catch (Throwable t) {
            return false;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
