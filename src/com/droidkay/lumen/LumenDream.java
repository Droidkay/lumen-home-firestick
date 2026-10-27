package com.droidkay.lumen;

import android.service.dreams.DreamService;

/**
 * The same screensaver as a system "Daydream", so it can also show over other apps on devices that allow it.
 * On Fire OS it's switched on with ADB (see README).
 */
public class LumenDream extends DreamService {
    private Screensaver saver;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        saver = new Screensaver(this);
        setContentView(saver);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (saver != null) saver.start();
    }

    @Override
    public void onDreamingStopped() {
        if (saver != null) saver.stop();
        super.onDreamingStopped();
    }
}
