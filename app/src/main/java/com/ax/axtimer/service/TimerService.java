package com.ax.axtimer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import com.ax.axtimer.R;
import com.ax.axtimer.overlay.FloatingOverlay;
import com.ax.axtimer.timer.TimerEngine;
import com.ax.axtimer.ui.HomeActivity;
import com.ax.axtimer.utils.TimerConstants;

/**
 * TimerService
 * ────────────
 * Android Foreground Service that:
 *   1. Keeps the process alive while the timer runs.
 *   2. Creates and manages the FloatingOverlay (overlay window).
 *   3. Drives the TimerEngine and forwards its callbacks to the overlay.
 *   4. Posts a persistent notification so the user can always see the timer.
 *
 * Lifecycle:
 *   START  → startForeground() → show overlay → start engine
 *   TICK   → engine callback → overlay.updateTimer()
 *   COMBO  → engine.onSuccessCombo() → +2 pts already credited in overlay
 *   DONE   → overlay.showResult() → wait for user to close
 *   CLOSE  → stopSelf() → overlay removed
 */
public class TimerService extends Service
        implements TimerEngine.Callback, FloatingOverlay.OverlayCallback {

    private TimerEngine     timerEngine;
    private FloatingOverlay floatingOverlay;

    // Accumulated points (mirrored here for notification updates)
    private int totalPoints = 0;

    // Track whether the overlay is showing
    private boolean overlayShowing = false;

    // ── Service entry points ──────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;

        switch (action) {
            case TimerConstants.ACTION_START:
                int durationMinutes = intent.getIntExtra(
                        TimerConstants.EXTRA_DURATION_MINUTES, 5);
                startTimerSession(durationMinutes);
                break;

            case TimerConstants.ACTION_STOP:
                shutdownService();
                break;
        }

        // START_NOT_STICKY: don't recreate if killed by system
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    // ── Session management ────────────────────────────────────────────────────

    private void startTimerSession(int durationMinutes) {
        // 1. Build and post the foreground notification first
        createNotificationChannel();
        startForeground(TimerConstants.NOTIFICATION_ID, buildNotification("Session running…"));

        // 2. Show the floating overlay
        if (!overlayShowing && Settings.canDrawOverlays(this)) {
            floatingOverlay = new FloatingOverlay(this, this);
            floatingOverlay.show();
            overlayShowing = true;
        }

        // 3. Start the timer engine
        if (timerEngine != null) {
            timerEngine.destroy();
        }
        timerEngine = new TimerEngine(durationMinutes, this);
        timerEngine.start();
    }

    private void shutdownService() {
        if (timerEngine != null) {
            timerEngine.destroy();
            timerEngine = null;
        }
        if (floatingOverlay != null) {
            floatingOverlay.hide();
            floatingOverlay = null;
            overlayShowing  = false;
        }
        stopForeground(true);
        stopSelf();
    }

    // ── TimerEngine.Callback ──────────────────────────────────────────────────

    @Override
    public void onTick(int cycleSecondsRemaining, int sessionSecondsRemaining) {
        // Update the floating overlay
        if (floatingOverlay != null) {
            floatingOverlay.updateTimer(cycleSecondsRemaining, sessionSecondsRemaining);
        }

        // Update notification every 10 seconds to save battery
        if (cycleSecondsRemaining % 10 == 0) {
            String label = formatTime(cycleSecondsRemaining)
                    + " · " + formatSessionLabel(sessionSecondsRemaining);
            updateNotification(label);
        }
    }

    @Override
    public void onCycleComplete(int sessionSecondsRemaining) {
        // Natural cycle end (user did not press combo).
        // The engine has already reset the cycle internally.
        // Just update the UI so the new "05:00" appears.
        if (floatingOverlay != null) {
            floatingOverlay.updateTimer(TimerEngine.CYCLE_SECONDS, sessionSecondsRemaining);
        }
    }

    @Override
    public void onSessionComplete() {
        // All time consumed — show result
        if (floatingOverlay != null) {
            floatingOverlay.showResult();
        }
        updateNotification("Session complete! " + totalPoints + " points earned");
    }

    // ── FloatingOverlay.OverlayCallback ──────────────────────────────────────

    @Override
    public void onComboAchieved() {
        // The overlay has already credited +2 pts to its own counter.
        // Tell the engine to reset the cycle and reduce session by 5 min.
        totalPoints += 2;
        if (timerEngine != null) {
            timerEngine.onSuccessCombo();
        }
    }

    @Override
    public void onSuccessPressed() {
        // User tapped ✅ — stop the timer engine, keep overlay open
        // (result screen is already shown by the overlay itself)
        if (timerEngine != null) {
            timerEngine.stop();
        }
    }

    @Override
    public void onCloseRequested() {
        // User tapped ❌ CLOSE on the result screen
        shutdownService();
    }

    // ── Service destroy ───────────────────────────────────────────────────────

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timerEngine != null) timerEngine.destroy();
        if (floatingOverlay != null) floatingOverlay.hide();
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    TimerConstants.CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW // LOW = no sound, still persistent
            );
            channel.setDescription(getString(R.string.timer_notification_desc));
            channel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String contentText) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, HomeActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, TimerConstants.CHANNEL_ID)
                .setContentTitle(getString(R.string.timer_running))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(getResources().getColor(R.color.notification_color, null))
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(TimerConstants.NOTIFICATION_ID, buildNotification(text));
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String formatTime(int totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String formatSessionLabel(int totalSeconds) {
        int minutes = totalSeconds / 60;
        if (minutes >= 60) {
            int h = minutes / 60;
            int m = minutes % 60;
            return m > 0 ? h + "h " + m + "m left" : h + "h left";
        }
        return minutes + "m left";
    }
}
