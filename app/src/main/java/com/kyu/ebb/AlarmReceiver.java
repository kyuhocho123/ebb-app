package com.kyu.ebb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("ebb", "Reminders", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String body = i.getStringExtra("b");
        Notification n = new Notification.Builder(c, "ebb")
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFB8144F)
            .setContentTitle(i.getStringExtra("t"))
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build();
        try { nm.notify(i.getIntExtra("id", 1), n); } catch (SecurityException ignored) { }
        EbbWidget.refreshAll(c);
    }
}
