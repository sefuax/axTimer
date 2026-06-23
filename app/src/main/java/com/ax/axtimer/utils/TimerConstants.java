package com.ax.axtimer.utils;

/**
 * Centralized constants for AX Timer.
 * Keeps magic numbers and action strings in one place.
 */
public final class TimerConstants {

    private TimerConstants() { /* no instances */ }

    // ── Intent actions (HomeActivity → TimerService) ──────────────────────────
    public static final String ACTION_START  = "com.ax.axtimer.ACTION_START";
    public static final String ACTION_STOP   = "com.ax.axtimer.ACTION_STOP";

    // ── Broadcast actions (TimerService → FloatingOverlay) ────────────────────
    public static final String BROADCAST_TICK      = "com.ax.axtimer.TICK";
    public static final String BROADCAST_CYCLE_END = "com.ax.axtimer.CYCLE_END";

    // ── Intent / Bundle extras ─────────────────────────────────────────────────
    /** Total selected session duration in minutes */
    public static final String EXTRA_DURATION_MINUTES = "duration_minutes";

    /** Seconds remaining in current 5-minute cycle */
    public static final String EXTRA_CYCLE_SECONDS    = "cycle_seconds";

    /** Total session seconds remaining */
    public static final String EXTRA_SESSION_SECONDS  = "session_seconds";

    /** Accumulated points */
    public static final String EXTRA_POINTS           = "points";

    // ── Timer config ───────────────────────────────────────────────────────────
    /** Each work cycle is exactly 5 minutes = 300 seconds */
    public static final int CYCLE_DURATION_SECONDS = 300;

    /** Foreground Service notification ID */
    public static final int NOTIFICATION_ID = 1001;

    /** Notification channel ID */
    public static final String CHANNEL_ID = "ax_timer_channel";
}
