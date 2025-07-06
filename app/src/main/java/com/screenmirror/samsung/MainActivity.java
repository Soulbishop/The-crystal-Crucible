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

import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.TouchInputService; // Assuming you need to reference this

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private static final int REQUEST_CODE_ACCESSIBILITY_SETTINGS = 2;

    public static final String ACTION_START_STREAMING = "com.screenmirror.samsung.START_STREAMING";
    public static final String ACTION_STOP_STREAMING = "com.screenmirror.samsung.STOP_STREAMING";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection; // Keep reference if needed for callback

    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    private TextView statusText;
    private TextView ipAddressText;
    private TextView deviceName; // From layout

    // Assuming these exist in your R.string
    private static final String STATUS_READY = "Status: Ready to start";
    private static final String STATUS_PERMISSION_DENIED = "Status: Screen capture permission denied";
    private static final String STATUS_STREAMING = "Status: Streaming active";
    private static final String STATUS_SERVICE_NOT_RUNNING = "Status: Service not running";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements (corrected IDs)
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        settingsButton = findViewById(R.id.settingsButton);
        statusText = findViewById(R.id.statusText);
        ipAddressText = findViewById(R.id.ipAddressText);
        deviceName = findViewById(R.id.deviceName); // From layout

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
        updateUI(); // Update UI when returning to activity (e.g., after granting permission)
    }

    private void requestScreenCapturePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Check if service is already running, prevent multiple requests
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
                // MediaProjection is now acquired in the service, so we pass the result code and data
                startStreamingService(resultCode, data);
            } else {
                Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                statusText.setText(STATUS_PERMISSION_DENIED);
                updateUI();
            }
        }
    }

    private void startStreamingService(int resultCode, Intent data) {
        // Ensure the StreamingService is not already running to avoid issues
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
        serviceIntent.putExtra("resultData", data); // Pass the entire Intent object
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
            Toast.makeText(this, "Stopping screen mirroring.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Screen mirroring is not active.", Toast.LENGTH_SHORT).show();
        }
        updateUI();
    }

    private void openAccessibilitySettings() {
        // Check if the service is already enabled
        if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Accessibility service is already enabled.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // It's good practice to provide a package name or component if you want to direct user
        // to your specific service's settings, but for general accessibility, this is fine.
        intent.setData(Uri.parse("package:" + getPackageName())); // Directs to app's accessibility settings if supported
        startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY_SETTINGS);
        Toast.makeText(this, "Please enable 'Screen Mirror' accessibility service.", Toast.LENGTH_LONG).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        // This is a common way to check if an accessibility service is enabled.
        // It's robust but might require parsing a lot of data.
        // For simplicity, a direct check:
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
        // Update status text
        if (StreamingService.isRunning()) {
            statusText.setText(STATUS_STREAMING);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            // Display IP address when streaming is active
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            String ipAddressString = Formatter.formatIpAddress(ipAddress);
            ipAddressText.setText("Device IP: " + ipAddressString + ":" + StreamingService.WEBSOCKET_PORT); // Assumed WEBSOCKET_PORT is public static final
            ipAddressText.setVisibility(TextView.VISIBLE);
        } else {
            statusText.setText(STATUS_READY);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            ipAddressText.setVisibility(TextView.GONE); // Hide IP when not streaming
        }

        // Always check accessibility service for the settings button
        if (isAccessibilityServiceEnabled()) {
            settingsButton.setText("ACCESSIBILITY SERVICE ENABLED");
            settingsButton.setEnabled(false); // Disable if already enabled
            settingsButton.setBackgroundTint(getResources().getColor(android.R.color.holo_green_dark)); // Green if enabled
        } else {
            settingsButton.setText("OPEN ACCESSIBILITY SETTINGS");
            settingsButton.setEnabled(true);
            settingsButton.setBackgroundTint(getResources().getColor(android.R.color.darker_gray)); // Gray if not enabled
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // MediaProjection is stopped by StreamingService onDestroy
        // No need to stop it here directly if service manages it
    }

    // This callback is usually handled within the MediaProjection lifecycle
    // and is not directly part of MainActivity's MediaProjection object unless it's managed here.
    // If you explicitly set a callback here, ensure it's a valid override.
    // Given the previous error, removing problematic override (if any) or ensuring correct signature.
    // Example of a correct MediaProjection.Callback if you were to use one in MainActivity:
    private MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.i(TAG, "MediaProjection stopped.");
            // Handle cleanup if MediaProjection stops unexpectedly
            // This might trigger UI updates or service stopping if not already done
            if (StreamingService.isRunning()) {
                stopStreamingService();
            }
            mediaProjection = null; // Clear reference
            updateUI();
        }

        // Correct signature for onCapturedContentVisibilityChanged
        @Override
        public void onCapturedContentVisibilityChanged(int visibility) {
            Log.d(TAG, "Captured content visibility changed: " + visibility);
            // No super call needed here
            // You can add logic here if you need to react to content visibility changes
        }
    };
}
