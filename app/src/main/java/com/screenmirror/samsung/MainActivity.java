package com.screenmirror.samsung;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri; // For opening app settings
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog; // For user dialogs

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenmirror.samsung.service.ScreenCaptureService; // If used, ensure its manifest entry is correct
import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.TouchInputService;

import java.util.Objects; // For Objects.requireNonNull

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1; // Still good for direct request
    private static final int REQUEST_CODE_ACCESSIBILITY_SETTINGS = 2; // For launching settings

    public static final String ACTION_START_STREAMING = "com.screenmirror.samsung.START_STREAMING";
    public static final String ACTION_STOP_STREAMING = "com.screenmirror.samsung.STOP_STREAMING";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    private Button startMirroringButton;
    private Button stopMirroringButton;
    private TextView statusTextView;

    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

    private MediaProjection.Callback mediaProjectionCallback;

    // Handler for delayed tasks or UI updates from non-UI threads
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper()); // Initialize handler

        // Initialize UI components
        startMirroringButton = findViewById(R.id.start_mirroring_button);
        stopMirroringButton = findViewById(R.id.stop_mirroring_button);
        statusTextView = findViewById(R.id.status_text_view);

        // Safely get system service
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager == null) {
            Log.e(TAG, "MediaProjectionManager is null. Device may not support media projection.");
            Toast.makeText(this, "Device not supported for screen mirroring.", Toast.LENGTH_LONG).show();
            updateStatus("Status: Device Not Supported");
            // Disable buttons if core service is unavailable
            startMirroringButton.setEnabled(false);
            stopMirroringButton.setEnabled(false);
            return; // Critical error, stop further initialization
        }

        // Register ActivityResultLaunchers
        setupActivityResultLaunchers();

        // Set up button listeners
        startMirroringButton.setOnClickListener(v -> handleStartMirroringClick());
        stopMirroringButton.setOnClickListener(v -> handleStopMirroringClick());

        updateStatus("Status: Ready to Mirror");
    }

    private void setupActivityResultLaunchers() {
        mediaProjectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // MediaProjection.Callback should be set up *before* starting the service
                        // to ensure it can immediately react if MediaProjection stops.
                        // However, the callback needs `mediaProjection` instance, so it's a bit circular.
                        // We register it here as soon as mediaProjection is available.
                        mediaProjection = mediaProjectionManager.getMediaProjection(result.getResultCode(), result.getData());
                        if (mediaProjection != null) {
                            Log.d(TAG, "MediaProjection obtained successfully.");
                            setupMediaProjectionCallbacks(); // Setup callbacks immediately
                            startScreenMirroringService(); // Start the service
                        } else {
                            // This case indicates user granted permission but getMediaProjection returned null
                            Log.e(TAG, "Failed to get MediaProjection after user consent. Result OK, but data null or manager failed.");
                            Toast.makeText(this, "Failed to get screen capture permission. Please try again.", Toast.LENGTH_LONG).show();
                            updateStatus("Status: Permission Acquisition Failed");
                            resetUIState(); // Reset UI on failure
                        }
                    } else {
                        Log.w(TAG, "MediaProjection permission denied by user. Result Code: " + result.getResultCode());
                        Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show();
                        updateStatus("Status: Permission Denied");
                        resetUIState(); // Reset UI on denial
                    }
                });

        requestNotificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                        requestMediaProjectionPermission(); // Proceed to next step
                    } else {
                        Log.w(TAG, "POST_NOTIFICATIONS permission denied. Mirroring may lack foreground indicator.");
                        Toast.makeText(this, "Notification permission denied. Mirroring may not show ongoing notification.", Toast.LENGTH_LONG).show();
                        // Even if denied, we might proceed as screen capture itself is still possible,
                        // but the foreground service might not be as visible.
                        requestMediaProjectionPermission();
                    }
                });
    }

    private void handleStartMirroringClick() {
        // First, check if the Accessibility Service for touch input is enabled.
        // This is a critical prerequisite for full functionality.
        if (!isAccessibilityServiceEnabled(this, TouchInputService.class)) {
            showAccessibilityServiceDialog();
        } else {
            checkAndRequestNotificationPermission();
        }
    }

    private void handleStopMirroringClick() {
        stopScreenMirroringService();
    }


    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Should show explanation here if shouldShowRequestPermissionRationale returns true
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Notification Permission Needed")
                            .setMessage("This app needs notification permission to show an ongoing notification while mirroring, which is required for reliable background operation.")
                            .setPositiveButton("Grant", (dialog, which) -> requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                Toast.makeText(this, "Notification permission denied. Proceeding without full visibility.", Toast.LENGTH_LONG).show();
                                requestMediaProjectionPermission(); // Proceed anyway, but warn user
                            })
                            .show();
                } else {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            } else {
                requestMediaProjectionPermission();
            }
        } else {
            requestMediaProjectionPermission(); // No POST_NOTIFICATIONS permission needed on older APIs
        }
    }

    private void requestMediaProjectionPermission() {
        Log.d(TAG, "Requesting MediaProjection permission.");
        updateStatus("Status: Requesting Screen Capture...");
        try {
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
        } catch (SecurityException e) {
            // This might happen on some custom ROMs or highly restricted environments
            Log.e(TAG, "SecurityException when creating screen capture intent: " + e.getMessage());
            Toast.makeText(this, "Failed to create screen capture intent. Device security restriction?", Toast.LENGTH_LONG).show();
            updateStatus("Status: Error creating intent");
            resetUIState();
        }
    }

    private void setupMediaProjectionCallbacks() {
        // Unregister any existing callback first to prevent multiple registrations
        if (mediaProjectionCallback != null && mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
                Log.d(TAG, "Unregistered old MediaProjection callback.");
            } catch (IllegalStateException e) {
                Log.w(TAG, "Could not unregister old MediaProjection callback (already unregistered?): " + e.getMessage());
            }
        }

        mediaProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "MediaProjection onStop callback triggered. Screen capture stopped by system or user.");
                Toast.makeText(MainActivity.this, "Screen capture stopped by system or another app.", Toast.LENGTH_LONG).show();
                // Ensure UI and service are stopped correctly
                mainHandler.post(() -> stopScreenMirroringService());
            }

            // Android 14 (API 34) and above
            @Override
            public void onCapturedContentResize(int width, int height) {
                super.onCapturedContentResize(width, height);
                Log.d(TAG, "onCapturedContentResize: " + width + "x" + height);
                // In a robust app, you might want to inform the StreamingService of this change
                // so it can adjust its encoder or virtual display if necessary.
                // For simplicity here, we just log.
            }

            // Android 14 (API 34) and above
            @Override
            public void onCapturedContentVisibilityChanged(int visibility) {
                super.onCapturedContentVisibilityChanged(visibility);
                Log.d(TAG, "onCapturedContentVisibilityChanged: " + visibility);
                // For example, if visibility is View.INVISIBLE, the capture might be paused.
                // You might pause streaming or notify the user.
            }
        };

        // Register the callback on the main looper for UI-related events
        if (mediaProjection != null) {
            mediaProjection.registerCallback(mediaProjectionCallback, mainHandler);
            Log.d(TAG, "MediaProjection callbacks registered.");
        } else {
            Log.e(TAG, "Failed to register MediaProjection callbacks: mediaProjection is null.");
            // This is a critical error, likely means MediaProjection acquisition failed earlier.
            Toast.makeText(this, "Internal error: MediaProjection not available.", Toast.LENGTH_LONG).show();
            resetUIState();
        }
    }


    private void startScreenMirroringService() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null. Cannot start mirroring service.");
            Toast.makeText(this, "Screen capture permission was not successfully granted.", Toast.LENGTH_LONG).show();
            updateStatus("Status: Error - Permission Not Granted");
            resetUIState(); // Ensure UI is in a consistent state
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        // Use Objects.requireNonNull for robustness and clarity, assumes WindowManager is always available
        Objects.requireNonNull(windowManager).getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        Log.d(TAG, "Attempting to start StreamingService. Resolution: " + width + "x" + height + " Density: " + density);
        updateStatus("Status: Starting Streaming...");

        Intent streamingServiceIntent = new Intent(this, StreamingService.class);
        streamingServiceIntent.setAction(ACTION_START_STREAMING);

        // Passing MediaProjection: This remains the most sensitive part for robustness.
        // Direct Parcelable might have issues. A safer but more complex alternative is
        // to pass the RESULT_CODE and DATA Intent to the service, and let the service
        // call getMediaProjection() itself using its own MediaProjectionManager.
        // However, for simplicity and common practice, we'll continue with Parcelable here,
        // but it's important to be aware of potential process death issues.
        Bundle extras = new Bundle();
        extras.putParcelable("mediaProjection", mediaProjection);
        streamingServiceIntent.putExtras(extras);

        streamingServiceIntent.putExtra("width", width);
        streamingServiceIntent.putExtra("height", height);
        streamingServiceIntent.putExtra("density", density);

        try {
            ContextCompat.startForegroundService(this, streamingServiceIntent);
            startMirroringButton.setEnabled(false);
            stopMirroringButton.setEnabled(true);
            updateStatus("Status: Streaming Active");
            Log.d(TAG, "StreamingService started successfully.");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to start foreground service. Likely missing FOREGROUND_SERVICE permission or API limit: " + e.getMessage());
            Toast.makeText(this, "Failed to start streaming service. Check app permissions.", Toast.LENGTH_LONG).show();
            updateStatus("Status: Service Start Failed");
            resetUIState(); // Reset UI on failure
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting foreground service: " + e.getMessage());
            Toast.makeText(this, "Security error starting streaming service. Check app permissions.", Toast.LENGTH_LONG).show();
            updateStatus("Status: Service Start Failed (Security)");
            resetUIState();
        }
    }

    private void stopScreenMirroringService() {
        Log.d(TAG, "Attempting to stop screen mirroring.");
        updateStatus("Status: Stopping Streaming...");

        Intent streamingServiceIntent = new Intent(this, StreamingService.class);
        streamingServiceIntent.setAction(ACTION_STOP_STREAMING);

        try {
            stopService(streamingServiceIntent); // Will call onDestroy on the service
            Log.d(TAG, "Sent stop command to StreamingService.");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException stopping service: " + e.getMessage());
            Toast.makeText(this, "Failed to stop service due to security error.", Toast.LENGTH_LONG).show();
        } catch (Exception e) { // Catch any other unexpected exceptions
            Log.e(TAG, "Unexpected error stopping service: " + e.getMessage());
            Toast.makeText(this, "An error occurred while stopping streaming.", Toast.LENGTH_LONG).show();
        }

        // Clean up MediaProjection immediately in MainActivity as well
        releaseMediaProjection();

        resetUIState();
        updateStatus("Status: Mirroring Stopped");
        Toast.makeText(this, "Screen mirroring stopped.", Toast.LENGTH_SHORT).show();
    }

    private void releaseMediaProjection() {
        if (mediaProjection != null) {
            if (mediaProjectionCallback != null) {
                try {
                    mediaProjection.unregisterCallback(mediaProjectionCallback);
                } catch (IllegalStateException e) {
                    Log.w(TAG, "Error unregistering MediaProjection callback (already unregistered?): " + e.getMessage());
                }
            }
            mediaProjection.stop(); // This invalidates the MediaProjection token
            mediaProjection = null;
            mediaProjectionCallback = null; // Clear reference
            Log.d(TAG, "MediaProjection instance released in MainActivity.");
        }
    }

    private void resetUIState() {
        startMirroringButton.setEnabled(true);
        stopMirroringButton.setEnabled(false);
    }

    private void updateStatus(String status) {
        // Ensure UI updates are always on the main thread
        mainHandler.post(() -> statusTextView.setText(status));
    }

    /**
     * Checks if a specific Accessibility Service is enabled.
     * @param context Application context.
     * @param serviceClass The Class object of the AccessibilityService to check (e.g., TouchInputService.class).
     * @return true if the service is enabled, false otherwise.
     */
    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String serviceId = context.getPackageName() + "/" + serviceClass.getCanonicalName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error finding setting ACCESSIBILITY_ENABLED: " + e.getMessage());
        }

        if (accessibilityEnabled == 1) {
            String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices != null) {
                return enabledServices.contains(serviceId);
            }
        }
        return false;
    }

    /**
     * Shows a dialog guiding the user to enable the Accessibility Service.
     */
    private void showAccessibilityServiceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Accessibility Service Required")
                .setMessage("To enable touch input from your mirrored device, please enable the '" + getString(R.string.app_name) + "' Accessibility Service in your device settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    // Optionally, you can try to deep link to your specific service, but it's not always reliable
                    // intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY_SETTINGS); // Use startActivityForResult to know when they return
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Touch input will not be available without Accessibility Service.", Toast.LENGTH_LONG).show();
                    // User chose not to enable, but they can still start mirroring without touch
                    checkAndRequestNotificationPermission();
                })
                .setCancelable(false) // User must choose an option
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ACCESSIBILITY_SETTINGS) {
            // When the user returns from accessibility settings
            if (isAccessibilityServiceEnabled(this, TouchInputService.class)) {
                Toast.makeText(this, "Accessibility Service enabled! You can now start mirroring.", Toast.LENGTH_SHORT).show();
                checkAndRequestNotificationPermission(); // Proceed to next permission check
            } else {
                Toast.makeText(this, "Accessibility Service not enabled. Touch input will not work.", Toast.LENGTH_LONG).show();
                // If they didn't enable it, prompt them again or let them proceed without touch
                showAccessibilityServiceDialog(); // Or just let them try to start mirroring
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check Accessibility Service status when activity resumes (e.g., after user returns from settings)
        if (startMirroringButton.isEnabled() && !isAccessibilityServiceEnabled(this, TouchInputService.class)) {
            // Only show the dialog if mirroring is not active and service is not enabled.
            // This prevents re-showing the dialog if the user is already mirroring.
            // If the user starts mirroring without it, then opens settings and returns, this will prompt again.
            // You might want a more sophisticated way to only prompt once or based on a specific user action.
            // For now, this is a reasonable check on resume.
             // showAccessibilityServiceDialog(); // Consider if you want to be this aggressive on resume.
                                                // For robustness, maybe only prompt on button click.
                                                // The current flow calls it from `handleStartMirroringClick`.
        }
        // Update UI based on service running state (e.g., if service was stopped externally)
        updateButtonStatesBasedOnService();
    }

    /**
     * Helper to update UI based on whether StreamingService is currently running.
     * This relies on `getServiceInfo` which is not directly available to check if it's "running".
     * A more robust way would be to have the StreamingService broadcast its state,
     * or use `ActivityManager.getRunningServices` (which has limitations on modern Android).
     * For now, we'll assume `stopService` effectively stops it and `startForegroundService` starts it.
     * This method is a placeholder for a more sophisticated state management if needed.
     */
    private void updateButtonStatesBasedOnService() {
        // This is a simplification. A real app might have the service broadcast its status.
        // For example, if StreamingService posts a sticky broadcast on start/stop.
        // For now, we assume the UI state is controlled by our start/stop methods.
        // If the service can be killed by the system, these buttons might get out of sync.
        // You'd need a BroadcastReceiver in MainActivity to listen for service lifecycle events.
        boolean isServiceRunning = StreamingService.isRunning(); // Assuming StreamingService has a static method to check its running state
        startMirroringButton.setEnabled(!isServiceRunning);
        stopMirroringButton.setEnabled(isServiceRunning);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy. Releasing MediaProjection if still active.");
        releaseMediaProjection(); // Ensure MediaProjection is released when activity is destroyed
        // No need to stop service here, as stopScreenMirroringService() would have been called
        // if user explicitly stopped, or onStop callback if system stopped.
        // If the activity is destroyed while mirroring, the service should continue running as a foreground service.
    }
}
