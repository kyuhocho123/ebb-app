package com.kyu.ebb;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/** Home-screen widget: D+ count, months together, her next period, next anniversary. */
public class EbbWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) draw(c, m, id);
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, EbbWidget.class));
        for (int id : ids) draw(c, m, id);
    }

    private static String days(long n) {
        if (n == 0) return "today";
        if (n == 1) return "tomorrow";
        return "in " + n + " days";
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        String top = "Ebb", sub = "Open Ebb to set up", per = "", ann = "";
        try {
            JSONObject w = new JSONObject(Scheduler.prefs(c).getString("widget", "{}"));
            LocalDate t = LocalDate.now();
            String name = w.optString("name", "");
            String start = w.optString("start", "");
            if (start.length() == 10) {
                LocalDate s = LocalDate.parse(start);
                long d = ChronoUnit.DAYS.between(s, t) + 1;
                Period pd = Period.between(s, t);
                top = "Day " + d;
                int yy = w.optInt("ty", -1) >= 0 ? w.optInt("ty") : pd.getYears();
                int mo = w.optInt("tm", -1) >= 0 ? w.optInt("tm") : pd.getMonths();
                sub = (yy > 0 ? yy + (yy == 1 ? " yr " : " yrs ") : "")
                    + mo + " mo" + (name.isEmpty() ? "" : " · " + name);
            } else {
                sub = name.isEmpty() ? "Cycle" : name;
            }
            String who = w.optString("who", "Period");
            per = w.optString("note", "");
            JSONArray ps = w.optJSONArray("periods");
            if (ps != null) for (int i = 0; i < ps.length(); i++) {
                JSONObject o = ps.getJSONObject(i);
                LocalDate a = LocalDate.parse(o.getString("s")), b = LocalDate.parse(o.getString("e"));
                if (b.isBefore(t)) continue;
                per = !a.isAfter(t) ? who + ": likely now" : who + " " + days(ChronoUnit.DAYS.between(t, a));
                break;
            }
            JSONArray as = w.optJSONArray("anns");
            if (as != null) for (int i = 0; i < as.length(); i++) {
                JSONObject o = as.getJSONObject(i);
                LocalDate a = LocalDate.parse(o.getString("d"));
                if (a.isBefore(t)) continue;
                ann = o.getString("l") + " · " + days(ChronoUnit.DAYS.between(t, a));
                break;
            }
        } catch (Exception ignored) { }
        v.setTextViewText(R.id.w_top, top);
        v.setTextViewText(R.id.w_sub, sub);
        v.setTextViewText(R.id.w_period, per);
        v.setTextViewText(R.id.w_ann, ann);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, open);
        m.updateAppWidget(id, v);
    }
}
