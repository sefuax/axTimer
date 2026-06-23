package com.ax.axtimer.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ax.axtimer.R;

/**
 * SplashActivity
 * ──────────────
 * Premium 3-second splash screen featuring:
 *  - Radial glow pulse behind the AX logo
 *  - Scale + fade in of the main logo text
 *  - Staggered fade in of "Made By AX" tagline and owner handle
 *  - Animated expanding divider line
 *  - Loading dots at the bottom
 *  - Smooth fade-out before navigating to HomeActivity
 */
public class SplashActivity extends AppCompatActivity {

    // Total splash duration: 3 seconds
    private static final long SPLASH_DURATION_MS = 3000L;

    private View    viewGlow;
    private View    layoutContent;
    private TextView tvLogo;
    private TextView tvMadeBy;
    private TextView tvOwner;
    private View    viewDivider;
    private View    layoutDots;
    private View    dot1, dot2, dot3;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide system UI for immersive splash
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        // Bind views
        viewGlow       = findViewById(R.id.viewGlow);
        layoutContent  = findViewById(R.id.layoutSplashContent);
        tvLogo         = findViewById(R.id.tvSplashLogo);
        tvMadeBy       = findViewById(R.id.tvMadeBy);
        tvOwner        = findViewById(R.id.tvSplashOwner);
        viewDivider    = findViewById(R.id.viewDivider);
        layoutDots     = findViewById(R.id.layoutDots);
        dot1           = findViewById(R.id.dot1);
        dot2           = findViewById(R.id.dot2);
        dot3           = findViewById(R.id.dot3);

        // Begin animations after a short layout pass
        handler.postDelayed(this::startSplashAnimations, 100);

        // Navigate after SPLASH_DURATION_MS
        handler.postDelayed(this::navigateToHome, SPLASH_DURATION_MS);
    }

    /**
     * Orchestrated animation sequence:
     *  0ms   → glow pulses in
     *  200ms → logo scales + fades in
     *  700ms → divider line expands
     *  900ms → "Made By AX" fades in
     *  1100ms → owner handle fades in
     *  1300ms → loading dots appear
     *  1500ms → dots animate one by one
     */
    private void startSplashAnimations() {
        // 1. Glow pulse
        animateGlow();

        // 2. Logo scale + fade
        handler.postDelayed(() -> {
            ObjectAnimator scaleX  = ObjectAnimator.ofFloat(layoutContent, "scaleX", 0.6f, 1f);
            ObjectAnimator scaleY  = ObjectAnimator.ofFloat(layoutContent, "scaleY", 0.6f, 1f);
            ObjectAnimator alphaIn = ObjectAnimator.ofFloat(layoutContent, "alpha", 0f, 1f);
            scaleX.setInterpolator(new OvershootInterpolator(1.2f));
            scaleY.setInterpolator(new OvershootInterpolator(1.2f));
            alphaIn.setInterpolator(new DecelerateInterpolator());
            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY, alphaIn);
            set.setDuration(800);
            set.start();
        }, 200);

        // 3. Divider expands
        handler.postDelayed(() -> {
            viewDivider.setAlpha(1f);
            ValueAnimator widthAnim = ValueAnimator.ofInt(0, dpToPx(60));
            widthAnim.setDuration(400);
            widthAnim.setInterpolator(new DecelerateInterpolator());
            widthAnim.addUpdateListener(anim -> {
                viewDivider.getLayoutParams().width = (int) anim.getAnimatedValue();
                viewDivider.requestLayout();
            });
            widthAnim.start();
        }, 700);

        // 4. Made By AX
        handler.postDelayed(() ->
            tvMadeBy.animate().alpha(1f).setDuration(500).start(), 900);

        // 5. Owner handle
        handler.postDelayed(() ->
            tvOwner.animate().alpha(1f).setDuration(500).start(), 1100);

        // 6. Dots container
        handler.postDelayed(() ->
            layoutDots.animate().alpha(1f).setDuration(400).start(), 1300);

        // 7. Dot pulse sequence
        handler.postDelayed(this::animateDots, 1500);
    }

    /** Pulsing radial glow behind the AX logo */
    private void animateGlow() {
        viewGlow.setAlpha(0f);
        viewGlow.setScaleX(0.5f);
        viewGlow.setScaleY(0.5f);

        ObjectAnimator alpha  = ObjectAnimator.ofFloat(viewGlow, "alpha", 0f, 0.7f, 0.4f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(viewGlow, "scaleX", 0.5f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(viewGlow, "scaleY", 0.5f, 1.2f, 1f);

        alpha.setInterpolator(new DecelerateInterpolator());
        scaleX.setInterpolator(new DecelerateInterpolator());
        scaleY.setInterpolator(new DecelerateInterpolator());

        AnimatorSet glowSet = new AnimatorSet();
        glowSet.playTogether(alpha, scaleX, scaleY);
        glowSet.setDuration(1200);
        glowSet.start();

        // Gentle continuous pulse after initial reveal
        glowSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pulseGlow();
            }
        });
    }

    /** Continuous slow pulse on the glow view */
    private void pulseGlow() {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(viewGlow, "alpha", 0.4f, 0.65f);
        pulse.setDuration(800);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
    }

    /** Sequentially lights up the loading dots */
    private void animateDots() {
        animateDot(dot2, 0);
        animateDot(dot3, 200);
        animateDot(dot1, 400); // wrap back
    }

    private void animateDot(View dot, long delay) {
        handler.postDelayed(() -> {
            dot.animate()
               .alpha(1f).scaleX(1.3f).scaleY(1.3f)
               .setDuration(200)
               .withEndAction(() ->
                   dot.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
               .start();
        }, delay);
    }

    /** Fade everything out, then launch HomeActivity */
    private void navigateToHome() {
        View root = findViewById(android.R.id.content);
        root.animate()
            .alpha(0f)
            .setDuration(500)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            })
            .start();
    }

    /** Convert dp to px */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
