package com.screenmirror.samsung;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.net.wifi.WifiManager; // Added import for WifiManager
import android.text.format.Formatter; // Added import for Formatter
import android.content.res.ColorStateList; // Added import for ColorStateList
import androidx.core.content.ContextCompat; // Added import for ContextCompat for modern color retrieval

import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.TouchInputService;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private static final int REQUEST_CODE_ACCESSIBILITY_SETTINGS = 2;

    public static final String ACTION_START_STREAMING = "com.screenmirror.samsung.START_STREAMING";
    public static final String ACTION_STOP_STREAMING = "com.screenmirror.samsung.STOP_STREAMING";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection; // Kept reference for callback registration/unregistration

    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    private TextView statusText;
    private TextView ipAddressText;
    private TextView deviceName;

    private static final String STATUS_READY = "Status: Ready to start";
    private static final String STATUS_PERMISSION_DENIED = "Status: Screen capture permission denied";
    private static final String STATUS_STREAMING = "Status: Streaming active";
    // private static final String STATUS_SERVICE_NOT_RUNNING = "Status: Service not running"; // Can be removed if not used


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements (corrected IDs based on activity_main.xml)
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        statusText = findViewById(R.id.statusText);
        ipAddressText = findViewById(R.id.ipAddressText);
        deviceName = findViewById(R.id.deviceName);

        // Set device name placeholder (you might want to dynamically get this)
        deviceName.setText(Build.MANUFACTURER + " " + Build.MODEL);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        startButton.setOnClickListener(v -> requestScreenCapturePermission());
        stopButton.setOnClickListener(v -> stopStreamingService());
        settingsButton.setOnClickListener(v -> openAccessibilitySettings());

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI(); // Update UI when returning to activity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister MediaProjection callback if it was registered and MediaProjection object exists
        if (mediaProjection != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
            }
            // MediaProjection.stop() is handled by the service when it stops,
            // or explicitly by stopStreamingService() if called from UI.
        }
    }

    private void requestScreenCapturePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (StreamingService.isRunning()) {
                Toast.makeText(this, "Screen mirroring is already active.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        } else {
            Toast.makeText(this, "Screen mirroring requires Android 5.0 (API 21) or higher.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Acquire MediaProjection in MainActivity to register its callback, then pass data to service
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                    if (mediaProjection != null) {
                        // Register the callback now that mediaProjection is available
                        mediaProjection.registerCallback(mediaProjectionCallback, null);
                        Log.d(TAG, "MediaProjection acquired and callback registered in MainActivity.");
                        startStreamingService(resultCode, data); // Pass data to service
                    } else {
                        Log.e(TAG, "Failed to get MediaProjection in MainActivity after permission.");
                        Toast.makeText(this, "Failed to start screen capture.", Toast.LENGTH_SHORT).show();
                        statusText.setText(STATUS_PERMISSION_DENIED);
                        updateUI();
                    }
                }
            } else {
                Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                statusText.setText(STATUS_PERMISSION_DENIED);
                updateUI();
            }
        }
        // For accessibility settings, no direct action needed on result, onResume handles UI update
        if (requestCode == REQUEST_CODE_ACCESSIBILITY_SETTINGS) {
            updateUI(); // Refresh UI after user returns from settings
        }
    }

    private void startStreamingService(int resultCode, Intent data) {
        if (StreamingService.isRunning()) {
            Log.w(TAG, "Attempted to start StreamingService, but it's already running.");
            Toast.makeText(this, "Streaming is already active.", Toast.LENGTH_SHORT).show();
            updateUI();
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);

        Intent serviceIntent = new Intent(this, StreamingService.class);
        serviceIntent.setAction(ACTION_START_STREAMING);
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("resultData", data);
        serviceIntent.putExtra("width", metrics.widthPixels);
        serviceIntent.putExtra("height", metrics.heightPixels);
        serviceIntent.putExtra("density", metrics.densityDpi);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Starting screen mirroring...", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void stopStreamingService() {
        if (StreamingService.isRunning()) {
            Intent serviceIntent = new Intent(this, StreamingService.class);
            serviceIntent.setAction(ACTION_STOP_STREAMING);
            stopService(serviceIntent);
            // Also stop MediaProjection and unregister its callback if it's managed by MainActivity
            if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
                mediaProjection.stop(); // This explicitly stops the MediaProjection session
                mediaProjection = null; // Clear the reference
            }
            Toast.makeText(this, "Stopping screen mirroring.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Screen mirroring is not active.", Toast.LENGTH_SHORT).show();
        }
        updateUI();
    }

    private void openAccessibilitySettings() {
        if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Accessibility service is already enabled.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // Directs to app's specific accessibility settings if supported, otherwise general settings
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY_SETTINGS);
        Toast.makeText(this, "Please enable 'Screen Mirror' accessibility service.", Toast.LENGTH_LONG).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + TouchInputService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(this.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (settingValue != null) {
                    return settingValue.contains(service);
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Accessibility settings not found: " + e.getMessage());
        }
        return false;
    }


    private void updateUI() {
        // Update status text and button states
        if (StreamingService.isRunning()) {
            statusText.setText(STATUS_STREAMING);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            // Display IP address when streaming is active
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String ipAddressString = Formatter.formatIpAddress(ipAddress);
            ipAddressText.setText("Device IP: " + ipAddressString + ":" + StreamingService.WEBSOCKET_PORT);
            ipAddressText.setVisibility(TextView.VISIBLE);
        } else {
            statusText.setText(STATUS_READY);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            ipAddressText.setVisibility(TextView.GONE);
        }

        // Always check accessibility service for the settings button
        if (isAccessibilityServiceEnabled()) {
            settingsButton.setText("ACCESSIBILITY SERVICE ENABLED");
            settingsButton.setEnabled(false);
            // Using ContextCompat.getColor for modern Android color retrieval
            settingsButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_dark)));
        } else {
            settingsButton.setText("OPEN ACCESSIBILITY SETTINGS");
            settingsButton.setEnabled(true);
            settingsButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.darker_gray)));
        }
    }

    // MediaProjection.Callback for handling MediaProjection session events
    private MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.i(TAG, "MediaProjection stopped by system or user.");
            // If the MediaProjection stops unexpectedly (e.g., user revokes permission from quick settings)
            // Ensure the streaming service is also stopped and UI updated.
            if (StreamingService.isRunning()) {
                stopStreamingService(); // This will also unregister and stop MediaProjection if it was started here
            }
            mediaProjection = null; // Clear reference
            updateUI(); // Refresh UI to reflect stopped state
        }

        @Override
        public void onCapturedContentVisibilityChanged(int visibility) {
            // This method is called when the visibility of the captured content changes (e.g., app goes to background)
            Log.d(TAG, "Captured content visibility changed: " + visibility);
            // No super call needed here. You can add logic to react to visibility changes if needed.
        }
    };
}
