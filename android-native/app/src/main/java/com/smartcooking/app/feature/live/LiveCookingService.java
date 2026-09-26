package com.smartcooking.app.feature.live;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the process (and so the Bluetooth read loop) alive while CookX Sense is connected, with the
 * live cooking notification as its foreground notification. Started only from the foreground, as
 * Android 12+ requires; stopped when the probe disconnects.
 */
public final class LiveCookingService extends Service {
    private static volatile boolean running;

    public static boolean isRunning() { return running; }

    /** Returns false when the system refuses to start a foreground service right now. */
    public static boolean start(Context context) {
        if (running) return true;
        try {
            Intent intent = new Intent(context, LiveCookingService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException refused) {
            // ForegroundServiceStartNotAllowedException (API 31+) or a missing permission.
            return false;
        }
    }

    public static void stop(Context context) {
        if (!running) return;
        context.stopService(new Intent(context, LiveCookingService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LiveNotifier.Payload payload = LiveNotifier.current();
        if (payload == null) {
            payload = new LiveNotifier.Payload();
            payload.connected = true;
        }
        Notification notification = LiveNotifier.build(this, payload);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(LiveNotifier.LIVE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(LiveNotifier.LIVE_ID, notification);
            }
            running = true;
        } catch (RuntimeException refused) {
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
