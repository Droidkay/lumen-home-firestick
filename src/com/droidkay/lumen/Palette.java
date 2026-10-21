package com.droidkay.lumen;

/** Background themes. Colours are ARGB. */
final class Palette {
    final String name;
    final int top, bottom, blob1, blob2, blob3, accent;

    private Palette(String name, int top, int bottom, int blob1, int blob2, int blob3, int accent) {
        this.name = name;
        this.top = top;
        this.bottom = bottom;
        this.blob1 = blob1;
        this.blob2 = blob2;
        this.blob3 = blob3;
        this.accent = accent;
    }

    static final Palette[] ALL = new Palette[] {
        new Palette("Midnight", 0xFF0D1024, 0xFF04050B, 0x996C5CE7, 0x803D7BFF, 0x55FF5CA8, 0xFF9D8CFF),
        new Palette("Aurora",   0xFF06191C, 0xFF02090B, 0x8820C997, 0x7014B8D9, 0x50A3E635, 0xFF35EBB8),
        new Palette("Ember",    0xFF1C0E0A, 0xFF080404, 0x99FF6B3D, 0x70FF3D6B, 0x55FFC23D, 0xFFFF9157),
        new Palette("Ocean",    0xFF041226, 0xFF02060F, 0x880077FF, 0x7000C2FF, 0x506A5CFF, 0xFF4DB3FF),
        new Palette("Graphite", 0xFF17191D, 0xFF08090B, 0x40A0AEC0, 0x3060708A, 0x28FFFFFF, 0xFFF1F3F5),
    };
}
