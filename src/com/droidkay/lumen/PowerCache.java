package com.droidkay.lumen;

/** The latest inverter reading, so the screensaver can show the battery without asking the inverter itself. */
final class PowerCache {
    private PowerCache() {
    }

    private static volatile Reading last;
    private static volatile long at;

    static void set(Reading r) {
        last = r;
        at = System.currentTimeMillis();
    }

    /** "Battery 85%  ·  NEPA on", or null if there's no recent reading. */
    static String line() {
        Reading r = last;
        if (r == null || System.currentTimeMillis() - at > 10 * 60_000L || Double.isNaN(r.soc)) return null;
        return "Battery " + Math.round(r.soc) + "%  ·  " + (r.gridOn() ? "NEPA on" : "No NEPA");
    }
}
