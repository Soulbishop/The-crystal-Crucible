package com.screenmirror.samsung;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenmirror.samsung.service.ScreenCaptureService;
import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.TouchInputService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1;

    public static final String ACTION_START_STREAMING = "com.screenmirror.samsung.START_STREAMING";
    public static final String ACTION_STOP_STREAMING = "com.screenmirror.samsung.STOP_STREAMING";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    private Button startMirroringButton;
    private Button stopMirroringButton;
    private TextView statusTextView;

    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // MediaProjection callbacks for Android 14+
    private MediaProjection.Callback mediaProjectionCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startMirroringButton = findViewById(R.id.start_mirroring_button);
        stopMirroringButton = findViewById(R.id.stop_mirroring_button);
        statusTextView = findViewById(R.id.status_text_view);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // Initialize ActivityResultLauncher for MediaProjection
        mediaProjectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(result.getResultCode(), result.getData());
                        if (mediaProjection != null) {
                            Log.d(TAG, "MediaProjection obtained successfully.");
                            setupMediaProjectionCallbacks(); // Setup callbacks for Android 14+
                            startScreenMirroring();
                        } else {
                            Log.e(TAG, "Failed to get MediaProjection after user consent.");
                            Toast.makeText(this, "Failed to get screen capture permission.", Toast.LENGTH_SHORT).show();
                            updateStatus("Status: Permission Denied");
                        }
                    } else {
                        Log.w(TAG, "MediaProjection permission denied by user.");
                        Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                        updateStatus("Status: Permission Denied");
                    }
                });

        // Initialize ActivityResultLauncher for POST_NOTIFICATIONS permission
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                        requestMediaProjection();
                    } else {
                        Log.w(TAG, "POST_NOTIFICATIONS permission denied.");
                        Toast.makeText(this, "Notification permission denied. Mirroring may not work correctly.", Toast.LENGTH_LONG).show();
                        updateStatus("Status: Notification Permission Denied");
                    }
                });

        startMirroringButton.setOnClickListener(v -> checkPermissionsAndStartMirroring());
        stopMirroringButton.setOnClickListener(v -> stopScreenMirroring());

        updateStatus("Status: Ready to Mirror");

        // Ensure accessibility service is enabled for touch input
        checkAccessibilityServiceStatus();
    }

    private void checkPermissionsAndStartMirroring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                requestMediaProjection();
            }
        } else {
            requestMediaProjection();
        }
    }

    private void requestMediaProjection() {
        Log.d(TAG, "Requesting MediaProjection permission.");
        updateStatus("Status: Requesting Screen Capture...");
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
    }

    private void setupMediaProjectionCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
            mediaProjectionCallback = new MediaProjection.Callback() {
                @Override
                public void onCapturedContentResize(int width, int height) {
                    super.onCapturedContentResize(width, height);
                    Log.d(TAG, "onCapturedContentResize: " + width + "x" + height);
                    // Handle resolution change if necessary, e.g., restart virtual display
                }

                @Override
                public void onCapturedContentVisibilityChanged(int visibility) {
                    super.onCapturedContentVisibilityChanged(visibility);
                    Log.d(TAG, "onCapturedContentVisibilityChanged: " + visibility);
                    // Handle visibility change
                }

                @Override
                public void onStop() {
                    super.onStop();
                    Log.d(TAG, "MediaProjection onStop callback triggered.");
                    Toast.makeText(MainActivity.this, "Screen capture stopped by system.", Toast.LENGTH_SHORT).show();
                    stopScreenMirroring(); // Automatically stop mirroring if MediaProjection stops
                }
            };
            mediaProjection.registerCallback(mediaProjectionCallback, new Handler(Looper.getMainLooper()));
            Log.d(TAG, "MediaProjection callbacks registered for Android 14+.");
        } else {
            // For older Android versions, MediaProjection.Callback doesn't have onCapturedContent* methods,
            // but the onStop() method is available and should be registered.
            mediaProjectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    Log.d(TAG, "MediaProjection onStop callback triggered (Legacy).");
                    Toast.makeText(MainActivity.this, "Screen capture stopped by system.", Toast.LENGTH_SHORT).show();
                    stopScreenMirroring(); // Automatically stop mirroring if MediaProjection stops
                }
            };
            mediaProjection.registerCallback(mediaProjectionCallback, new Handler(Looper.getMainLooper()));
            Log.d(TAG, "MediaProjection onStop callback registered for legacy Android.");
        }
    }


    private void startScreenMirroring() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null. Cannot start mirroring.");
            Toast.makeText(this, "Permission not granted for screen capture.", Toast.LENGTH_SHORT).show();
            updateStatus("Status: Error - Permission Not Granted");
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        // Corrected line 248: Ensure the string literal is properly closed.
        Log.d(TAG, "Starting services for mirroring. Resolution: " + width + "x" + height + " Density: " + density);
        updateStatus("Status: Streaming Active");

        // Start StreamingService
        Intent streamingServiceIntent = new Intent(this, StreamingService.class);
        streamingServiceIntent.setAction(ACTION_START_STREAMING);
        streamingServiceIntent.putExtra("mediaProjection", mediaProjection);
        streamingServiceIntent.putExtra("width", width);
        streamingServiceIntent.putExtra("height", height);
        streamingServiceIntent.putExtra("density", density);
        startForegroundService(streamingServiceIntent);

        startMirroringButton.setEnabled(false);
        stopMirroringButton.setEnabled(true);
    }

    private void stopScreenMirroring() {
        Log.d(TAG, "Stopping screen mirroring.");
        updateStatus("Status: Stopping Streaming...");

        // Stop StreamingService
        Intent streamingServiceIntent = new Intent(this, StreamingService.class);
        streamingServiceIntent.setAction(ACTION_STOP_STREAMING);
        stopService(streamingServiceIntent);

        if (mediaProjection != null) {
            if (mediaProjectionCallback != null) {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
            }
            mediaProjection.stop();
            mediaProjection = null;
            Log.d(TAG, "MediaProjection stopped.");
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
            Log.d(TAG, "VirtualDisplay released.");
        }

        startMirroringButton.setEnabled(true);
        stopMirroringButton.setEnabled(false);
        updateStatus("Status: Mirroring Stopped");
        Toast.makeText(this, "Screen mirroring stopped.", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> statusTextView.setText(status));
    }

    private void checkAccessibilityServiceStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled(this, TouchInputService.class);
        if (!accessibilityEnabled) {
            Log.w(TAG, "Accessibility service for touch input is NOT enabled. Prompting user.");
            Toast.makeText(this, "Please enable 'The Crystal Crucible Touch Input' in Accessibility settings for touch control.", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Accessibility service for touch input is enabled.");
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String service = context.getPackageName() + "/" + serviceClass.getCanonicalName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error finding setting ACCESSIBILITY_ENABLED: " + e.getMessage());
        }
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy.");
        if (mediaProjection != null) {
            if (mediaProjectionCallback != null) {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
            }
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
