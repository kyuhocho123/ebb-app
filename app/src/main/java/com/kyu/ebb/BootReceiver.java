package com.kyu.ebb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are wiped on reboot; put them back. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        Scheduler.arm(c, Scheduler.prefs(c).getString("sched", "[]"));
        EbbWidget.refreshAll(c);
    }
}
