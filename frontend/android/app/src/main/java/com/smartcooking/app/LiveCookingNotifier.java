package com.smartcooking.app;

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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 实时烹饪通知（App 退到后台后显示）。
 * Android 16+ 使用系统「实时更新」（状态栏温度胶囊，相当于灵动岛）；更早的版本使用与首页
 * CookX Sense 卡片一致的自定义卡片。温度由蓝牙读取线程直接刷新，网页暂停时也能更新。
 */
final class LiveCookingNotifier {
    private static final String CHANNEL_LIVE = "cookx_live_cooking";
    private static final String CHANNEL_ALERT = "cookx_live_alert";
    private static final int LIVE_ID = 4101;
    private static final int ALERT_ID = 4102;
    private static final long MIN_UPDATE_MS = 1000;

    static final class Payload {
        boolean connected;
        double temperature = Double.NaN;
        int maxTemperature = 250;
        int targetLow;
        int targetHigh;
        String status = "";
        String dish = "";
        String step = "";
        String adviceTitle = "下一步建议";
        String adviceText = "";
        String alertLevel = "";
        long timerEndsAt;
    }

    private static final Object LOCK = new Object();
    private static Payload payload;
    private static boolean active;
    private static long lastPostedAt;
    private static long nativeTemperatureAt;
    private static String lastAlertLevel = "";
    private static final StringBuilder lineBuffer = new StringBuilder();

    private LiveCookingNotifier() {}

    static void show(Context context, Payload next) {
        synchronized (LOCK) {
            // 原生刚读到的温度比网页传来的更新时，保留原生读数
            if (payload != null && SystemClock.elapsedRealtime() - nativeTemperatureAt < 3000 && !Double.isNaN(payload.temperature)) {
                next.temperature = payload.temperature;
                next.alertLevel = alertFor(next);
            }
            payload = next;
            active = true;
            post(context.getApplicationContext(), true);
        }
    }

    static void hide(Context context) {
        synchronized (LOCK) {
            active = false;
            lastAlertLevel = "";
            NotificationManager manager = manager(context);
            if (manager != null) manager.cancel(LIVE_ID);
        }
    }

    /** 蓝牙原始数据：按行解析 CX2 / TEMP: / 纯数字帧，只在通知显示时刷新。 */
    static void onBytes(Context context, byte[] bytes) {
        synchronized (LOCK) {
            if (!active || payload == null) return;
            String chunk = new String(bytes, StandardCharsets.US_ASCII);
            Double latest = null;
            for (int i = 0; i < chunk.length(); i++) {
                char c = chunk.charAt(i);
                if (c == '\n') {
                    Double value = parseLine(lineBuffer.toString());
                    if (value != null) latest = value;
                    lineBuffer.setLength(0);
                } else if (lineBuffer.length() < 256) {
                    lineBuffer.append(c);
                } else {
                    lineBuffer.setLength(0);
                }
            }
            if (latest == null) return;
            payload.temperature = latest;
            payload.connected = true;
            nativeTemperatureAt = SystemClock.elapsedRealtime();
            payload.alertLevel = alertFor(payload);
            post(context.getApplicationContext(), false);
        }
    }

    static void onConnection(Context context, boolean connected) {
        synchronized (LOCK) {
            if (!active || payload == null || payload.connected == connected) return;
            payload.connected = connected;
            if (!connected) payload.temperature = Double.NaN;
            payload.alertLevel = alertFor(payload);
            post(context.getApplicationContext(), true);
        }
    }

    static Double parseLine(String line) {
        String text = line.trim();
        try {
            if (text.startsWith("CX2,")) {
                String[] parts = text.split(",", -1);
                if (parts.length != 7 || !"1".equals(parts[6]) || parts[4].isEmpty()) return null;
                double value = Double.parseDouble(parts[4]);
                return value >= -70 && value <= 380 ? value : null;
            }
            String raw = text.startsWith("TEMP:") ? text.substring(5) : text;
            if (raw.isEmpty()) return null;
            double value = Double.parseDouble(raw);
            return value >= -50 && value <= 500 ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static String alertFor(Payload p) {
        if (Double.isNaN(p.temperature)) return "";
        double t = p.temperature;
        if (t >= 260 || (p.targetHigh > 0 && t > p.targetHigh + 40)) return "danger";
        if (t >= 235 || (p.targetHigh > 0 && t > p.targetHigh + 15)) return "warn";
        return "";
    }

    private static void post(Context context, boolean force) {
        if (!active || payload == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastPostedAt < MIN_UPDATE_MS) return;
        lastPostedAt = now;
        NotificationManager manager = manager(context);
        if (manager == null) return;
        ensureChannels(manager);
        Notification notification = Build.VERSION.SDK_INT >= 36 ? buildLiveUpdate(context) : buildCard(context);
        try {
            manager.notify(LIVE_ID, notification);
            if ("danger".equals(payload.alertLevel) && !"danger".equals(lastAlertLevel)) manager.notify(ALERT_ID, buildAlert(context));
        } catch (SecurityException denied) {
            // 用户未授予通知权限时静默跳过，界面内仍有预警
        }
        lastAlertLevel = payload.alertLevel;
    }

    private static String tempText(Payload p) {
        return Double.isNaN(p.temperature) ? "--" : String.format(Locale.ROOT, "%d°C", Math.round(p.temperature));
    }

    private static int progress(Payload p) {
        if (Double.isNaN(p.temperature)) return 0;
        return (int) Math.max(2, Math.min(100, Math.round(p.temperature / Math.max(1, p.maxTemperature) * 100)));
    }

    private static String advice(Payload p) {
        if ("danger".equals(p.alertLevel)) return "锅温过高，请立即调小火力或离火";
        if ("warn".equals(p.alertLevel)) return "温度偏高，建议调小火力";
        return p.adviceText == null ? "" : p.adviceText;
    }

    private static String dishLine(Payload p) {
        String dish = p.dish == null ? "" : p.dish;
        return (p.status == null || p.status.isEmpty() ? "" : p.status + " · ") + dish;
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, LIVE_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Android 15 及以下：与首页卡片一致的自定义通知。 */
    private static Notification buildCard(Context context) {
        Payload p = payload;
        boolean alert = "danger".equals(p.alertLevel);
        RemoteViews small = new RemoteViews(context.getPackageName(), R.layout.live_card_small);
        RemoteViews big = new RemoteViews(context.getPackageName(), R.layout.live_card_big);
        for (RemoteViews views : new RemoteViews[] { small, big }) {
            views.setTextViewText(R.id.live_temp, tempText(p));
            views.setTextColor(R.id.live_temp, alert ? 0xFFFF6152 : 0xFFFFFFFF);
            views.setProgressBar(R.id.live_progress, 100, progress(p), false);
            views.setTextViewText(R.id.live_dish, dishLine(p));
            views.setTextViewText(R.id.live_advice, advice(p));
            views.setInt(R.id.live_root, "setBackgroundResource", alert ? R.drawable.live_card_bg_alert : R.drawable.live_card_bg);
        }
        big.setTextViewText(R.id.live_status, p.connected ? "● 已连接" : "● 未连接");
        big.setTextColor(R.id.live_status, p.connected ? 0xFF7EE6A4 : 0x99F6F3EE);
        big.setTextViewText(R.id.live_advice_title, alert ? "锅温过高" : (p.adviceTitle == null ? "下一步建议" : p.adviceTitle));
        long remaining = p.timerEndsAt - System.currentTimeMillis();
        if (p.timerEndsAt > 0 && remaining > 0) {
            big.setViewVisibility(R.id.live_timer, View.VISIBLE);
            big.setChronometer(R.id.live_timer, SystemClock.elapsedRealtime() + remaining, null, true);
            big.setChronometerCountDown(R.id.live_timer, true);
        } else {
            big.setViewVisibility(R.id.live_timer, View.GONE);
        }
        Notification.Builder builder = builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_cookx)
            .setContentTitle("CookX " + tempText(p))
            .setContentText(dishLine(p))
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setStyle(new Notification.DecoratedCustomViewStyle())
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC);
        return builder.build();
    }

    /** Android 16+：系统实时更新，状态栏显示温度胶囊。 */
    @TargetApi(36)
    private static Notification buildLiveUpdate(Context context) {
        Payload p = payload;
        boolean alert = "danger".equals(p.alertLevel);
        int color = alert ? 0xFFFF3B30 : 0xFFFF8A2E;
        Notification.ProgressStyle style = new Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress(p))
            .setProgressSegments(java.util.Collections.singletonList(new Notification.ProgressStyle.Segment(100).setColor(color)));
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_cookx)
            .setContentTitle((alert ? "锅温过高 " : "当前锅温 ") + tempText(p) + " · " + (p.dish == null ? "" : p.dish))
            .setContentText(advice(p).isEmpty() ? p.step : advice(p))
            .setStyle(style)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShortCriticalText(Double.isNaN(p.temperature) ? "CookX" : String.format(Locale.ROOT, "%d°", Math.round(p.temperature)));
        // 请求提升为「实时更新」（Notification.EXTRA_REQUEST_PROMOTED_ONGOING）
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

    private static Notification buildAlert(Context context) {
        return builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_cookx)
            .setContentTitle("锅温过高 " + tempText(payload))
            .setContentText("已超出安全范围，请立即调小火力或离火")
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

    private static void ensureChannels(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (manager.getNotificationChannel(CHANNEL_LIVE) == null) {
            NotificationChannel live = new NotificationChannel(CHANNEL_LIVE, "实时烹饪", NotificationManager.IMPORTANCE_LOW);
            live.setDescription("退出 App 后在通知栏显示锅温与下一步建议");
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
