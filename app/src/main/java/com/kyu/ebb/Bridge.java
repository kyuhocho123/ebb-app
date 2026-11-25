package com.kyu.ebb;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Methods the web app calls as window.EbbNative.* */
public class Bridge {
    private final MainActivity a;
    private final SharedPreferences p;

    Bridge(MainActivity activity) {
        a = activity;
        p = activity.getSharedPreferences("ebb", Context.MODE_PRIVATE);
    }

    @JavascriptInterface public String platform() { return "android"; }

    @JavascriptInterface public void save(String json) { p.edit().putString("state", json).apply(); }

    @JavascriptInterface public String load() { return p.getString("state", ""); }

    @JavascriptInterface public void setWidget(String json) {
        p.edit().putString("widget", json).commit();
        EbbWidget.refreshAll(a);
    }

    @JavascriptInterface public void schedule(String json) { Scheduler.set(a, json); }

    @JavascriptInterface public boolean hasPerm(String kind) {
        if ("notif".equals(kind)) {
            if (Build.VERSION.SDK_INT >= 33)
                return a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            NotificationManager nm = (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.areNotificationsEnabled();
        }
        return a.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            && a.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface public void requestPerm(final String kind) {
        a.runOnUiThread(() -> {
            if ("notif".equals(kind)) {
                if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                else a.js("window.ebbPerm&&ebbPerm('notif'," + hasPerm("notif") + ")");
            } else {
                a.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, 2);
            }
        });
    }

    /** Writable calendars on the phone, Google accounts first. */
    @JavascriptInterface public String calendars() {
        JSONArray out = new JSONArray();
        if (!hasPerm("cal")) return out.toString();
        String[] proj = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL};
        try (Cursor c = a.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)) {
            if (c == null) return out.toString();
            JSONArray other = new JSONArray();
            while (c.moveToNext()) {
                if (c.getInt(4) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue;
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("name", c.getString(1));
                o.put("account", c.getString(2));
                if ("com.google".equals(c.getString(3))) out.put(o); else other.put(o);
            }
            for (int i = 0; i < other.length(); i++) out.put(other.get(i));
        } catch (Exception e) { /* return what we have */ }
        return out.toString();
    }

    /** Replace Ebb's own events in the chosen calendar. Returns "OK:n" or "ERR:msg". */
    @JavascriptInterface public String syncCalendar(String calId, String json) {
        if (!hasPerm("cal")) return "ERR:no calendar permission";
        try {
            removeOwnEvents();
            long cal = Long.parseLong(calId);
            JSONArray ev = new JSONArray(json);
            JSONArray ids = new JSONArray();
            ContentResolver cr = a.getContentResolver();
            for (int i = 0; i < ev.length(); i++) {
                JSONObject e = ev.getJSONObject(i);
                LocalDate s = LocalDate.parse(e.getString("s"));
                LocalDate end = LocalDate.parse(e.getString("e")).plusDays(1);
                ContentValues v = new ContentValues();
                v.put(CalendarContract.Events.CALENDAR_ID, cal);
                v.put(CalendarContract.Events.TITLE, e.optString("t"));
                v.put(CalendarContract.Events.DESCRIPTION, e.optString("d"));
                v.put(CalendarContract.Events.DTSTART, s.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
                v.put(CalendarContract.Events.DTEND, end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
                v.put(CalendarContract.Events.ALL_DAY, 1);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
                Uri u = cr.insert(CalendarContract.Events.CONTENT_URI, v);
                if (u != null) ids.put(Long.parseLong(u.getLastPathSegment()));
            }
            p.edit().putString("calIds", ids.toString()).apply();
            return "OK:" + ids.length();
        } catch (Exception e) {
            return "ERR:" + e.getMessage();
        }
    }

    @JavascriptInterface public int clearCalendar() {
        if (!hasPerm("cal")) return 0;
        int n = removeOwnEvents();
        p.edit().putString("calIds", "[]").apply();
        return n;
    }

    private int removeOwnEvents() {
        int n = 0;
        try {
            JSONArray ids = new JSONArray(p.getString("calIds", "[]"));
            for (int i = 0; i < ids.length(); i++) {
                Uri u = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, String.valueOf(ids.getLong(i)));
                n += a.getContentResolver().delete(u, null, null);
            }
        } catch (Exception ignored) { }
        return n;
    }

    @JavascriptInterface public void share(String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        a.startActivity(Intent.createChooser(i, "Share"));
    }

    @JavascriptInterface public void open(String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
    }
}
