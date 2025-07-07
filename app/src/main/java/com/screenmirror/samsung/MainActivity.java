package com.screenmirror.samsung;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName; // Added for TouchInputService.isAccessibilityServiceEnabled
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
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

    private static final String TAG = "MainActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1000;
    private static final int REQUEST_PERMISSIONS = 1001;

    // FIX: Added these constants for use in StreamingService
    public static final String ACTION_START_STREAMING = "com.screenmirror.samsung.START_STREAMING";
    public static final String ACTION_STOP_STREAMING = "com.screenmirror.samsung.STOP_STREAMING";

    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    private TextView statusText;
    private TextView ipAddressText;

    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
        updateUI();
        checkPermissions();
    }

    private void initializeViews() {
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        statusText = findViewById(R.id.statusText);
        ipAddressText = findViewById(R.id.ipAddressText);

        displayIPAddress();
    }

    private void setupClickListeners() {
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScreenMirroring();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScreenMirroring();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
    }

    private void displayIPAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
            ipAddressText.setText("Device IP: " + ipAddress + ":8080");
        } catch (Exception e) {
            ipAddressText.setText("IP Address: Unable to determine");
            Log.e(TAG, "Error getting IP address", e);
        }
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }
    }

    private void startScreenMirroring() {
        // FIX: isAccessibilityServiceEnabled check relies on the method being in TouchInputService
        if (!TouchInputService.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }

    private void stopScreenMirroring() {
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, StreamingService.class));
        stopService(new Intent(this, DiscoveryService.class));

        isServiceRunning = false;
        updateUI();

        Toast.makeText(this, "Screen mirroring stopped", Toast.LENGTH_SHORT).show();
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Enable 'Screen Mirror Touch Input' service", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Starting services...", Toast.LENGTH_SHORT).show();

                // Start ScreenCaptureService with proper data
                Intent screenCaptureIntent = new Intent(this, ScreenCaptureService.class);
                screenCaptureIntent.putExtra("resultCode", resultCode);
                screenCaptureIntent.putExtra("data", data);

                try {
                    startForegroundService(screenCaptureIntent);
                    Toast.makeText(this, "ScreenCaptureService started", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to start ScreenCaptureService: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error starting ScreenCaptureService", e);
                    return;
                }

                // Start other services
                try {
                    startService(new Intent(this, StreamingService.class));
                    startService(new Intent(this, DiscoveryService.class));

                    isServiceRunning = true;
                    updateUI();

                    Toast.makeText(this, "All services started successfully", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    Toast.makeText(this, "Error starting other services: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error starting other services", e);
                }

            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { // FIX: Removed @NonNull from parameters
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "Some permissions were denied. App may not work correctly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateUI() {
        if (isServiceRunning) {
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusText.setText("Status: Screen mirroring active");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusText.setText("Status: Ready to start");
            statusText.setTextColor(getResources().getColor(android.R.color.primary_text_light));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayIPAddress();

        // FIX: isAccessibilityServiceEnabled check relies on the method being in TouchInputService
        if (TouchInputService.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, "Accessibility service is enabled", Toast.LENGTH_SHORT).show();
        }
    }
}
