package com.smartcooking.app.feature.live;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import com.smartcooking.app.MainActivity;
import com.smartcooking.app.R;
import java.util.Collections;
import java.util.Locale;

/**
 * The ongoing "live cooking" notification. On Android 16+ it is a Live Update (progress style with
 * the pan temperature as the status-bar chip, Android's counterpart of the Dynamic Island); older
 * versions show a custom card that matches the home CookX Sense card. Framework APIs only.
 */
public final class LiveNotifier {
    static final String CHANNEL_LIVE = "cookx_live_cooking";
    static final String CHANNEL_ALERT = "cookx_live_alert";
    public static final int LIVE_ID = 4101;
    static final int ALERT_ID = 4102;

    /** What the notification shows; filled from the Kotlin live state. */
    public static final class Payload {
        public boolean connected;
        public boolean simulated;
        public double temperature = Double.NaN;
        public String dish = "";
        public String step = "";
        public String adviceTitle = "下一步建议";
        public String adviceText = "";
        /** "", "warn" or "danger". */
        public String alertLevel = "";
        public String alertTitle = "";
        public String alertText = "";
        public long timerEndsAt;
        public boolean cooking;
    }

    private static Payload current;
    private static String lastAlertLevel = "";

    private LiveNotifier() {}

    /** Latest payload, used by the foreground service when it (re)starts. */
    public static synchronized Payload current() { return current; }

    public static synchronized Notification build(Context context, Payload payload) {
        current = payload;
        ensureChannels(context);
        return Build.VERSION.SDK_INT >= 36 ? buildLiveUpdate(context, payload) : buildCard(context, payload);
    }

    /** Posts or refreshes the notification; also raises a heads-up alert when overheating starts. */
    public static synchronized void post(Context context, Payload payload) {
        NotificationManager manager = manager(context);
        if (manager == null) return;
        Notification notification = build(context, payload);
        try {
            manager.notify(LIVE_ID, notification);
            if ("danger".equals(payload.alertLevel) && !"danger".equals(lastAlertLevel)) manager.notify(ALERT_ID, buildAlert(context, payload));
        } catch (SecurityException denied) {
            // Notifications not permitted: the in-app alert still shows.
        }
        lastAlertLevel = payload.alertLevel;
    }

    public static synchronized void cancel(Context context) {
        current = null;
        lastAlertLevel = "";
        NotificationManager manager = manager(context);
        if (manager != null) manager.cancel(LIVE_ID);
    }

    static String tempText(Payload p) {
        return Double.isNaN(p.temperature) ? "--" : String.format(Locale.ROOT, "%d°C", Math.round(p.temperature));
    }

    static int progress(Payload p) {
        if (Double.isNaN(p.temperature)) return 0;
        return (int) Math.max(2, Math.min(100, Math.round(p.temperature / 250.0 * 100)));
    }

    private static String advice(Payload p) {
        if (!p.alertLevel.isEmpty()) return p.alertText;
        return p.adviceText == null ? "" : p.adviceText;
    }

    private static String statusLine(Payload p) {
        String prefix = p.cooking ? "正在烹饪 · " : (p.simulated ? "仿真回放 · " : "");
        return prefix + (p.dish == null || p.dish.isEmpty() ? "CookX Sense" : p.dish);
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("cookx_route", "kitchen");
        return PendingIntent.getActivity(context, LIVE_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Android 15 and below: a custom card that looks like the home CookX Sense card. */
    private static Notification buildCard(Context context, Payload p) {
        boolean danger = "danger".equals(p.alertLevel);
        RemoteViews small = new RemoteViews(context.getPackageName(), R.layout.live_card_small);
        RemoteViews big = new RemoteViews(context.getPackageName(), R.layout.live_card_big);
        for (RemoteViews views : new RemoteViews[] { small, big }) {
            views.setTextViewText(R.id.live_temp, tempText(p));
            views.setTextColor(R.id.live_temp, danger ? 0xFFFF6152 : 0xFFFFFFFF);
            views.setProgressBar(R.id.live_progress, 100, progress(p), false);
            views.setTextViewText(R.id.live_dish, statusLine(p));
            views.setTextViewText(R.id.live_advice, advice(p));
            views.setInt(R.id.live_root, "setBackgroundResource", danger ? R.drawable.live_card_bg_alert : R.drawable.live_card_bg);
        }
        big.setTextViewText(R.id.live_status, p.connected ? "● 已连接" : (p.simulated ? "● 仿真回放" : "● 未连接"));
        big.setTextColor(R.id.live_status, p.connected || p.simulated ? 0xFF7EE6A4 : 0x99F6F3EE);
        big.setTextViewText(R.id.live_advice_title, !p.alertLevel.isEmpty() ? p.alertTitle : p.adviceTitle);
        long remaining = p.timerEndsAt - System.currentTimeMillis();
        if (p.timerEndsAt > 0 && remaining > 0) {
            big.setViewVisibility(R.id.live_timer, View.VISIBLE);
            big.setChronometer(R.id.live_timer, SystemClock.elapsedRealtime() + remaining, null, true);
            big.setChronometerCountDown(R.id.live_timer, true);
        } else {
            big.setViewVisibility(R.id.live_timer, View.GONE);
        }
        return builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_cookx)
            .setContentTitle("CookX " + tempText(p))
            .setContentText(statusLine(p))
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setStyle(new Notification.DecoratedCustomViewStyle())
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build();
    }

    /** Android 16+: a promoted Live Update; the status-bar chip shows the pan temperature. */
    @TargetApi(36)
    private static Notification buildLiveUpdate(Context context, Payload p) {
        boolean danger = "danger".equals(p.alertLevel);
        int color = danger ? 0xFFFF3B30 : 0xFFFF8A2E;
        Notification.ProgressStyle style = new Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress(p))
            .setProgressSegments(Collections.singletonList(new Notification.ProgressStyle.Segment(100).setColor(color)));
        String title = (danger ? "锅温过高 " : "当前锅温 ") + tempText(p) + (p.dish == null || p.dish.isEmpty() ? "" : " · " + p.dish);
        String text = advice(p).isEmpty() ? p.step : advice(p);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_cookx)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setColor(color)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShortCriticalText(Double.isNaN(p.temperature) ? "CookX" : String.format(Locale.ROOT, "%d°", Math.round(p.temperature)));
        // Notification.EXTRA_REQUEST_PROMOTED_ONGOING: ask the system to show it as a Live Update.
        Bundle extras = new Bundle();
        extras.putBoolean("android.requestPromotedOngoing", true);
        builder.addExtras(extras);
        long remaining = p.timerEndsAt - System.currentTimeMillis();
        if (p.timerEndsAt > 0 && remaining > 0) {
            builder.setWhen(p.timerEndsAt).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true);
        } else {
            builder.setShowWhen(false);
        }
        return builder.build();
    }

    private static Notification buildAlert(Context context, Payload p) {
        return builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_cookx)
            .setContentTitle(p.alertTitle + " " + tempText(p))
            .setContentText(p.alertText)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build();
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context context, String channel) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(context, channel) : new Notification.Builder(context);
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = manager(context);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_LIVE) == null) {
            NotificationChannel live = new NotificationChannel(CHANNEL_LIVE, "实时烹饪", NotificationManager.IMPORTANCE_LOW);
            live.setDescription("连接 CookX Sense 或烹饪时，在通知栏显示锅温与下一步建议");
            live.setShowBadge(false);
            manager.createNotificationChannel(live);
        }
        if (manager.getNotificationChannel(CHANNEL_ALERT) == null) {
            NotificationChannel alert = new NotificationChannel(CHANNEL_ALERT, "锅温预警", NotificationManager.IMPORTANCE_HIGH);
            alert.setDescription("锅温超出安全范围时提醒");
            alert.enableVibration(true);
            manager.createNotificationChannel(alert);
        }
    }
}
