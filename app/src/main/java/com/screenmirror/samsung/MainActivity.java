package com.screenmirror.samsung;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection; // Keep this import
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Parcelable; // This import is correct and needed
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
    private static final int REQUEST_ACCESSIBILITY = 1001; // Restored constant
    private static final int REQUEST_OVERLAY = 1002;       // Restored constant
    private static final int REQUEST_PERMISSIONS = 1003;   // Restored original constant value

    private MediaProjectionManager mediaProjectionManager; // Global declaration
    // private MediaProjection mediaProjection; // This is not needed globally in MainActivity after being passed

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
        
        // CRITICAL FIX: Initialize mediaProjectionManager globally here
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        updateUI(); // Initial UI update
        checkPermissions(); // Check permissions on startup
        displayIPAddress(); // Display IP initially
    }

    private void initializeViews() {
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        statusText = findViewById(R.id.statusText);
        ipAddressText = findViewById(R.id.ipAddressText);
    }

    private void setupClickListeners() {
        startButton.setOnClickListener(v -> startScreenMirroring()); // Using lambda for brevity
        stopButton.setOnClickListener(v -> stopScreenMirroring());   // Using lambda for brevity
        settingsButton.setOnClickListener(v -> openAccessibilitySettings()); // Using lambda for brevity
    }

    private void displayIPAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.getConnectionInfo() != null) { // Added null check
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                String ip = Formatter.formatIpAddress(ipAddress);
                ipAddressText.setText("Device IP: " + ip + ":" + 8080); // Using 8080 as streaming port
            } else {
                ipAddressText.setText("IP Address: Wi-Fi not connected");
                Log.w(TAG, "Wi-Fi not connected or WifiManager is null, cannot get IP.");
            }
        } catch (Exception e) {
            ipAddressText.setText("IP Address: Error getting IP"); // Changed message
            Log.e(TAG, "Error getting IP address", e);
        }
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO, // New: If needed, must also be in AndroidManifest.xml
            Manifest.permission.CAMERA,       // New: If needed, must also be in AndroidManifest.xml
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE // Good to include for clarity in request
        };

        boolean allRuntimePermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allRuntimePermissionsGranted = false;
                break;
            }
        }

        // Request runtime permissions if any are missing
        if (!allRuntimePermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }

        // Check overlay permission (SYSTEM_ALERT_WINDOW) - handled correctly here, outside runtime array
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_OVERLAY);
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show();
        }

        // Check accessibility permission (BIND_ACCESSIBILITY_SERVICE) - handled correctly here
        // CRITICAL FIX: Changed isEnabled to isAccessibilityServiceEnabled
        if (!TouchInputService.isAccessibilityServiceEnabled(this)) {
            showAccessibilityDialog();
        }
    }

    private void startScreenMirroring() {
        // CRITICAL FIX: Changed isEnabled to isAccessibilityServiceEnabled
        if (!TouchInputService.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show();
            openAccessibilitySettings(); // Call the correct method name
            return;
        }
        
        // Ensure overlay permission is granted before starting capture
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps' permission first", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }

        // Request media projection permission
        Intent intent = mediaProjectionManager.createScreenCaptureIntent(); // Uses the globally initialized manager
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }

    private void stopScreenMirroring() {
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, StreamingService.class));
        stopService(new Intent(this, DiscoveryService.class));

        // mediaProjection is no longer managed by MainActivity, so no need to stop it here.
        // It's handled by ScreenCaptureService.

        isServiceRunning = false;
        updateUI();

        Toast.makeText(this, "Screen mirroring stopped", Toast.LENGTH_SHORT).show();
    }

    private void openAccessibilitySettings() { // Renamed from openSettings
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Enable 'Screen Mirror Touch Input' service", Toast.LENGTH_LONG).show();
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
                    startForegroundService(screenCaptureIntent); // Use startForegroundService for Android O+
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
        // CRITICAL FIX: Restore handling for special permission results
        else if (requestCode == REQUEST_ACCESSIBILITY) {
            if (TouchInputService.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(this, "Accessibility service enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Accessibility service NOT enabled", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
                // If overlay was just granted, and services depend on it, you might want to re-try starting services
                // For now, just a toast. User has to click start again.
            } else {
                Toast.makeText(this, "Overlay permission NOT granted", Toast.LENGTH_LONG).show();
            }
        }
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
                Toast.makeText(this, "All required runtime permissions granted", Toast.LENGTH_SHORT).show();
                // You might want to call checkPermissions() again here to re-check special permissions
                // checkPermissions();
            } else {
                Toast.makeText(this, "Some essential runtime permissions were denied, app may not function fully.", Toast.LENGTH_LONG).show();
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
            // Better color choice:
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray)); // Changed from primary_text_light
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayIPAddress(); // Update IP on resume

        // Check if accessibility service is enabled on resume, and update UI or prompt if needed
        if (!TouchInputService.isAccessibilityServiceEnabled(this)) {
            // No need for a toast every time on resume, just if not enabled
            // showAccessibilityDialog(); // Or re-prompt if you want
        } else {
            // Toast.makeText(this, "Accessibility service is enabled", Toast.LENGTH_SHORT).show(); // Too noisy
        }
        updateUI(); // Update UI based on current service status
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceRunning) {
            stopScreenMirroring(); // Ensure services are stopped on Activity destroy
        }
    }
}
