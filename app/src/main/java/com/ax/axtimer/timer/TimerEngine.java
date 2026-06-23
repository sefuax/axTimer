package com.ax.axtimer.timer;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * TimerEngine
 * ───────────
 * Drift-corrected countdown engine.
 *
 * Uses SystemClock.elapsedRealtime() as the source of truth so the timer
 * stays accurate even when the device is under load, the screen changes,
 * or the app is backgrounded.
 *
 * The engine tracks two levels:
 *   • cycleSecondsRemaining  – current 5-minute (300s) work cycle
 *   • sessionSecondsRemaining – total selected session (e.g. 3600s for 1 hour)
 *
 * When a cycle ends the engine resets it to CYCLE_DURATION_SECONDS and
 * notifies the listener; it does NOT subtract from the session automatically
 * (the caller controls that via onCycleComplete so the overlay can show
 * the result screen for the ✅ button).
 */
public class TimerEngine {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final long  TICK_MS           = 1000L;  // 1-second ticks
    public  static final int   CYCLE_SECONDS     = 300;    // 5 minutes

    // ── State ─────────────────────────────────────────────────────────────────
    private int  cycleSecondsRemaining;
    private int  sessionSecondsRemaining;
    private boolean running = false;

    /** Absolute elapsed-realtime when the current tick interval started */
    private long lastTickTime;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Handler   handler;
    private final Callback  callback;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param sessionMinutes Total session length chosen by the user (minutes).
     * @param callback       Event listener implemented by TimerService.
     */
    public TimerEngine(int sessionMinutes, Callback callback) {
        this.callback                 = callback;
        this.sessionSecondsRemaining  = sessionMinutes * 60;
        this.cycleSecondsRemaining    = CYCLE_SECONDS;
        this.handler                  = new Handler(Looper.getMainLooper());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Start (or resume) the countdown. */
    public void start() {
        if (running) return;
        running      = true;
        lastTickTime = SystemClock.elapsedRealtime();
        handler.postDelayed(tickRunnable, TICK_MS);
    }

    /** Stop the countdown (can be restarted). */
    public void stop() {
        running = false;
        handler.removeCallbacks(tickRunnable);
    }

    /** Hard stop — releases resources. */
    public void destroy() {
        stop();
    }

    /**
     * Called when the user presses [1]→[2] or [2]→[1] successfully.
     * Adds 2 points (handled externally) and resets the current cycle
     * while subtracting 5 minutes from the remaining session.
     */
    public void onSuccessCombo() {
        cycleSecondsRemaining = CYCLE_SECONDS;
        sessionSecondsRemaining = Math.max(0, sessionSecondsRemaining - (5 * 60));
        // Emit a tick immediately so the UI updates without delay
        callback.onTick(cycleSecondsRemaining, sessionSecondsRemaining);
    }

    // Getters used by the service to restore state on rebind
    public int getCycleSecondsRemaining()   { return cycleSecondsRemaining; }
    public int getSessionSecondsRemaining() { return sessionSecondsRemaining; }
    public boolean isRunning()              { return running; }

    // ── Internal tick loop ────────────────────────────────────────────────────

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            // Drift correction: calculate how many seconds have actually elapsed
            long now      = SystemClock.elapsedRealtime();
            long elapsed  = now - lastTickTime;
            int  ticks    = (int) (elapsed / TICK_MS);

            if (ticks < 1) {
                // Not yet a full second — reschedule for the remainder
                handler.postDelayed(this, TICK_MS - elapsed);
                return;
            }

            // Consume the ticks
            lastTickTime += ticks * TICK_MS;

            for (int i = 0; i < ticks; i++) {
                if (!running) break;

                cycleSecondsRemaining--;
                sessionSecondsRemaining = Math.max(0, sessionSecondsRemaining - 1);

                if (cycleSecondsRemaining <= 0) {
                    // Cycle ended: notify listener (it may reset or end session)
                    callback.onCycleComplete(sessionSecondsRemaining);
                    cycleSecondsRemaining = CYCLE_SECONDS; // ready for next cycle
                } else {
                    callback.onTick(cycleSecondsRemaining, sessionSecondsRemaining);
                }

                // If session is fully consumed
                if (sessionSecondsRemaining <= 0) {
                    running = false;
                    callback.onSessionComplete();
                    return;
                }
            }

            // Schedule next tick aligned with the wall clock
            long nextDelay = TICK_MS - (SystemClock.elapsedRealtime() - lastTickTime);
            handler.postDelayed(this, Math.max(0, nextDelay));
        }
    };

    // ── Callback interface ────────────────────────────────────────────────────

    public interface Callback {
        /** Fires every second during a cycle. */
        void onTick(int cycleSecondsRemaining, int sessionSecondsRemaining);

        /**
         * Fires when a 5-minute cycle reaches zero naturally (without the
         * user pressing [1]→[2]). The engine has already reset the cycle
         * counter internally.
         */
        void onCycleComplete(int sessionSecondsRemaining);

        /** Fires when the total session time reaches zero. */
        void onSessionComplete();
    }
}
