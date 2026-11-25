package com.kyu.ebb;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
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
import java.util.ArrayList;
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

    private static final String TAG = "com.kyu.ebb";

    /** Replace Ebb's own events in the chosen calendar. Returns "OK:n" or "ERR:msg". */
    @JavascriptInterface public String syncCalendar(String calId, String json) {
        if (!hasPerm("cal")) return "ERR:no calendar permission";
        try {
            removeOwnEvents();
            long cal = Long.parseLong(calId);
            JSONArray ev = new JSONArray(json);
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            for (int i = 0; i < ev.length(); i++) {
                JSONObject e = ev.getJSONObject(i);
                LocalDate s = LocalDate.parse(e.getString("s"));
                LocalDate end = LocalDate.parse(e.getString("e")).plusDays(1);
                ops.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValue(CalendarContract.Events.CALENDAR_ID, cal)
                    .withValue(CalendarContract.Events.TITLE, e.optString("t"))
                    .withValue(CalendarContract.Events.DESCRIPTION, e.optString("d"))
                    .withValue(CalendarContract.Events.DTSTART, s.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    .withValue(CalendarContract.Events.DTEND, end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    .withValue(CalendarContract.Events.ALL_DAY, 1)
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    .withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, TAG)
                    .withValue(CalendarContract.Events.CUSTOM_APP_URI, "ebb://event")
                    .build());
            }
            ContentProviderResult[] res = a.getContentResolver().applyBatch(CalendarContract.AUTHORITY, ops);
            return "OK:" + res.length;
        } catch (Exception e) {
            return "ERR:" + e.getMessage();
        }
    }

    @JavascriptInterface public int clearCalendar() {
        if (!hasPerm("cal")) return 0;
        return removeOwnEvents();
    }

    /**
     * Deletes only events Ebb created: those tagged with Ebb's package name.
     * Untagged events from older builds are removed by stored ID, but only on the phone that
     * created them (IDs live in device-only prefs) and only if they still look like Ebb's.
     */
    private int removeOwnEvents() {
        ContentResolver cr = a.getContentResolver();
        int n = 0;
        try {
            n += cr.delete(CalendarContract.Events.CONTENT_URI,
                CalendarContract.Events.CUSTOM_APP_PACKAGE + "=?", new String[]{TAG});
        } catch (Exception ignored) { }
        SharedPreferences dev = Scheduler.device(a);
        String legacy = dev.getString("calIds", null);
        if (legacy == null && !dev.getBoolean("legacyChecked", false)) legacy = p.getString("calIds", "[]");
        try {
            JSONArray ids = new JSONArray(legacy == null ? "[]" : legacy);
            String sel = CalendarContract.Events._ID + "=? AND (" + CalendarContract.Events.DESCRIPTION + " LIKE '%Ebb%' OR "
                + CalendarContract.Events.DESCRIPTION + " LIKE 'Estimate only%' OR " + CalendarContract.Events.TITLE + " LIKE '%(est.)%' OR "
                + CalendarContract.Events.TITLE + " LIKE '%anniversary%' OR " + CalendarContract.Events.TITLE + " LIKE '%days')";
            for (int i = 0; i < ids.length(); i++)
                n += cr.delete(CalendarContract.Events.CONTENT_URI, sel, new String[]{String.valueOf(ids.getLong(i))});
        } catch (Exception ignored) { }
        dev.edit().remove("calIds").putBoolean("legacyChecked", true).apply();
        p.edit().remove("calIds").apply();
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
