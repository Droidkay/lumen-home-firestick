package com.droidkay.lumen;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional. When enabled, it notices the Amazon home screen appearing (Home button, start-up,
 * leaving an app) and brings Lumen to the front instead. It only listens to the Amazon home
 * screen package (set in res/xml/home_watcher.xml) and never reads screen content.
 */
public class HomeWatcherService extends AccessibilityService {
    private long lastLaunch;
    private static HomeWatcherService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    /** Puts the TV to sleep (Fire OS 7+), using the Home-button service. Returns false if unavailable. */
    static boolean sleep() {
        HomeWatcherService s = instance;
        if (s == null || android.os.Build.VERSION.SDK_INT < 28) return false;
        try {
            return s.performGlobalAction(8 /* GLOBAL_ACTION_LOCK_SCREEN */);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !AppRepo.AMAZON_HOME.equals(pkg.toString())) return;
        if (!Prefs.takeover(this)) return;
        if (System.currentTimeMillis() < Prefs.pauseUntil(this)) return;

        // Fire TV Settings (and its sub-screens) can run inside Amazon's home app: never bounce those.
        CharSequence cls = e.getClassName();
        String c = cls == null ? "" : cls.toString().toLowerCase(java.util.Locale.US);
        if (c.contains("setting")) return;
        // Ignore the brief flashes right after Lumen opened Settings.
        try {
            long opened = Long.parseLong(Prefs.getStr(this, "settingsOpenedAt", "0"));
            if (System.currentTimeMillis() - opened < 4000) return;
        } catch (Exception ignored) {
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 700) return;
        lastLaunch = now;

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra(MainActivity.EXTRA_FROM_HOME, true);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
