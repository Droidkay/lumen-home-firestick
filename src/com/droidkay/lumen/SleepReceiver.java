package com.droidkay.lumen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Fires when the sleep timer ends. */
public class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs.sp(c).edit().remove("sleepAt").apply();
        if (!HomeWatcherService.sleep()) {
            Toast.makeText(c, "Sleep timer ended. Turn on Lumen's Home-button service so it can sleep the TV.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
