package com.kyu.ebb;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Turns the web app's reminder list into AlarmManager alarms. */
public class Scheduler {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ebb", Context.MODE_PRIVATE); }

    static void set(Context c, String json) {
        cancelAll(c);
        prefs(c).edit().putString("sched", json).apply();
        arm(c, json);
    }

    static void arm(Context c, String json) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            JSONArray arr = new JSONArray(json);
            JSONArray ids = new JSONArray();
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.getLong("at");
                if (at <= now) continue;
                int id = o.getInt("id");
                Intent it = new Intent(c, AlarmReceiver.class)
                    .putExtra("t", o.optString("t"))
                    .putExtra("b", o.optString("b"))
                    .putExtra("id", id);
                PendingIntent pi = PendingIntent.getBroadcast(c, id, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                ids.put(id);
            }
            prefs(c).edit().putString("schedIds", ids.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void cancelAll(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            JSONArray ids = new JSONArray(prefs(c).getString("schedIds", "[]"));
            for (int i = 0; i < ids.length(); i++) {
                PendingIntent pi = PendingIntent.getBroadcast(c, ids.getInt(i), new Intent(c, AlarmReceiver.class),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            }
        } catch (Exception ignored) { }
    }
}
