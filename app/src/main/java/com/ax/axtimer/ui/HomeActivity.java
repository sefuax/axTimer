package com.ax.axtimer.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ax.axtimer.R;
import com.ax.axtimer.service.TimerService;
import com.ax.axtimer.utils.TimerConstants;

/**
 * HomeActivity
 * ────────────
 * Main UI for session setup. Shows duration chips and Start / Reset buttons.
 * Checks for SYSTEM_ALERT_WINDOW permission before launching the TimerService.
 */
public class HomeActivity extends AppCompatActivity {

    // Duration chips mapped to their minute values
    private static final int[] CHIP_IDS = {
        R.id.chip5, R.id.chip10, R.id.chip15,
        R.id.chip20, R.id.chip30, R.id.chip45,
        R.id.chip60, R.id.chip120, R.id.chip180
    };
    private static final int[] CHIP_MINUTES = {
        5, 10, 15, 20, 30, 45, 60, 120, 180
    };

    private int selectedMinutes = -1;   // -1 = nothing selected
    private TextView tvSelectedDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvSelectedDuration = findViewById(R.id.tvSelectedDuration);

        // Wire up all duration chips
        setupChips();

        // Start button
        View btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            animateButtonPress(v);
            handleStart();
        });

        // Reset button
        View btnReset = findViewById(R.id.btnReset);
        btnReset.setOnClickListener(v -> {
            animateButtonPress(v);
            handleReset();
        });

        // Staggered entrance animations
        playEntranceAnimations();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Duration chip setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupChips() {
        for (int i = 0; i < CHIP_IDS.length; i++) {
            final int minutes  = CHIP_MINUTES[i];
            final int chipId   = CHIP_IDS[i];
            View chip = findViewById(chipId);
            chip.setOnClickListener(v -> selectChip(chipId, minutes));
        }
    }

    /**
     * Highlights the selected chip, de-highlights all others,
     * and updates the selected duration label.
     */
    private void selectChip(int selectedId, int minutes) {
        selectedMinutes = minutes;

        for (int id : CHIP_IDS) {
            View chip = findViewById(id);
            chip.setActivated(id == selectedId);
            // Animate selected chip with a bounce
            if (id == selectedId) {
                chip.animate()
                    .scaleX(1.06f).scaleY(1.06f)
                    .setDuration(100)
                    .withEndAction(() ->
                        chip.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                    .start();
            }
        }

        // Update label
        String label = formatDuration(minutes);
        tvSelectedDuration.setText("Selected: " + label);
        tvSelectedDuration.setTextColor(getResources().getColor(R.color.ax_cyan, getTheme()));

        // Pulse the label
        tvSelectedDuration.animate()
            .alpha(0f).setDuration(80)
            .withEndAction(() ->
                tvSelectedDuration.animate().alpha(1f).setDuration(200).start())
            .start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button handlers
    // ─────────────────────────────────────────────────────────────────────────

    private void handleStart() {
        if (selectedMinutes < 0) {
            Toast.makeText(this, "Please select a session duration first", Toast.LENGTH_SHORT).show();
            shakeView(findViewById(R.id.layoutDurationCard));
            return;
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            // Route through PermissionActivity
            Intent permIntent = new Intent(this, PermissionActivity.class);
            permIntent.putExtra(TimerConstants.EXTRA_DURATION_MINUTES, selectedMinutes);
            startActivity(permIntent);
        } else {
            launchTimerService(selectedMinutes);
        }
    }

    private void handleReset() {
        selectedMinutes = -1;
        for (int id : CHIP_IDS) {
            View chip = findViewById(id);
            chip.setActivated(false);
        }
        tvSelectedDuration.setText("Select a duration above");
        tvSelectedDuration.setTextColor(getResources().getColor(R.color.ax_text_hint, getTheme()));

        // Stop any running service
        Intent stopIntent = new Intent(this, TimerService.class);
        stopIntent.setAction(TimerConstants.ACTION_STOP);
        startService(stopIntent);
    }

    /**
     * Starts the foreground timer service with the selected duration.
     * The service will then show the floating widget.
     */
    private void launchTimerService(int durationMinutes) {
        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(TimerConstants.ACTION_START);
        serviceIntent.putExtra(TimerConstants.EXTRA_DURATION_MINUTES, durationMinutes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this,
            "Session started! Floating timer will appear.", Toast.LENGTH_SHORT).show();

        // Optionally bring to background so user can immediately switch apps
        moveTaskToBack(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entrance animations
    // ─────────────────────────────────────────────────────────────────────────

    private void playEntranceAnimations() {
        Handler h = new Handler(Looper.getMainLooper());

        int[] viewIds = {
            R.id.layoutHeader,
            R.id.layoutDurationCard,
            R.id.layoutButtonsCard,
            R.id.tvFooter
        };
        long[] delays = { 50, 200, 350, 500 };

        for (int i = 0; i < viewIds.length; i++) {
            final View v = findViewById(viewIds[i]);
            if (v == null) continue;
            final long delay = delays[i];
            h.postDelayed(() -> {
                v.setTranslationY(50f);
                v.animate()
                 .alpha(1f)
                 .translationY(0f)
                 .setDuration(600)
                 .setInterpolator(new DecelerateInterpolator())
                 .start();
            }, delay);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Micro-interaction helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Brief scale-down + up on button press */
    private void animateButtonPress(View v) {
        v.animate()
         .scaleX(0.95f).scaleY(0.95f)
         .setDuration(80)
         .withEndAction(() ->
             v.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
         .start();
    }

    /** Horizontal shake on invalid action */
    private void shakeView(View v) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(v, "translationX",
            0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f);
        shake.setDuration(400);
        shake.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private String formatDuration(int minutes) {
        if (minutes < 60) return minutes + " minutes";
        int hours = minutes / 60;
        return hours + (hours == 1 ? " hour" : " hours");
    }
}
