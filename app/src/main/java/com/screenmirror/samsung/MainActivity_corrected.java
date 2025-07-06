package com.screenmirror.samsung;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenmirror.samsung.service.ScreenCaptureService;
import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.DiscoveryService;

/**
 * 🧪 MainActivity - ALCHEMICAL EDITION
 * 🔴 Samsung Galaxy S22 Ultra Screen Mirroring Controller
 * 🔵 Optimized for Android 14+ MediaProjection API
 * ⚗️ WebSocket communication with iPad Air 2
 */
public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "🔴CrystalCrucible";
    
    // 🧪 ALCHEMICAL CONSTANTS - Request Codes
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    private static final int REQUEST_OVERLAY_PERMISSION = 1003;
    
    // 🔴 CRIMSON VARIABLES - Core Components
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private Intent mediaProjectionIntent;
    
    // 🔵 AZURE VARIABLES - UI Components
    private Button startButton;
    private Button stopButton;
    private TextView statusText;
    private TextView connectionStatus;
    private TextView deviceInfo;
    
    // ⚗️ HERMETIC VARIABLES - Service State
    private boolean isCapturing = false;
    private boolean isStreaming = false;
    private boolean servicesStarted = false;
    
    // 🧪 PHILOSOPHER'S VARIABLES - Samsung Optimization
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WAKE_LOCK
    };
    
    // 🔴 Android 14+ specific permissions
    private static final String[] ANDROID_14_PERMISSIONS = {
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "🧪 MainActivity onCreate - Initializing alchemical interface");
        
        // 🔵 Initialize MediaProjection manager
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // ⚗️ Initialize UI components
        initializeAlchemicalInterface();
        
        // 🔴 Check and request permissions
        checkAndRequestPermissions();
        
        // 🧪 Display device information
        displayDeviceInformation();
        
        Log.d(TAG, "🔴 MainActivity initialization complete");
    }
    
    /**
     * 🧪 Initialize Alchemical User Interface
     */
    private void initializeAlchemicalInterface() {
        // 🔴 Find UI components
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        statusText = findViewById(R.id.statusText);
        connectionStatus = findViewById(R.id.connectionStatus);
        deviceInfo = findViewById(R.id.deviceInfo);
        
        // 🔵 Set initial UI state
        startButton.setText("🧪 Start Transmutation");
        stopButton.setText("⚗️ Stop Transmutation");
        statusText.setText("🔴 Ready for alchemical screen mirroring");
        connectionStatus.setText("🔵 Awaiting iPad connection...");
        
        // ⚗️ Configure button listeners
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAlchemicalTransmutation();
            }
        });
        
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAlchemicalTransmutation();
            }
        });
        
        // 🧪 Initial button states
        updateButtonStates();
        
        Log.d(TAG, "🔵 Alchemical interface initialized");
    }
    
    /**
     * 🔴 Display Samsung Galaxy S22 Ultra Device Information
     */
    private void displayDeviceInformation() {
        StringBuilder deviceInfoText = new StringBuilder();
        deviceInfoText.append("🧪 Device: ").append(Build.MODEL).append("\n");
        deviceInfoText.append("🔴 Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        deviceInfoText.append("🔵 Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        deviceInfoText.append("⚗️ Board: ").append(Build.BOARD).append("\n");
        
        // 🧪 Add Samsung-specific optimizations status
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            deviceInfoText.append("🔴 Samsung Optimizations: ENABLED\n");
            deviceInfoText.append("🔵 OneUI Compatibility: ACTIVE\n");
        }
        
        // ⚗️ MediaProjection API status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            deviceInfoText.append("🧪 Android 14+ API: SUPPORTED\n");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            deviceInfoText.append("🔴 MediaProjection API: SUPPORTED\n");
        } else {
            deviceInfoText.append("⚗️ MediaProjection API: NOT SUPPORTED\n");
        }
        
        deviceInfo.setText(deviceInfoText.toString());
        Log.d(TAG, "🧪 Device information displayed");
    }
    
    /**
     * 🔵 Check and Request All Required Permissions
     */
    private void checkAndRequestPermissions() {
        if (!hasAllPermissions()) {
            Log.d(TAG, "🔴 Requesting alchemical permissions...");
            requestAllPermissions();
        } else {
            Log.d(TAG, "🧪 All permissions granted - Ready for transmutation");
            checkOverlayPermission();
        }
    }
    
    /**
     * ⚗️ Check if all required permissions are granted
     */
    private boolean hasAllPermissions() {
        // 🔴 Check standard permissions
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "🔴 Missing permission: " + permission);
                return false;
            }
        }
        
        // 🔵 Check Android 14+ specific permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            for (String permission : ANDROID_14_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "🔵 Missing Android 14+ permission: " + permission);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 🧪 Request all required permissions
     */
    private void requestAllPermissions() {
        // 🔴 Combine all required permissions
        String[] allPermissions;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // ⚗️ Include Android 14+ permissions
            allPermissions = new String[REQUIRED_PERMISSIONS.length + ANDROID_14_PERMISSIONS.length];
            System.arraycopy(REQUIRED_PERMISSIONS, 0, allPermissions, 0, REQUIRED_PERMISSIONS.length);
            System.arraycopy(ANDROID_14_PERMISSIONS, 0, allPermissions, REQUIRED_PERMISSIONS.length, ANDROID_14_PERMISSIONS.length);
        } else {
            allPermissions = REQUIRED_PERMISSIONS;
        }
        
        ActivityCompat.requestPermissions(this, allPermissions, REQUEST_PERMISSIONS);
    }
    
    /**
     * 🔵 Check overlay permission for Android 6+
     */
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "🔴 Requesting overlay permission for alchemical interface");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            }
        }
    }
    
    /**
     * 🧪 Start Alchemical Screen Transmutation
     */
    private void startAlchemicalTransmutation() {
        Log.d(TAG, "🔴 Initiating alchemical transmutation sequence...");
        
        if (!hasAllPermissions()) {
            showAlchemicalToast("⚗️ Permissions required for transmutation");
            checkAndRequestPermissions();
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showAlchemicalToast("🔴 Overlay permission required for alchemical interface");
            checkOverlayPermission();
            return;
        }
        
        // 🔵 Request MediaProjection permission
        if (mediaProjectionIntent == null) {
            Log.d(TAG, "🧪 Requesting MediaProjection permission...");
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        } else {
            // ⚗️ Permission already granted, start services
            startAlchemicalServices();
        }
    }
    
    /**
     * ⚗️ Start all alchemical services
     */
    private void startAlchemicalServices() {
        try {
            Log.d(TAG, "🔴 Starting alchemical services...");
            
            // 🧪 Create MediaProjection from intent
            if (mediaProjectionIntent != null && mediaProjectionManager != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, mediaProjectionIntent);
                
                if (mediaProjection != null) {
                    // 🔵 Register MediaProjection callbacks for Android 14+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        registerMediaProjectionCallbacks();
                    }
                    
                    // ⚗️ Start discovery service first
                    startDiscoveryService();
                    
                    // 🔴 Start streaming service
                    startStreamingService();
                    
                    // 🧪 Start screen capture service
                    startScreenCaptureService();
                    
                    // 🔵 Update UI state
                    isCapturing = true;
                    isStreaming = true;
                    servicesStarted = true;
                    updateAlchemicalStatus("🧪 Transmutation active - Awaiting iPad connection");
                    updateButtonStates();
                    
                    showAlchemicalToast("🔴 Alchemical transmutation initiated successfully");
                    
                } else {
                    Log.e(TAG, "🔴 Failed to create MediaProjection");
                    showAlchemicalToast("⚗️ Failed to initialize screen capture");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "🔴 Error starting alchemical services: " + e.getMessage());
            showAlchemicalToast("⚗️ Transmutation failed: " + e.getMessage());
        }
    }
    
    /**
     * 🔵 Register MediaProjection callbacks for Android 14+
     */
    private void registerMediaProjectionCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mediaProjection != null) {
            Log.d(TAG, "🧪 Registering Android 14+ MediaProjection callbacks");
            
            MediaProjection.Callback callback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.d(TAG, "🔴 MediaProjection stopped by system");
                    runOnUiThread(() -> {
                        stopAlchemicalTransmutation();
                        showAlchemicalToast("⚗️ Screen capture stopped by system");
                    });
                }
                
                @Override
                public void onCapturedContentResize(int width, int height) {
                    Log.d(TAG, "🔵 Screen resolution changed: " + width + "x" + height);
                    // 🧪 Notify streaming service of resolution change
                    Intent resizeIntent = new Intent(MainActivity.this, StreamingService.class);
                    resizeIntent.setAction("RESIZE_CAPTURE");
                    resizeIntent.putExtra("width", width);
                    resizeIntent.putExtra("height", height);
                    startService(resizeIntent);
                }
                
                @Override
                public void onCapturedContentVisibilityChanged(boolean isVisible) {
                    Log.d(TAG, "⚗️ Content visibility changed: " + isVisible);
                    // 🔴 Notify streaming service of visibility change
                    Intent visibilityIntent = new Intent(MainActivity.this, StreamingService.class);
                    visibilityIntent.setAction("VISIBILITY_CHANGED");
                    visibilityIntent.putExtra("isVisible", isVisible);
                    startService(visibilityIntent);
                }
            };
            
            mediaProjection.registerCallback(callback, null);
        }
    }
    
    /**
     * 🧪 Start Discovery Service
     */
    private void startDiscoveryService() {
        Intent discoveryIntent = new Intent(this, DiscoveryService.class);
        startService(discoveryIntent);
        Log.d(TAG, "🔵 Discovery service started");
    }
    
    /**
     * 🔴 Start Streaming Service
     */
    private void startStreamingService() {
        Intent streamingIntent = new Intent(this, StreamingService.class);
        startService(streamingIntent);
        Log.d(TAG, "⚗️ Streaming service started");
    }
    
    /**
     * ⚗️ Start Screen Capture Service
     */
    private void startScreenCaptureService() {
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        captureIntent.putExtra("mediaProjectionIntent", mediaProjectionIntent);
        startService(captureIntent);
        Log.d(TAG, "🧪 Screen capture service started");
    }
    
    /**
     * 🔵 Stop Alchemical Transmutation
     */
    private void stopAlchemicalTransmutation() {
        Log.d(TAG, "🔴 Stopping alchemical transmutation...");
        
        try {
            // ⚗️ Stop all services
            stopService(new Intent(this, ScreenCaptureService.class));
            stopService(new Intent(this, StreamingService.class));
            stopService(new Intent(this, DiscoveryService.class));
            
            // 🧪 Stop MediaProjection
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            
            // 🔴 Reset state
            isCapturing = false;
            isStreaming = false;
            servicesStarted = false;
            mediaProjectionIntent = null;
            
            // 🔵 Update UI
            updateAlchemicalStatus("🔴 Transmutation stopped - Ready to restart");
            updateButtonStates();
            
            showAlchemicalToast("⚗️ Alchemical transmutation halted");
            
        } catch (Exception e) {
            Log.e(TAG, "🔴 Error stopping services: " + e.getMessage());
            showAlchemicalToast("🧪 Error during transmutation halt: " + e.getMessage());
        }
    }
    
    /**
     * 🧪 Update button states based on current status
     */
    private void updateButtonStates() {
        runOnUiThread(() -> {
            startButton.setEnabled(!isCapturing);
            stopButton.setEnabled(isCapturing);
            
            if (isCapturing) {
                startButton.setAlpha(0.5f);
                stopButton.setAlpha(1.0f);
            } else {
                startButton.setAlpha(1.0f);
                stopButton.setAlpha(0.5f);
            }
        });
    }
    
    /**
     * 🔴 Update alchemical status display
     */
    private void updateAlchemicalStatus(String status) {
        runOnUiThread(() -> {
            statusText.setText(status);
        });
    }
    
    /**
     * 🔵 Show alchemical toast message
     */
    private void showAlchemicalToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (requestCode) {
            case REQUEST_MEDIA_PROJECTION:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "🧪 MediaProjection permission granted");
                    mediaProjectionIntent = data;
                    startAlchemicalServices();
                } else {
                    Log.d(TAG, "🔴 MediaProjection permission denied");
                    showAlchemicalToast("⚗️ Screen capture permission required for transmutation");
                }
                break;
                
            case REQUEST_OVERLAY_PERMISSION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.d(TAG, "🔵 Overlay permission granted");
                        showAlchemicalToast("🧪 Overlay permission granted");
                    } else {
                        Log.d(TAG, "⚗️ Overlay permission denied");
                        showAlchemicalToast("🔴 Overlay permission required for alchemical interface");
                    }
                }
                break;
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "🔴 Permission denied: " + permissions[i]);
                    allGranted = false;
                }
            }
            
            if (allGranted) {
                Log.d(TAG, "🧪 All permissions granted");
                showAlchemicalToast("🔵 All permissions granted - Ready for transmutation");
                checkOverlayPermission();
            } else {
                Log.d(TAG, "⚗️ Some permissions denied");
                showAlchemicalToast("🔴 All permissions required for alchemical transmutation");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🔴 MainActivity onDestroy - Cleaning up alchemical resources");
        
        // 🧪 Ensure all services are stopped
        if (servicesStarted) {
            stopAlchemicalTransmutation();
        }
        
        // ⚗️ Clean up MediaProjection
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        Log.d(TAG, "🔵 Alchemical cleanup complete");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "⚗️ MainActivity paused");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🧪 MainActivity resumed");
        
        // 🔴 Update connection status when resuming
        updateConnectionStatus();
    }
    
    /**
     * 🔵 Update connection status display
     */
    private void updateConnectionStatus() {
        // 🧪 This would typically check actual connection status
        // For now, show generic status based on service state
        if (isStreaming) {
            connectionStatus.setText("🔴 Streaming active - Awaiting iPad connection");
        } else {
            connectionStatus.setText("🔵 Ready for iPad connection");
        }
    }
}

