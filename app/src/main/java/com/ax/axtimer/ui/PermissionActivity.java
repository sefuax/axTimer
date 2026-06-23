package com.ax.axtimer.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ax.axtimer.R;
import com.ax.axtimer.service.TimerService;
import com.ax.axtimer.utils.TimerConstants;

/**
 * PermissionActivity
 * ──────────────────
 * Handles the SYSTEM_ALERT_WINDOW (overlay) permission flow.
 * Launched by HomeActivity when the permission is not yet granted.
 * Returns the user back to the timer flow once permission is granted.
 */
public class PermissionActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private int pendingDurationMinutes = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        // Retrieve the duration the user selected before hitting permission wall
        pendingDurationMinutes = getIntent().getIntExtra(
                TimerConstants.EXTRA_DURATION_MINUTES, -1);

        // Grant permission button
        View btnGrant = findViewById(R.id.btnGrantPermission);
        btnGrant.setOnClickListener(v -> requestOverlayPermission());
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                // Permission granted — launch the service
                if (pendingDurationMinutes > 0) {
                    Intent serviceIntent = new Intent(this, TimerService.class);
                    serviceIntent.setAction(TimerConstants.ACTION_START);
                    serviceIntent.putExtra(TimerConstants.EXTRA_DURATION_MINUTES,
                            pendingDurationMinutes);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    Toast.makeText(this,
                        "Session started! Floating timer will appear.",
                        Toast.LENGTH_SHORT).show();
                }
                finish();
            } else {
                Toast.makeText(this,
                    "Overlay permission is required to show the floating timer.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }
}
