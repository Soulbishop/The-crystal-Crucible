package com.screenmirror.samsung;

import android.os.Parcelable; // This import is correct and needed
import android.media.projection.MediaProjection; // This import is correct and needed
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
// REMOVED duplicate MediaProjection import (Line 7) - it's already on Line 2
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenmirror.samsung.service.ScreenCaptureService;
import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.TouchInputService;
import com.screenmirror.samsung.service.DiscoveryService;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1000;
    private static final int REQUEST_ACCESSIBILITY = 1001;
    private static final int REQUEST_OVERLAY = 1002;
    private static final int REQUEST_PERMISSIONS = 1003;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    private TextView statusText;
    private TextView ipAddressText;

    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
        checkPermissions();

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        updateUI();
        displayIPAddress();
    }

    private void initializeViews() {
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        statusText = findViewById(R.id.statusText);
        ipAddressText = findViewById(R.id.ipAddressText);
    }

    private void setupClickListeners() {
        startButton.setOnClickListener(v -> startScreenMirroring());
        stopButton.setOnClickListener(v -> stopScreenMirroring());
        settingsButton.setOnClickListener(v -> openSettings());
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_OVERLAY);
        }

        // Check accessibility permission
        if (!TouchInputService.isAccessibilityServiceEnabled(this)) { // This call is now correct as per previous fix
            showAccessibilityDialog();
        }
    }

    private void startScreenMirroring() {
        if (isRunning) {
            Toast.makeText(this, "Screen mirroring is already running", Toast.LENGTH_SHORT).show();
            return;
        }

        // Request media projection permission
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }

    private void stopScreenMirroring() {
        if (!isRunning) {
            Toast.makeText(this, "Screen mirroring is not running", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop all services
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, StreamingService.class));
        stopService(new Intent(this, DiscoveryService.class));

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        isRunning = false;
        updateUI();

        Toast.makeText(this, "Screen mirroring stopped", Toast.LENGTH_SHORT).show();
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void showAccessibilityDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Accessibility Service Required")
            .setMessage("To control your device from the iPad, please enable the Screen Mirror Touch Input service in Accessibility Settings.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivityForResult(intent, REQUEST_ACCESSIBILITY);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void displayIPAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String ip = Formatter.formatIpAddress(ipAddress);
            ipAddressText.setText("Device IP: " + ip + ":8080");
        }
    }

    private void updateUI() {
        if (isRunning) {
            startButton.setVisibility(View.GONE);
            stopButton.setVisibility(View.VISIBLE);
            statusText.setText("Screen mirroring is active");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            startButton.setVisibility(View.VISIBLE);
            stopButton.setVisibility(View.GONE);
            statusText.setText("Screen mirroring is stopped");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                startServices(resultCode, data); // MODIFIED: Pass resultCode and data
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_ACCESSIBILITY) {
            if (TouchInputService.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(this, "Accessibility service enabled", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // MODIFIED: startServices now accepts resultCode and data
    private void startServices(int resultCode, Intent data) {
        // Start screen capture service
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        // MODIFIED: Pass result code and data to the service directly,
        // so the service can get its own MediaProjection
        captureIntent.putExtra("resultCode", resultCode);
        captureIntent.putExtra("data", data);
        startForegroundService(captureIntent);

        // Start streaming service
        Intent streamingIntent = new Intent(this, StreamingService.class);
        startService(streamingIntent);

        // Start discovery service
        Intent discoveryIntent = new Intent(this, DiscoveryService.class);
        startService(discoveryIntent);

        isRunning = true;
        updateUI();

        Toast.makeText(this, "Screen mirroring started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRunning) {
            stopScreenMirroring();
        }
    }
}
