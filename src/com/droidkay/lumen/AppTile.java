package com.droidkay.lumen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * App tile with an Apple TV-like feel: the selected card lifts, grows, tilts in 3D (parallax),
 * a band of light sweeps across it and it keeps gently swaying while it stays selected.
 */
final class AppTile extends LinearLayout {
    /** Direction of the last D-pad move (-1/0/1), set by the activity so the tilt "follows" you. */
    static int lastDx, lastDy;

    final AppRepo.App app;
    final String section;

    private final FrameLayout card;
    private final View gloss;
    private final View sweep;
    private final View edge;
    private final View dim;
    private final TextView label;
    private final float density;
    private final boolean alwaysLabel;
    private final int cardW;
    private ValueAnimator sway;

    AppTile(Context c, AppRepo.App app, String section, int wPx, int hPx, boolean alwaysLabel) {
        super(c);
        this.app = app;
        this.section = section;
        this.alwaysLabel = alwaysLabel;
        this.cardW = wPx;
        this.density = c.getResources().getDisplayMetrics().density;

        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription(app.label);

        float radius = dp(16);

        card = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {lighten(app.tint, 30), app.tint});
        bg.setCornerRadius(radius);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(dp(2));
        card.setCameraDistance(9000 * density);

        if (app.banner != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (app.icon != null) iv.setImageDrawable(app.icon);
            int s = (int) (hPx * 0.50f);
            card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }

        dim = new View(c);
        dim.setBackgroundColor(0x24000000);
        card.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        gloss = new View(c);
        GradientDrawable gl = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x40FFFFFF, 0x0AFFFFFF, 0x00FFFFFF});
        gl.setCornerRadius(radius);
        gloss.setBackground(gl);
        gloss.setAlpha(0f);
        card.addView(gloss, new FrameLayout.LayoutParams(-1, -1));

        sweep = new View(c);
        GradientDrawable sw = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x00FFFFFF, 0x3DFFFFFF, 0x00FFFFFF});
        sweep.setBackground(sw);
        sweep.setRotation(18f);
        sweep.setAlpha(0f);
        int sweepW = (int) (wPx * 0.45f);
        FrameLayout.LayoutParams swl = new FrameLayout.LayoutParams(sweepW, (int) (hPx * 1.8f), Gravity.CENTER);
        card.addView(sweep, swl);

        edge = new View(c);
        GradientDrawable eg = new GradientDrawable();
        eg.setColor(0x00000000);
        eg.setCornerRadius(radius);
        eg.setStroke(Math.max(2, (int) dp(1.5f)), 0x8CFFFFFF);
        edge.setBackground(eg);
        edge.setAlpha(0f);
        card.addView(edge, new FrameLayout.LayoutParams(-1, -1));

        addView(card, new LayoutParams(wPx, hPx));

        label = new TextView(c);
        label.setText(app.label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTextColor(0xFFFFFFFF);
        label.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setAlpha(alwaysLabel ? 0.7f : 0f);
        LayoutParams lp = new LayoutParams(wPx, LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) dp(14);
        addView(label, lp);

        setPivotX(wPx / 2f);
        setPivotY(hPx / 2f);

        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyFocus(hasFocus);
            }
        });
    }

    private static int lighten(int c, int amt) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + amt);
        int g = Math.min(255, ((c >> 8) & 0xFF) + amt);
        int b = Math.min(255, (c & 0xFF) + amt);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSway();
        super.onDetachedFromWindow();
    }

    private void stopSway() {
        if (sway != null) {
            sway.cancel();
            sway = null;
        }
    }

    private void applyFocus(boolean f) {
        float s = f ? 1.12f : 1.0f;
        animate().scaleX(s).scaleY(s).translationY(f ? -dp(4) : 0f)
                .setDuration(f ? 240 : 160)
                .setInterpolator(f ? new OvershootInterpolator(1.1f) : new DecelerateInterpolator())
                .start();
        card.animate().translationZ(f ? dp(20) : 0f).setDuration(220).start();
        gloss.animate().alpha(f ? 1f : 0f).setDuration(200).start();
        edge.animate().alpha(f ? 1f : 0f).setDuration(200).start();
        dim.animate().alpha(f ? 0f : 1f).setDuration(200).start();
        label.animate().alpha(f ? 1f : (alwaysLabel ? 0.7f : 0f)).setDuration(180).start();

        stopSway();
        if (f) {
            // 1. tilt in from the direction you came, like the card is leaning toward you
            card.setRotationY(lastDx * -9f);
            card.setRotationX(lastDy * 7f);
            card.animate().rotationX(0f).rotationY(0f).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.6f)).start();

            // 2. a band of light sweeps across once
            sweep.setTranslationX(lastDx >= 0 ? -cardW * 0.9f : cardW * 0.9f);
            sweep.setAlpha(1f);
            sweep.animate().translationX(lastDx >= 0 ? cardW * 0.9f : -cardW * 0.9f)
                    .setDuration(700).setStartDelay(60).setInterpolator(new DecelerateInterpolator())
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (isFocused()) startSway();
                        }
                    }).start();
        } else {
            sweep.animate().cancel();
            sweep.animate().setStartDelay(0).alpha(0f).setDuration(150).start();
            card.animate().rotationX(0f).rotationY(0f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    /** Gentle, slow parallax sway while the tile stays selected; the light drifts with it. */
    private void startSway() {
        stopSway();
        sway = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        sway.setDuration(7000);
        sway.setRepeatCount(ValueAnimator.INFINITE);
        sway.setInterpolator(new LinearInterpolator());
        final float phase = (float) (Math.random() * Math.PI);
        sway.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                float ry = (float) Math.sin(t + phase) * 3.5f;
                float rx = (float) Math.sin(2 * t + phase) * 1.8f;
                card.setRotationY(ry);
                card.setRotationX(rx);
                sweep.setTranslationX(-ry / 3.5f * cardW * 0.35f);
                sweep.setAlpha(0.35f + 0.25f * (float) Math.abs(Math.sin(t + phase)));
            }
        });
        sway.start();
    }
}
