package com.droidkay.lumen;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.List;

/**
 * HDMI / AV inputs as the TV input framework reports them. A Fire TV Edition television lists its
 * HDMI ports here and can switch to them; a Fire TV Stick has no inputs of its own (it IS an HDMI
 * source), so the list is empty there and the Inputs widget says so.
 */
final class Inputs {
    private Inputs() {}

    static final class Input {
        final String id, label;

        Input(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** Pass-through inputs (HDMI, composite, component, ...) in the order the system lists them. */
    static List<Input> list(Context c) {
        ArrayList<Input> out = new ArrayList<Input>();
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            int n = 0;
            for (TvInputInfo i : tim.getTvInputList()) {
                if (!i.isPassthroughInput()) continue;
                n++;
                String label = null;
                try {
                    CharSequence l = i.loadCustomLabel(c);
                    if (l == null || l.length() == 0) l = i.loadLabel(c);
                    if (l != null && l.length() > 0) label = l.toString();
                } catch (Throwable ignored) {
                }
                if (label == null) label = i.getType() == TvInputInfo.TYPE_HDMI ? "HDMI " + n : "Input " + n;
                out.add(new Input(i.getId(), label));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Switches the TV to this input. Returns false if nothing on the device can show it. */
    static boolean open(Context c, Input in) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** One-line summary for the widget. */
    static String summary(List<Input> l) {
        if (l.isEmpty()) return "None on this device";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 3; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(l.get(i).label);
        }
        return sb.toString();
    }
}
