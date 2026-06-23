package com.ax.axtimer.overlay;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ax.axtimer.R;
import com.ax.axtimer.timer.TimerEngine;

/**
 * FloatingOverlay
 * ───────────────
 * Manages the system-level overlay window drawn via WindowManager.
 * The widget:
 *   • Floats above all other apps (TYPE_APPLICATION_OVERLAY)
 *   • Is draggable anywhere on screen
 *   • Snaps to nearest edge when released
 *   • Updates every second with timer state
 *   • Handles [1], [2], and [✅] button logic
 *   • Switches to a result screen when the session completes
 */
public class FloatingOverlay {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Context       context;
    private final WindowManager windowManager;
    private       View          overlayView;

    // ── WindowManager layout params ───────────────────────────────────────────
    private WindowManager.LayoutParams layoutParams;

    // ── UI references ─────────────────────────────────────────────────────────
    private TextView    tvCountdown;
    private TextView    tvSessionRemaining;
    private TextView    tvPointsBadge;
    private ProgressBar progressCycle;
    private TextView    btnAction1;
    private TextView    btnAction2;
    private TextView    btnSuccess;
    private View        layoutTimerView;
    private View        layoutResultView;
    private TextView    tvTotalPoints;

    // ── Game state ────────────────────────────────────────────────────────────
    private int  totalPoints     = 0;
    private int  lastButtonPress = 0;   // 0 = none, 1 = btn1, 2 = btn2
    private boolean btn1Active   = false;
    private boolean btn2Active   = false;

    // ── Drag state ────────────────────────────────────────────────────────────
    private int   initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    // ── Callback to service ───────────────────────────────────────────────────
    private OverlayCallback callback;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FloatingOverlay(Context context, OverlayCallback callback) {
        this.context       = context;
        this.callback      = callback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Inflates the widget and adds it to the WindowManager. */
    public void show() {
        overlayView = LayoutInflater.from(context)
                .inflate(R.layout.layout_floating_widget, null);

        // Bind views
        tvCountdown         = overlayView.findViewById(R.id.tvCountdown);
        tvSessionRemaining  = overlayView.findViewById(R.id.tvSessionRemaining);
        tvPointsBadge       = overlayView.findViewById(R.id.tvPointsBadge);
        progressCycle       = overlayView.findViewById(R.id.progressCycle);
        btnAction1          = overlayView.findViewById(R.id.btnAction1);
        btnAction2          = overlayView.findViewById(R.id.btnAction2);
        btnSuccess          = overlayView.findViewById(R.id.btnSuccess);
        layoutTimerView     = overlayView.findViewById(R.id.layoutTimerView);
        layoutResultView    = overlayView.findViewById(R.id.layoutResultView);
        tvTotalPoints       = overlayView.findViewById(R.id.tvTotalPoints);

        // WindowManager params
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 40;
        layoutParams.y = 200;

        // Wire up buttons and drag
        setupButtons();
        setupDrag();

        // Fade in on add
        overlayView.setAlpha(0f);
        windowManager.addView(overlayView, layoutParams);
        overlayView.animate().alpha(1f).setDuration(400).start();
    }

    /** Removes the overlay from the WindowManager. */
    public void hide() {
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            overlayView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    try {
                        windowManager.removeView(overlayView);
                    } catch (Exception ignored) {}
                })
                .start();
        }
    }

    // ── Timer update ──────────────────────────────────────────────────────────

    /**
     * Called every second by TimerService with fresh state.
     *
     * @param cycleSecondsLeft   Seconds remaining in the current 5-min cycle.
     * @param sessionSecondsLeft Total session seconds remaining.
     */
    public void updateTimer(int cycleSecondsLeft, int sessionSecondsLeft) {
        if (overlayView == null) return;

        // Format MM:SS
        tvCountdown.setText(formatTime(cycleSecondsLeft));

        // Update session remaining label
        String sessionLabel = formatSessionLabel(sessionSecondsLeft);
        tvSessionRemaining.setText(sessionLabel);

        // Progress bar — max is CYCLE_SECONDS (300), fills from right to left
        int progressValue = cycleSecondsLeft;
        progressCycle.setProgress(progressValue);

        // Warn colour when < 30 seconds left in cycle
        if (cycleSecondsLeft <= 30) {
            tvCountdown.setTextColor(context.getResources().getColor(R.color.ax_magenta, null));
        } else {
            tvCountdown.setTextColor(context.getResources().getColor(R.color.ax_cyan, null));
        }

        tvPointsBadge.setText(String.valueOf(totalPoints));
    }

    /** Transition overlay to the final result screen. */
    public void showResult() {
        if (overlayView == null) return;

        tvTotalPoints.setText(String.valueOf(totalPoints));

        // Cross-fade timer → result
        layoutTimerView.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            layoutTimerView.setVisibility(View.GONE);
            layoutResultView.setVisibility(View.VISIBLE);
            layoutResultView.setAlpha(0f);
            layoutResultView.animate().alpha(1f).setDuration(350).start();

            // Animate the points count up
            animatePointsCount(0, totalPoints);
        }).start();

        // Wire close button
        TextView btnClose = overlayView.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> callback.onCloseRequested());
    }

    // ── Button setup ──────────────────────────────────────────────────────────

    private void setupButtons() {
        btnAction1.setOnClickListener(v -> handleButton(1));
        btnAction2.setOnClickListener(v -> handleButton(2));
        btnSuccess.setOnClickListener(v -> handleSuccessButton());
    }

    /**
     * Core combo logic:
     *   If user presses 1 then 2 (or 2 then 1) within a single cycle →
     *   award +2 points, notify the engine to reset the cycle and subtract
     *   5 minutes from the session.
     */
    private void handleButton(int buttonId) {
        boolean wasActive = (buttonId == 1) ? btn1Active : btn2Active;

        // Toggle visual active state
        if (buttonId == 1) {
            btn1Active = !btn1Active;
            btn2Active = false;  // deselect the other
        } else {
            btn2Active = !btn2Active;
            btn1Active = false;
        }

        updateButtonVisuals();
        animateButtonTap(buttonId == 1 ? btnAction1 : btnAction2);

        // Check combo: opposite button was previously pressed
        if (!wasActive && lastButtonPress != 0 && lastButtonPress != buttonId) {
            // Combo achieved!
            totalPoints += 2;
            tvPointsBadge.setText(String.valueOf(totalPoints));
            flashPointsBadge();

            // Reset button states
            btn1Active = false;
            btn2Active = false;
            lastButtonPress = 0;
            updateButtonVisuals();

            // Notify service / engine
            callback.onComboAchieved();
        } else {
            lastButtonPress = buttonId;
        }
    }

    /** Tapping ✅ ends the session and shows the result. */
    private void handleSuccessButton() {
        animateButtonTap(btnSuccess);
        showResult();
        callback.onSuccessPressed();
    }

    private void updateButtonVisuals() {
        btnAction1.setActivated(btn1Active);
        btnAction2.setActivated(btn2Active);

        int activeColor   = context.getResources().getColor(R.color.ax_deep_black, null);
        int inactiveColor = context.getResources().getColor(R.color.ax_text_primary, null);

        btnAction1.setTextColor(btn1Active ? activeColor : inactiveColor);
        btnAction2.setTextColor(btn2Active ? activeColor : inactiveColor);
    }

    // ── Drag & drop ───────────────────────────────────────────────────────────

    private void setupDrag() {
        overlayView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX      = layoutParams.x;
                    initialY      = layoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging    = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;

                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + (int) dx;
                        layoutParams.y = initialY + (int) dy;
                        windowManager.updateViewLayout(overlayView, layoutParams);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (isDragging) {
                        snapToEdge();
                        return true;
                    }
                    // Not a drag — treat as click (dispatch to children)
                    v.performClick();
                    return false;
            }
            return false;
        });
    }

    /** Snaps the widget horizontally to the nearest screen edge. */
    private void snapToEdge() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidth   = dm.widthPixels;
        int widgetWidth   = overlayView.getWidth();
        int midScreen     = screenWidth / 2;

        int targetX = (layoutParams.x + widgetWidth / 2 < midScreen) ? 20
                : (screenWidth - widgetWidth - 20);

        ValueAnimator snapAnim = ValueAnimator.ofInt(layoutParams.x, targetX);
        snapAnim.setDuration(220);
        snapAnim.addUpdateListener(anim -> {
            layoutParams.x = (int) anim.getAnimatedValue();
            if (overlayView.isAttachedToWindow()) {
                windowManager.updateViewLayout(overlayView, layoutParams);
            }
        });
        snapAnim.start();
    }

    // ── Micro-animations ──────────────────────────────────────────────────────

    private void animateButtonTap(View btn) {
        btn.animate()
           .scaleX(0.85f).scaleY(0.85f)
           .setDuration(80)
           .withEndAction(() ->
               btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
           .start();
    }

    private void flashPointsBadge() {
        View badge = tvPointsBadge.getRootView().findViewById(R.id.tvPointsBadge);
        if (badge == null) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(badge, "scaleX", 1f, 1.5f, 1f);
        pulse.setDuration(400);
        pulse.start();
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(badge, "scaleY", 1f, 1.5f, 1f);
        pulseY.setDuration(400);
        pulseY.start();
    }

    private void animatePointsCount(int from, int to) {
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(800);
        anim.addUpdateListener(a ->
            tvTotalPoints.setText(String.valueOf(a.getAnimatedValue())));
        anim.start();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Format seconds as MM:SS */
    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /** Format session remaining as human-readable */
    private static String formatSessionLabel(int totalSeconds) {
        if (totalSeconds <= 0) return "session complete";
        int minutes = totalSeconds / 60;
        if (minutes >= 60) {
            int h = minutes / 60;
            int m = minutes % 60;
            return m > 0 ? h + "h " + m + "m remaining" : h + "h remaining";
        }
        return minutes + " min remaining";
    }

    // ── Callback interface ────────────────────────────────────────────────────

    public interface OverlayCallback {
        /** User achieved 1→2 or 2→1 combo. Engine should reset cycle. */
        void onComboAchieved();

        /** User pressed ✅ to end manually. */
        void onSuccessPressed();

        /** User pressed ❌ CLOSE on result screen. */
        void onCloseRequested();
    }
}
