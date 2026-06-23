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

public class FloatingOverlay {

    private final Context       context;
    private final WindowManager windowManager;
    private       View          overlayView;
    private WindowManager.LayoutParams layoutParams;

    private TextView    tvCountdown;
    private TextView    tvSessionRemaining;
    private TextView    tvPointsBadge;
    private ProgressBar progressCycle;
    private TextView    btnAction1;
    private TextView    btnAction2;
    private TextView    btnSuccess;
    private TextView    btnToggle;
    private View        layoutTimerWidget;
    private View        layoutTimerView;
    private View        layoutResultView;
    private TextView    tvTotalPoints;

    private boolean isWidgetExpanded = false;

    private int  totalPoints     = 0;
    private int  lastButtonPress = 0;
    private boolean btn1Active   = false;
    private boolean btn2Active   = false;

    private int   initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    private OverlayCallback callback;

    public FloatingOverlay(Context context, OverlayCallback callback) {
        this.context       = context;
        this.callback      = callback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        overlayView = LayoutInflater.from(context)
                .inflate(R.layout.layout_floating_widget, null);

        tvCountdown         = overlayView.findViewById(R.id.tvCountdown);
        tvSessionRemaining  = overlayView.findViewById(R.id.tvSessionRemaining);
        tvPointsBadge       = overlayView.findViewById(R.id.tvPointsBadge);
        progressCycle       = overlayView.findViewById(R.id.progressCycle);
        btnAction1          = overlayView.findViewById(R.id.btnAction1);
        btnAction2          = overlayView.findViewById(R.id.btnAction2);
        btnSuccess          = overlayView.findViewById(R.id.btnSuccess);
        btnToggle           = overlayView.findViewById(R.id.btnToggle);
        layoutTimerWidget   = overlayView.findViewById(R.id.layoutTimerWidget);
        layoutTimerView     = overlayView.findViewById(R.id.layoutTimerView);
        layoutResultView    = overlayView.findViewById(R.id.layoutResultView);
        tvTotalPoints       = overlayView.findViewById(R.id.tvTotalPoints);

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

        // Toggle button click
        btnToggle.setOnClickListener(v -> toggleWidget());

        setupButtons();
        setupDrag();

        overlayView.setAlpha(0f);
        windowManager.addView(overlayView, layoutParams);
        overlayView.animate().alpha(1f).setDuration(400).start();
    }

    private void toggleWidget() {
        isWidgetExpanded = !isWidgetExpanded;
        if (isWidgetExpanded) {
            btnToggle.setText("−");
            layoutTimerWidget.setVisibility(View.VISIBLE);
            layoutTimerWidget.setAlpha(0f);
            layoutTimerWidget.animate().alpha(1f).setDuration(300).start();
        } else {
            btnToggle.setText("+");
            layoutTimerWidget.animate().alpha(0f).setDuration(200).withEndAction(() ->
                layoutTimerWidget.setVisibility(View.GONE)).start();
        }
    }

    public void hide() {
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            overlayView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    try { windowManager.removeView(overlayView); }
                    catch (Exception ignored) {}
                }).start();
        }
    }

    public void updateTimer(int cycleSecondsLeft, int sessionSecondsLeft) {
        if (overlayView == null) return;
        tvCountdown.setText(formatTime(cycleSecondsLeft));
        tvSessionRemaining.setText(formatSessionLabel(sessionSecondsLeft));
        progressCycle.setProgress(cycleSecondsLeft);
        if (cycleSecondsLeft <= 30) {
            tvCountdown.setTextColor(context.getResources().getColor(R.color.ax_magenta, null));
        } else {
            tvCountdown.setTextColor(context.getResources().getColor(R.color.ax_cyan, null));
        }
        tvPointsBadge.setText("pts: " + totalPoints);
    }

    public void showResult() {
        if (overlayView == null) return;
        tvTotalPoints.setText(String.valueOf(totalPoints));
        layoutTimerWidget.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            layoutTimerWidget.setVisibility(View.GONE);
            layoutResultView.setVisibility(View.VISIBLE);
            layoutResultView.setAlpha(0f);
            layoutResultView.animate().alpha(1f).setDuration(350).start();
            animatePointsCount(0, totalPoints);
        }).start();
        TextView btnClose = overlayView.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> callback.onCloseRequested());
    }

    private void setupButtons() {
        btnAction1.setOnClickListener(v -> handleButton(1));
        btnAction2.setOnClickListener(v -> handleButton(2));
        btnSuccess.setOnClickListener(v -> handleSuccessButton());
    }

    private void handleButton(int buttonId) {
        boolean wasActive = (buttonId == 1) ? btn1Active : btn2Active;
        if (buttonId == 1) { btn1Active = !btn1Active; btn2Active = false; }
        else { btn2Active = !btn2Active; btn1Active = false; }
        updateButtonVisuals();
        animateButtonTap(buttonId == 1 ? btnAction1 : btnAction2);
        if (!wasActive && lastButtonPress != 0 && lastButtonPress != buttonId) {
            totalPoints += 2;
            tvPointsBadge.setText("pts: " + totalPoints);
            flashPointsBadge();
            btn1Active = false; btn2Active = false; lastButtonPress = 0;
            updateButtonVisuals();
            callback.onComboAchieved();
        } else {
            lastButtonPress = buttonId;
        }
    }

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

    private void setupDrag() {
        overlayView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = layoutParams.x; initialY = layoutParams.y;
                    initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) isDragging = true;
                    if (isDragging) {
                        layoutParams.x = initialX + (int) dx;
                        layoutParams.y = initialY + (int) dy;
                        windowManager.updateViewLayout(overlayView, layoutParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (isDragging) { snapToEdge(); return true; }
                    v.performClick();
                    return false;
            }
            return false;
        });
    }

    private void snapToEdge() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int widgetWidth = overlayView.getWidth();
        int targetX = (layoutParams.x + widgetWidth / 2 < screenWidth / 2) ? 20
                : (screenWidth - widgetWidth - 20);
        ValueAnimator snapAnim = ValueAnimator.ofInt(layoutParams.x, targetX);
        snapAnim.setDuration(220);
        snapAnim.addUpdateListener(anim -> {
            layoutParams.x = (int) anim.getAnimatedValue();
            if (overlayView.isAttachedToWindow())
                windowManager.updateViewLayout(overlayView, layoutParams);
        });
        snapAnim.start();
    }

    private void animateButtonTap(View btn) {
        btn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
           .withEndAction(() -> btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
    }

    private void flashPointsBadge() {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(tvPointsBadge, "scaleX", 1f, 1.5f, 1f);
        pulse.setDuration(400); pulse.start();
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(tvPointsBadge, "scaleY", 1f, 1.5f, 1f);
        pulseY.setDuration(400); pulseY.start();
    }

    private void animatePointsCount(int from, int to) {
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(800);
        anim.addUpdateListener(a -> tvTotalPoints.setText(String.valueOf(a.getAnimatedValue())));
        anim.start();
    }

    private static String formatTime(int totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String formatSessionLabel(int totalSeconds) {
        if (totalSeconds <= 0) return "session complete";
        int minutes = totalSeconds / 60;
        if (minutes >= 60) {
            int h = minutes / 60, m = minutes % 60;
            return m > 0 ? h + "h " + m + "m remaining" : h + "h remaining";
        }
        return minutes + " min remaining";
    }

    public interface OverlayCallback {
        void onComboAchieved();
        void onSuccessPressed();
        void onCloseRequested();
    }
}
